package jeminsmp.title;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TitleManager {

    private final JeminSMPPlugin plugin;
    private final Map<String, TitleInfo> titleMap = new LinkedHashMap<>();
    private final Map<UUID, PlayerTitleData> playerData = new HashMap<>();

    private File titlesFile, playersFile;
    private FileConfiguration titlesConfig, playersConfig;

    public TitleManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        loadTitles();
        loadPlayers();
    }

    private void loadTitles() {
        titlesFile = new File(plugin.getDataFolder(), "titles.yml");
        if (!titlesFile.exists()) {
            try { titlesFile.createNewFile(); } catch (IOException ignored) {}
            titlesConfig = YamlConfiguration.loadConfiguration(titlesFile);
            titlesConfig.set("vip.price", 1000);
            titlesConfig.set("vip.display", "&6[VIP]");
            titlesConfig.set("mvp.price", 5000);
            titlesConfig.set("mvp.display", "&d[MVP]");
            titlesConfig.set("legend.price", 20000);
            titlesConfig.set("legend.display", "&4&l[LEGEND]");
            saveTitles();
        } else {
            titlesConfig = YamlConfiguration.loadConfiguration(titlesFile);
        }
        titleMap.clear();
        for (String id : titlesConfig.getKeys(false)) {
            long price = titlesConfig.getLong(id + ".price", 0);
            String display = titlesConfig.getString(id + ".display", "&f[" + id + "]");
            titleMap.put(id, new TitleInfo(id, price, display));
        }
    }

    private void saveTitles() {
        try { titlesConfig.save(titlesFile); } catch (IOException e) {
            plugin.getLogger().severe("titles.yml 저장 실패: " + e.getMessage());
        }
    }

    private void loadPlayers() {
        playersFile = new File(plugin.getDataFolder(), "players.yml");
        if (!playersFile.exists()) {
            try { playersFile.createNewFile(); } catch (IOException ignored) {}
        }
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);
        playerData.clear();
        for (String key : playersConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                List<String> owned = playersConfig.getStringList(key + ".owned");
                String equipped = playersConfig.getString(key + ".equipped", null);
                playerData.put(uuid, new PlayerTitleData(new HashSet<>(owned), equipped));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void savePlayers() {
        try { playersConfig.save(playersFile); } catch (IOException e) {
            plugin.getLogger().severe("title_players.yml 저장 실패: " + e.getMessage());
        }
    }

    public boolean createTitle(String id, long price, String display) {
        if (titleMap.containsKey(id)) return false;
        titleMap.put(id, new TitleInfo(id, price, display));
        titlesConfig.set(id + ".price", price);
        titlesConfig.set(id + ".display", display);
        saveTitles();
        return true;
    }

    /** 칭호 수정: field = "price" 또는 "display" */
    public boolean editTitle(String id, String field, String value) {
        TitleInfo old = titleMap.get(id);
        if (old == null) return false;
        TitleInfo updated = switch (field.toLowerCase()) {
            case "price" -> {
                try { yield new TitleInfo(id, Long.parseLong(value), old.display()); }
                catch (NumberFormatException e) { yield null; }
            }
            case "display" -> new TitleInfo(id, old.price(), value);
            default -> null;
        };
        if (updated == null) return false;
        titleMap.put(id, updated);
        titlesConfig.set(id + ".price", updated.price());
        titlesConfig.set(id + ".display", updated.display());
        saveTitles();
        return true;
    }

    public boolean deleteTitle(String id) {
        if (!titleMap.containsKey(id)) return false;
        titleMap.remove(id);
        titlesConfig.set(id, null);
        saveTitles();
        playerData.values().stream()
                .filter(d -> id.equals(d.equipped()))
                .forEach(d -> d.setEquipped(null));
        savePlayers();
        return true;
    }

    public Collection<TitleInfo> getAllTitles() { return titleMap.values(); }
    public TitleInfo getTitle(String id) { return titleMap.get(id); }

    private PlayerTitleData getData(UUID uuid) {
        return playerData.computeIfAbsent(uuid, k -> new PlayerTitleData(new HashSet<>(), null));
    }

    public boolean hasTitle(UUID uuid, String id) { return getData(uuid).owned().contains(id); }

    public boolean giveTitle(UUID uuid, String id) {
        PlayerTitleData data = getData(uuid);
        if (data.owned().contains(id)) return false;
        data.owned().add(id);
        playersConfig.set(uuid + ".owned", new ArrayList<>(data.owned()));
        savePlayers();
        return true;
    }

    public boolean equipTitle(UUID uuid, String id) {
        if (!hasTitle(uuid, id)) return false;
        getData(uuid).setEquipped(id);
        playersConfig.set(uuid + ".equipped", id);
        savePlayers();
        return true;
    }

    public void removeEquipped(UUID uuid) {
        getData(uuid).setEquipped(null);
        playersConfig.set(uuid + ".equipped", null);
        savePlayers();
    }

    public Set<String> getOwnedTitles(UUID uuid) { return getData(uuid).owned(); }

    public String getEquippedDisplay(UUID uuid) {
        String equipped = getData(uuid).equipped();
        if (equipped == null) return null;
        TitleInfo info = titleMap.get(equipped);
        return info != null ? info.display() : null;
    }

    /** UUID 마이그레이션: 오프라인 UUID → 온라인 UUID로 인메모리 칭호 데이터 이전 */
    public void migrateUuid(UUID from, UUID to) {
        PlayerTitleData data = playerData.remove(from);
        if (data != null) {
            playerData.put(to, data);
        }
    }

    public record TitleInfo(String id, long price, String display) {}

    public static class PlayerTitleData {
        private final Set<String> owned;
        private String equipped;
        public PlayerTitleData(Set<String> owned, String equipped) { this.owned = owned; this.equipped = equipped; }
        public Set<String> owned() { return owned; }
        public String equipped() { return equipped; }
        public void setEquipped(String v) { this.equipped = v; }
    }
}
