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
    public static final int MAX_SKILL_LEVEL = 5;

    // 딜 -> 힐 -> 포스 순서로 돌아가며 레벨 1개씩 자동 해금/성장. 3개 다 5레벨 찍으면(직업레벨 15) 이동기가 이어서 해금/성장.
    private static final SkillType[] UNLOCK_ORDER = { SkillType.DEAL, SkillType.HEAL, SkillType.FORCE };
    private static final int CORE_MAX_JOB_LEVEL = UNLOCK_ORDER.length * MAX_SKILL_LEVEL; // 15

    public static class JobData {
        public JobType job;
        public int level = 1;
        public long exp = 0;
        // 현재 선택 안 된 다른 직업들의 저장된 진행도 (전직해도 안 사라지게)
        final Map<JobType, Integer> savedLevel = new EnumMap<>(JobType.class);
        final Map<JobType, Long> savedExp = new EnumMap<>(JobType.class);

        public int skillLevel(SkillType type) {
            if (type == SkillType.MOVE) {
                if (level <= CORE_MAX_JOB_LEVEL) return 0;
                return Math.min(level - CORE_MAX_JOB_LEVEL, MAX_SKILL_LEVEL);
            }
            int idx = indexOf(type);
            int count = 0;
            for (int lvl = 1; lvl <= Math.min(level, CORE_MAX_JOB_LEVEL); lvl++) {
                if (UNLOCK_ORDER[(lvl - 1) % UNLOCK_ORDER.length] == type) count++;
            }
            return Math.min(count, MAX_SKILL_LEVEL);
        }

        private static int indexOf(SkillType type) {
            for (int i = 0; i < UNLOCK_ORDER.length; i++) if (UNLOCK_ORDER[i] == type) return i;
            return -1;
        }
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
                var savedSec = cfg.getConfigurationSection("players." + uuidStr + ".saved");
                if (savedSec != null) {
                    for (String jobKey : savedSec.getKeys(false)) {
                        JobType jt = JobType.fromString(jobKey);
                        if (jt == null) continue;
                        d.savedLevel.put(jt, cfg.getInt("players." + uuidStr + ".saved." + jobKey + ".level", 1));
                        d.savedExp.put(jt, cfg.getLong("players." + uuidStr + ".saved." + jobKey + ".exp", 0));
                    }
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
            cfg.set(path + ".saved", null);
            for (var se : d.savedLevel.entrySet()) {
                cfg.set(path + ".saved." + se.getKey().name() + ".level", se.getValue());
                cfg.set(path + ".saved." + se.getKey().name() + ".exp", d.savedExp.getOrDefault(se.getKey(), 0L));
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

    // ── 직업 선택 (직업별 레벨/경험치는 따로 저장되어 전직해도 유지됨) ──
    public void selectJob(UUID uuid, JobType job) {
        JobData d = getData(uuid);
        if (d.job != null) {
            d.savedLevel.put(d.job, d.level);
            d.savedExp.put(d.job, d.exp);
        }
        d.job = job;
        d.level = d.savedLevel.getOrDefault(job, 1);
        d.exp = d.savedExp.getOrDefault(job, 0L);
        save();
    }

    // 미션(개수) 기반 — 광물/몹 종류 상관없이 자격 있는 행동 1회 = 1. 레벨이 오를수록 필요 개수가 점점 늘어남
    public static long expForLevel(int level) {
        return 5L + level * 5L;
    }

    // ── 경험치 지급 & 레벨업 처리 ──
    public void addExp(Player player, JobType job, long amount) {
        JobData d = getData(player.getUniqueId());
        if (d.job != job) return;
        if (d.level >= MAX_LEVEL) return;

        d.exp += amount;
        int levelsGained = 0;
        while (d.level < MAX_LEVEL) {
            long need = expForLevel(d.level);
            if (d.exp < need) break;
            d.exp -= need;
            d.level++;
            levelsGained++;
        }
        if (levelsGained > 0) {
            if (d.level >= MAX_LEVEL) d.exp = 0;
            player.sendMessage("§6§l▲ 퀘스트 달성! §e" + job.display() + " Lv." + d.level
                    + " §7— " + describeUnlock(d.level));
            if (plugin.getJobSkillItemListener() != null) {
                plugin.getJobSkillItemListener().regrantIfMissing(player);
            }
        }
        save();
    }

    // ── 관리자: 레벨(미션) 즉시 설정 ──
    public void setLevel(Player player, int level) {
        JobData d = getData(player.getUniqueId());
        if (d.job == null) return;
        d.level = Math.max(1, Math.min(MAX_LEVEL, level));
        d.exp = 0;
        save();
        player.sendMessage("§6§l▲ 관리자에 의해 " + d.job.display() + " Lv." + d.level + "(으)로 설정되었습니다.");
        if (plugin.getJobSkillItemListener() != null) {
            plugin.getJobSkillItemListener().regrantIfMissing(player);
        }
    }

    private String describeUnlock(int newLevel) {
        if (newLevel <= CORE_MAX_JOB_LEVEL) {
            SkillType t = UNLOCK_ORDER[(newLevel - 1) % UNLOCK_ORDER.length];
            int skillLevel = ((newLevel - 1) / UNLOCK_ORDER.length) + 1;
            return t.display() + " Lv" + skillLevel + (skillLevel == 1 ? " 해금!" : "로 성장!");
        }
        int moveLevel = newLevel - CORE_MAX_JOB_LEVEL;
        if (moveLevel > MAX_SKILL_LEVEL) return "전부 최대 강화 완료";
        return "이동기 Lv" + moveLevel + (moveLevel == 1 ? " 해금!" : "로 성장!");
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
            case 2 -> 50;
            case 3 -> 40;
            case 4 -> 30;
            case 5 -> 20;
            default -> 60;
        };
    }
}
