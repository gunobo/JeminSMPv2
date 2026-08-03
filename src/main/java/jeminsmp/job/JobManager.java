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
    public static final int TIER1_MAX_LEVEL = 60;  // 1차 전직 후 레벨 상한
    public static final int TIER2_MAX_LEVEL = 100; // 2차 전직 후 레벨 상한
    public static final int MAX_SKILL_LEVEL = 5;

    // 전직 조건: 누적 활동(직업 공통 행동) 하나만 봄. 단계 오를수록 훨씬 크게 요구.
    private static final Map<JobType, Long> TIER1_MAIN_REQ = Map.of(
            JobType.MINER, 10_000L, JobType.FARMER, 10_000L, JobType.WARRIOR, 10_000L, JobType.FISHER, 10_000L
    );
    private static final Map<JobType, Long> TIER2_MAIN_REQ = Map.of(
            JobType.MINER, 50_000L, JobType.FARMER, 50_000L, JobType.WARRIOR, 50_000L, JobType.FISHER, 50_000L
    );

    private static Map<JobType, Long> mainReq(int tier) { return tier <= 1 ? TIER1_MAIN_REQ : TIER2_MAIN_REQ; }

    public static int levelCapForTier(int tier) {
        return switch (tier) {
            case 0 -> MAX_LEVEL;
            case 1 -> TIER1_MAX_LEVEL;
            default -> TIER2_MAX_LEVEL;
        };
    }

    // 딜 -> 힐 -> 포스 순서로 돌아가며 레벨 1개씩 자동 해금/성장. 3개 다 5레벨 찍으면(직업레벨 15) 이동기가 이어서 해금/성장.
    private static final SkillType[] UNLOCK_ORDER = { SkillType.DEAL, SkillType.HEAL, SkillType.FORCE };
    private static final int CORE_MAX_JOB_LEVEL = UNLOCK_ORDER.length * MAX_SKILL_LEVEL; // 15

    // 2차 전직 후(레벨 61~100)에는 딜->힐->포스->이동기 순으로 계속 돌며 한 단계씩 더 성장
    private static final SkillType[] FULL_CYCLE = { SkillType.DEAL, SkillType.HEAL, SkillType.FORCE, SkillType.MOVE };
    public static final int MAX_SKILL_LEVEL_TIER2 = MAX_SKILL_LEVEL + (TIER2_MAX_LEVEL - TIER1_MAX_LEVEL) / FULL_CYCLE.length; // 15

    public static class JobData {
        public JobType job;
        public int level = 1;
        public long exp = 0;
        // 현재 선택 안 된 다른 직업들의 저장된 진행도 (전직해도 안 사라지게)
        final Map<JobType, Integer> savedLevel = new EnumMap<>(JobType.class);
        final Map<JobType, Long> savedExp = new EnumMap<>(JobType.class);
        // 전직용 누적치(레벨업으로 소비되는 exp와 달리 절대 안 줄어듦) + 직업별 전직 단계(0=없음, 1=1차, 2=2차...)
        final Map<JobType, Long> lifetimeMain = new EnumMap<>(JobType.class);
        final Map<JobType, Integer> advanceTier = new EnumMap<>(JobType.class);
        boolean doubleYield = false; // 2차 전직 전용 토글 — 직업별 핵심 드랍 2배(광물/작물/몹처치/낚시)

        int tier(JobType job) { return advanceTier.getOrDefault(job, 0); }

        public int skillLevel(SkillType type) {
            int base;
            if (type == SkillType.MOVE) {
                base = level <= CORE_MAX_JOB_LEVEL ? 0 : Math.min(level - CORE_MAX_JOB_LEVEL, MAX_SKILL_LEVEL);
            } else {
                int count = 0;
                for (int lvl = 1; lvl <= Math.min(level, CORE_MAX_JOB_LEVEL); lvl++) {
                    if (UNLOCK_ORDER[(lvl - 1) % UNLOCK_ORDER.length] == type) count++;
                }
                base = Math.min(count, MAX_SKILL_LEVEL);
            }
            return base + bonusSkillLevel(type);
        }

        // 2차 전직 후(레벨 61~100) 딜->힐->포스->이동기 순으로 도는 추가 성장분
        private int bonusSkillLevel(SkillType type) {
            if (level <= TIER1_MAX_LEVEL) return 0;
            int bonus = 0;
            for (int lvl = TIER1_MAX_LEVEL + 1; lvl <= level; lvl++) {
                if (FULL_CYCLE[(lvl - TIER1_MAX_LEVEL - 1) % FULL_CYCLE.length] == type) bonus++;
            }
            return bonus;
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
                var lifetimeSec = cfg.getConfigurationSection("players." + uuidStr + ".lifetime");
                if (lifetimeSec != null) {
                    for (String jobKey : lifetimeSec.getKeys(false)) {
                        JobType jt = JobType.fromString(jobKey);
                        if (jt == null) continue;
                        d.lifetimeMain.put(jt, cfg.getLong("players." + uuidStr + ".lifetime." + jobKey + ".main", 0));
                    }
                }
                var tierSec = cfg.getConfigurationSection("players." + uuidStr + ".tier");
                if (tierSec != null) {
                    for (String jobKey : tierSec.getKeys(false)) {
                        JobType jt = JobType.fromString(jobKey);
                        if (jt != null) d.advanceTier.put(jt, cfg.getInt("players." + uuidStr + ".tier." + jobKey, 0));
                    }
                }
                // 예전(단순 리스트) 형식 호환 — 있으면 1차로 취급
                for (String jobKey : cfg.getStringList("players." + uuidStr + ".advanced")) {
                    JobType jt = JobType.fromString(jobKey);
                    if (jt != null) d.advanceTier.merge(jt, 1, Math::max);
                }
                boolean legacyToggle = cfg.getBoolean("players." + uuidStr + ".warriorDropBoost", false);
                d.doubleYield = cfg.getBoolean("players." + uuidStr + ".doubleYield", legacyToggle);
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
            cfg.set(path + ".lifetime", null);
            for (var le : d.lifetimeMain.entrySet()) {
                cfg.set(path + ".lifetime." + le.getKey().name() + ".main", le.getValue());
            }
            cfg.set(path + ".advanced", null);
            cfg.set(path + ".tier", null);
            for (var te : d.advanceTier.entrySet()) {
                cfg.set(path + ".tier." + te.getKey().name(), te.getValue());
            }
            cfg.set(path + ".warriorDropBoost", null);
            cfg.set(path + ".doubleYield", d.doubleYield);
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

        // 1차 전직용 누적치는 레벨 상한과 무관하게 계속 쌓임
        d.lifetimeMain.merge(job, amount, Long::sum);

        int cap = levelCapForTier(d.tier(job));
        if (d.level >= cap) { save(); return; }

        d.exp += amount;
        int levelsGained = 0;
        while (d.level < cap) {
            long need = expForLevel(d.level);
            if (d.exp < need) break;
            d.exp -= need;
            d.level++;
            levelsGained++;
        }
        if (levelsGained > 0) {
            if (d.level >= cap) d.exp = 0;
            player.sendMessage("§6§l▲ 퀘스트 달성! §e" + job.display() + " Lv." + d.level
                    + " §7— " + describeUnlock(d.level));
            player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            if (plugin.getJobSkillItemListener() != null) {
                plugin.getJobSkillItemListener().regrantIfMissing(player);
            }
        }
        save();
    }

    // ── 전직 (단계 무관 공통 로직) ──
    public int getTier(UUID uuid, JobType job) {
        return getData(uuid).tier(job);
    }

    /** 현재 직업에 아직 도전 안 한 다음 전직 단계가 존재하는지 (그 직업에 다음 단계 자체가 없으면 거짓) */
    public boolean hasNextTier(UUID uuid) {
        JobData d = getData(uuid);
        if (d.job == null) return false;
        return mainReq(d.tier(d.job) + 1).containsKey(d.job);
    }

    /** 다음 단계로 전직 가능한지. 그 직업에 다음 단계가 아예 없으면 항상 거짓 */
    public boolean canAdvance(UUID uuid) {
        JobData d = getData(uuid);
        if (d.job == null) return false;
        int currentTier = d.tier(d.job);
        int nextTier = currentTier + 1;
        Long mainNeed = mainReq(nextTier).get(d.job);
        if (mainNeed == null) return false; // 그 직업엔 다음 단계가 없음
        if (d.level < levelCapForTier(currentTier)) return false;
        long main = d.lifetimeMain.getOrDefault(d.job, 0L);
        return main >= mainNeed;
    }

    public String advanceProgressText(UUID uuid) {
        JobData d = getData(uuid);
        if (d.job == null) return "직업이 없습니다.";
        int currentTier = d.tier(d.job);
        int nextTier = currentTier + 1;
        Long mainNeed = mainReq(nextTier).get(d.job);
        if (mainNeed == null) return (currentTier == 0 ? "전직 없음" : currentTier + "차 전직 완료") + " §7(다음 단계 없음)";
        long main = d.lifetimeMain.getOrDefault(d.job, 0L);
        return "레벨 §f" + d.level + "§7/§f" + levelCapForTier(currentTier)
                + " §7| 누적 활동 §f" + main + "§7/§f" + mainNeed;
    }

    /** 성공하면 새로 달성한 단계(1, 2...)를 반환, 실패하면 0 */
    public int advance(Player player) {
        UUID uuid = player.getUniqueId();
        if (!canAdvance(uuid)) return 0;
        JobData d = getData(uuid);
        int newTier = d.tier(d.job) + 1;
        d.advanceTier.put(d.job, newTier);
        save();
        return newTier;
    }

    /** 2차 전직 전용: 직업별 핵심 드랍 2배 토글(광물/작물/몹처치/낚시). 2차 전직 안 했으면 null */
    public Boolean toggleDoubleYield(Player player) {
        JobData d = getData(player.getUniqueId());
        if (d.job == null || d.tier(d.job) < 2) return null;
        d.doubleYield = !d.doubleYield;
        save();
        return d.doubleYield;
    }

    public boolean isDoubleYieldActive(UUID uuid, JobType job) {
        JobData d = getData(uuid);
        return d.job == job && d.tier(job) >= 2 && d.doubleYield;
    }

    // ── 관리자: 레벨(미션) 즉시 설정 ──
    public void setLevel(Player player, int level) {
        JobData d = getData(player.getUniqueId());
        if (d.job == null) return;
        int cap = levelCapForTier(d.tier(d.job));
        d.level = Math.max(1, Math.min(cap, level));
        d.exp = 0;
        save();
        player.sendMessage("§6§l▲ 관리자에 의해 " + d.job.display() + " Lv." + d.level + "(으)로 설정되었습니다.");
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
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
        if (newLevel <= TIER1_MAX_LEVEL) {
            int moveLevel = newLevel - CORE_MAX_JOB_LEVEL;
            if (moveLevel > MAX_SKILL_LEVEL) return "전부 최대 강화 완료";
            return "이동기 Lv" + moveLevel + (moveLevel == 1 ? " 해금!" : "로 성장!");
        }
        // 2차 전직 후: 딜 -> 힐 -> 포스 -> 이동기 순으로 계속 돌며 한 단계씩 더 성장
        SkillType t = FULL_CYCLE[(newLevel - TIER1_MAX_LEVEL - 1) % FULL_CYCLE.length];
        int bonusLevel = MAX_SKILL_LEVEL + (newLevel - TIER1_MAX_LEVEL - 1) / FULL_CYCLE.length + 1;
        return t.display() + " Lv" + bonusLevel + "로 성장!";
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
        return switch (Math.min(skillLevel, MAX_SKILL_LEVEL)) {
            case 1 -> 60;
            case 2 -> 50;
            case 3 -> 40;
            case 4 -> 30;
            case 5 -> 20;
            default -> 60;
        };
    }
}
