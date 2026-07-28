package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JobManager {

    public static final int MAX_LEVEL = 30;
    public static final int MAX_SKILL_LEVEL = 3;

    public static class JobData {
        public JobType job;
        public int level = 1;
        public long exp = 0;
        public int skillPoints = 0;
        public final Map<SkillType, Integer> skillLevels = new EnumMap<>(SkillType.class);

        public JobData() {
            for (SkillType t : SkillType.values()) skillLevels.put(t, 0);
        }

        public int skillLevel(SkillType type) { return skillLevels.getOrDefault(type, 0); }
    }

    private final JeminSMPPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    private final Map<UUID, JobData> data = new HashMap<>();
    private final Map<UUID, Map<SkillType, Long>> cooldowns = new HashMap<>();

    public JobManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "jobs.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            cfg = new YamlConfiguration();
            return;
        }
        cfg = YamlConfiguration.loadConfiguration(file);
        var sec = cfg.getConfigurationSection("players");
        if (sec == null) return;
        for (String uuidStr : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                JobData d = new JobData();
                String jobStr = cfg.getString("players." + uuidStr + ".job");
                d.job = jobStr == null ? null : JobType.fromString(jobStr);
                d.level = cfg.getInt("players." + uuidStr + ".level", 1);
                d.exp = cfg.getLong("players." + uuidStr + ".exp", 0);
                d.skillPoints = cfg.getInt("players." + uuidStr + ".skillPoints", 0);
                for (SkillType t : SkillType.values()) {
                    d.skillLevels.put(t, cfg.getInt("players." + uuidStr + ".skills." + t.name(), 0));
                }
                data.put(uuid, d);
            } catch (Exception ignored) {}
        }
    }

    public void save() {
        cfg.set("players", null);
        for (var entry : data.entrySet()) {
            String path = "players." + entry.getKey();
            JobData d = entry.getValue();
            cfg.set(path + ".job", d.job == null ? null : d.job.name());
            cfg.set(path + ".level", d.level);
            cfg.set(path + ".exp", d.exp);
            cfg.set(path + ".skillPoints", d.skillPoints);
            for (SkillType t : SkillType.values()) {
                cfg.set(path + ".skills." + t.name(), d.skillLevel(t));
            }
        }
        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().warning("jobs.yml 저장 실패: " + e.getMessage()); }
    }

    public JobData getData(UUID uuid) {
        return data.computeIfAbsent(uuid, u -> new JobData());
    }

    public JobType getJob(UUID uuid) {
        JobData d = data.get(uuid);
        return d == null ? null : d.job;
    }

    // ── 직업 선택 (전직 시 초기화) ──
    public void selectJob(UUID uuid, JobType job) {
        JobData d = getData(uuid);
        d.job = job;
        d.level = 1;
        d.exp = 0;
        d.skillPoints = 0;
        for (SkillType t : SkillType.values()) d.skillLevels.put(t, 0);
        save();
    }

    public static long expForLevel(int level) {
        return level * 100L;
    }

    // ── 경험치 지급 & 레벨업 처리 ──
    public void addExp(Player player, JobType job, long amount) {
        JobData d = getData(player.getUniqueId());
        if (d.job != job) return;
        if (d.level >= MAX_LEVEL) return;

        d.exp += amount;
        boolean leveledUp = false;
        while (d.level < MAX_LEVEL) {
            long need = expForLevel(d.level);
            if (d.exp < need) break;
            d.exp -= need;
            d.level++;
            d.skillPoints++;
            leveledUp = true;
        }
        if (leveledUp) {
            if (d.level >= MAX_LEVEL) d.exp = 0;
            player.sendMessage("§6§l▲ 퀘스트 달성! §e" + job.icon() + " " + job.display() + " Lv." + d.level
                    + " §7(스킬포인트 +1, 보유 " + d.skillPoints + ")");
        }
        save();
    }

    // ── 스킬 투자 ──
    public String invest(UUID uuid, SkillType type) {
        JobData d = getData(uuid);
        if (d.job == null) return "직업을 먼저 선택하세요. (/job select <직업>)";
        int cur = d.skillLevel(type);
        if (cur >= MAX_SKILL_LEVEL) return "이미 " + type.display() + " 스킬은 최대 레벨입니다.";
        int cost = cur + 1;
        if (d.skillPoints < cost) return "스킬포인트가 부족합니다. (필요 " + cost + ", 보유 " + d.skillPoints + ")";
        d.skillPoints -= cost;
        d.skillLevels.put(type, cur + 1);
        save();
        return null;
    }

    // ── 쿨다운 ──
    public long getCooldownRemainingSeconds(UUID uuid, SkillType type) {
        Long until = cooldowns.getOrDefault(uuid, Map.of()).get(type);
        if (until == null) return 0;
        long remaining = (until - System.currentTimeMillis()) / 1000L;
        return Math.max(0, remaining);
    }

    public void setCooldown(UUID uuid, SkillType type, int seconds) {
        cooldowns.computeIfAbsent(uuid, u -> new EnumMap<>(SkillType.class))
                .put(type, System.currentTimeMillis() + seconds * 1000L);
    }

    public static int cooldownSeconds(int skillLevel) {
        return switch (skillLevel) {
            case 1 -> 60;
            case 2 -> 45;
            case 3 -> 30;
            default -> 60;
        };
    }
}
