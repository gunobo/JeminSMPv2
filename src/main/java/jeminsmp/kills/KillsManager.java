package jeminsmp.kills;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class KillsManager {

    private final JeminSMPPlugin plugin;
    private final File file;
    private FileConfiguration config;

    private final Map<UUID, PlayerStats> stats = new HashMap<>();
    private final Map<UUID, Integer> currentStreak = new HashMap<>();

    public KillsManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "kills.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        load();
    }

    private void load() {
        config = YamlConfiguration.loadConfiguration(file);
        stats.clear();
        var section = config.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "players." + key;
                String name = config.getString(path + ".name", "Unknown");
                int kills = config.getInt(path + ".kills", 0);
                int deaths = config.getInt(path + ".deaths", 0);
                int maxStreak = config.getInt(path + ".maxStreak", 0);
                stats.put(uuid, new PlayerStats(uuid, name, kills, deaths, maxStreak));
            } catch (Exception ignored) {}
        }
    }

    public void save() {
        for (PlayerStats s : stats.values()) {
            String path = "players." + s.uuid;
            config.set(path + ".name", s.name);
            config.set(path + ".kills", s.kills);
            config.set(path + ".deaths", s.deaths);
            config.set(path + ".maxStreak", s.maxStreak);
        }
        try { config.save(file); } catch (IOException e) {
            plugin.getLogger().severe("kills.yml 저장 실패: " + e.getMessage());
        }
    }

    public int registerKill(UUID killerUUID, String killerName) {
        PlayerStats s = getOrCreate(killerUUID, killerName);
        s.name = killerName;
        s.kills++;
        int streak = currentStreak.merge(killerUUID, 1, Integer::sum);
        if (streak > s.maxStreak) s.maxStreak = streak;
        return streak;
    }

    public int registerDeath(UUID deadUUID, String deadName) {
        PlayerStats s = getOrCreate(deadUUID, deadName);
        s.name = deadName;
        s.deaths++;
        int lost = currentStreak.getOrDefault(deadUUID, 0);
        currentStreak.remove(deadUUID);
        return lost;
    }

    public void resetStats(UUID uuid) {
        stats.remove(uuid);
        currentStreak.remove(uuid);
        config.set("players." + uuid, null);
        save();
    }

    public void resetAll() {
        stats.clear();
        currentStreak.clear();
        config.set("players", null);
        save();
    }

    public PlayerStats getStats(UUID uuid) { return stats.get(uuid); }
    public int getCurrentStreak(UUID uuid) { return currentStreak.getOrDefault(uuid, 0); }

    public List<PlayerStats> getTopByKills(int n) {
        return stats.values().stream()
                .sorted(Comparator.comparingInt(PlayerStats::kills).reversed())
                .limit(n).toList();
    }

    public List<PlayerStats> getTopByDeaths(int n) {
        return stats.values().stream()
                .sorted(Comparator.comparingInt((PlayerStats s) -> s.deaths).reversed())
                .limit(n).toList();
    }

    public List<PlayerStats> getTopByKda(int n) {
        return stats.values().stream()
                .filter(s -> s.kills > 0)
                .sorted(Comparator.comparingDouble(PlayerStats::kda).reversed())
                .limit(n).toList();
    }

    public List<PlayerStats> getTopByStreak(int n) {
        return stats.values().stream()
                .filter(s -> s.maxStreak > 0)
                .sorted(Comparator.comparingInt(PlayerStats::maxStreak).reversed())
                .limit(n).toList();
    }

    /** UUID 마이그레이션: 오프라인 UUID → 온라인 UUID로 인메모리 데이터 이전 */
    public void migrateUuid(UUID from, UUID to) {
        PlayerStats s = stats.remove(from);
        if (s != null) {
            stats.put(to, new PlayerStats(to, s.name, s.kills, s.deaths, s.maxStreak));
        }
        Integer streak = currentStreak.remove(from);
        if (streak != null) {
            currentStreak.put(to, streak);
        }
    }

    private PlayerStats getOrCreate(UUID uuid, String name) {
        return stats.computeIfAbsent(uuid, u -> new PlayerStats(u, name, 0, 0, 0));
    }

    public static class PlayerStats {
        public final UUID uuid;
        public String name;
        public int kills;
        public int deaths;
        public int maxStreak;

        PlayerStats(UUID uuid, String name, int kills, int deaths, int maxStreak) {
            this.uuid = uuid;
            this.name = name;
            this.kills = kills;
            this.deaths = deaths;
            this.maxStreak = maxStreak;
        }

        public double kda() { return deaths == 0 ? kills : (double) kills / deaths; }
        public int kills() { return kills; }
        public int maxStreak() { return maxStreak; }
    }
}
