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

    // 1차 전직 조건: 만렙 + 누적 활동(직업 공통 행동) + 누적 희귀 활동(직업별로 다름)
    private static final Map<JobType, Long> TIER1_MAIN_REQ = Map.of(
            JobType.MINER, 10_000L, JobType.FARMER, 10_000L, JobType.WARRIOR, 100_000L, JobType.FISHER, 10_000L
    );
    private static final Map<JobType, Long> TIER1_RARE_REQ = Map.of(
            JobType.MINER, 500L, JobType.FARMER, 1_000L, JobType.WARRIOR, 100L, JobType.FISHER, 50L
    );
    private static final Map<JobType, String> TIER1_RARE_LABEL = Map.of(
            JobType.MINER, "희귀 광물(다이아몬드/고대 잔해)",
            JobType.FARMER, "네더워트 수확",
            JobType.WARRIOR, "플레이어 처치",
            JobType.FISHER, "보물 낚시"
    );

    // 2차 전직 — 지금은 전사만 존재. 다른 직업은 다음 단계가 아직 없어서 canAdvance가 항상 거짓
    private static final Map<JobType, Long> TIER2_MAIN_REQ = Map.of(JobType.WARRIOR, 500_000L);
    private static final Map<JobType, Long> TIER2_RARE_REQ = Map.of(JobType.WARRIOR, 1_000L);
    private static final Map<JobType, String> TIER2_RARE_LABEL = Map.of(JobType.WARRIOR, "플레이어 처치");

    private static Map<JobType, Long> mainReq(int tier) { return tier <= 1 ? TIER1_MAIN_REQ : TIER2_MAIN_REQ; }
    private static Map<JobType, Long> rareReq(int tier) { return tier <= 1 ? TIER1_RARE_REQ : TIER2_RARE_REQ; }
    private static Map<JobType, String> rareLabel(int tier) { return tier <= 1 ? TIER1_RARE_LABEL : TIER2_RARE_LABEL; }

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

    public static class JobData {
        public JobType job;
        public int level = 1;
        public long exp = 0;
        // 현재 선택 안 된 다른 직업들의 저장된 진행도 (전직해도 안 사라지게)
        final Map<JobType, Integer> savedLevel = new EnumMap<>(JobType.class);
        final Map<JobType, Long> savedExp = new EnumMap<>(JobType.class);
        // 전직용 누적치(레벨업으로 소비되는 exp와 달리 절대 안 줄어듦) + 직업별 전직 단계(0=없음, 1=1차, 2=2차...)
        final Map<JobType, Long> lifetimeMain = new EnumMap<>(JobType.class);
        final Map<JobType, Long> lifetimeRare = new EnumMap<>(JobType.class);
        final Map<JobType, Integer> advanceTier = new EnumMap<>(JobType.class);
        boolean warriorDropBoost = false; // 전사 2차 전직 전용 토글(아이템 드랍 2배)

        int tier(JobType job) { return advanceTier.getOrDefault(job, 0); }

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
                var lifetimeSec = cfg.getConfigurationSection("players." + uuidStr + ".lifetime");
                if (lifetimeSec != null) {
                    for (String jobKey : lifetimeSec.getKeys(false)) {
                        JobType jt = JobType.fromString(jobKey);
                        if (jt == null) continue;
                        d.lifetimeMain.put(jt, cfg.getLong("players." + uuidStr + ".lifetime." + jobKey + ".main", 0));
                        d.lifetimeRare.put(jt, cfg.getLong("players." + uuidStr + ".lifetime." + jobKey + ".rare", 0));
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
                d.warriorDropBoost = cfg.getBoolean("players." + uuidStr + ".warriorDropBoost", false);
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
                cfg.set(path + ".lifetime." + le.getKey().name() + ".rare", d.lifetimeRare.getOrDefault(le.getKey(), 0L));
            }
            cfg.set(path + ".advanced", null);
            cfg.set(path + ".tier", null);
            for (var te : d.advanceTier.entrySet()) {
                cfg.set(path + ".tier." + te.getKey().name(), te.getValue());
            }
            cfg.set(path + ".warriorDropBoost", d.warriorDropBoost);
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

    /** 1차 전직 조건용 희귀 활동 누적(직업별로 다른 기준: 희귀광물/네더워트/플레이어처치/보물낚시) */
    public void addRareProgress(Player player, JobType job, long amount) {
        JobData d = getData(player.getUniqueId());
        if (d.job != job) return;
        d.lifetimeRare.merge(job, amount, Long::sum);
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
        long rare = d.lifetimeRare.getOrDefault(d.job, 0L);
        return main >= mainNeed && rare >= rareReq(nextTier).get(d.job);
    }

    public String advanceProgressText(UUID uuid) {
        JobData d = getData(uuid);
        if (d.job == null) return "직업이 없습니다.";
        int currentTier = d.tier(d.job);
        int nextTier = currentTier + 1;
        Long mainNeed = mainReq(nextTier).get(d.job);
        if (mainNeed == null) return (currentTier == 0 ? "전직 없음" : currentTier + "차 전직 완료") + " §7(다음 단계 없음)";
        long main = d.lifetimeMain.getOrDefault(d.job, 0L);
        long rare = d.lifetimeRare.getOrDefault(d.job, 0L);
        return "레벨 §f" + d.level + "§7/§f" + levelCapForTier(currentTier)
                + " §7| 누적 활동 §f" + main + "§7/§f" + mainNeed
                + " §7| " + rareLabel(nextTier).get(d.job) + " §f" + rare + "§7/§f" + rareReq(nextTier).get(d.job);
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

    /** 전사 2차 전직 전용: 아이템 드랍 2배 토글. 자격 없으면 null */
    public Boolean toggleWarriorDropBoost(Player player) {
        JobData d = getData(player.getUniqueId());
        if (d.job != JobType.WARRIOR || d.tier(JobType.WARRIOR) < 2) return null;
        d.warriorDropBoost = !d.warriorDropBoost;
        save();
        return d.warriorDropBoost;
    }

    public boolean isWarriorDropBoostActive(UUID uuid) {
        JobData d = getData(uuid);
        return d.job == JobType.WARRIOR && d.tier(JobType.WARRIOR) >= 2 && d.warriorDropBoost;
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
