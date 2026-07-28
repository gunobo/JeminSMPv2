package jeminsmp.autotitle;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class AutoTitleManager {

    // 킬 수 → {id, 표시}
    public static final Map<Integer, String[]> KILL_TITLES = new LinkedHashMap<>();
    // 플레이타임(시간) → {id, 표시}
    public static final Map<Integer, String[]> TIME_TITLES = new LinkedHashMap<>();
    // 도전과제 수 → {id, 표시}
    public static final Map<Integer, String[]> ADV_TITLES = new LinkedHashMap<>();

    static {
        KILL_TITLES.put(1,   new String[]{"auto_kill_1",   "&f[🗡 입문자]"});
        KILL_TITLES.put(10,  new String[]{"auto_kill_10",  "&a[🗡 사냥꾼]"});
        KILL_TITLES.put(30,  new String[]{"auto_kill_30",  "&6[⚔ 전사]"});
        KILL_TITLES.put(50,  new String[]{"auto_kill_50",  "&c[💀 암살자]"});
        KILL_TITLES.put(100, new String[]{"auto_kill_100", "&4&l[☠ 학살자]"});

        TIME_TITLES.put(1,  new String[]{"auto_time_1h",  "&7[🌱 새싹]"});
        TIME_TITLES.put(5,  new String[]{"auto_time_5h",  "&e[⭐ 모험가]"});
        TIME_TITLES.put(20, new String[]{"auto_time_20h", "&b[💎 베테랑]"});
        TIME_TITLES.put(50, new String[]{"auto_time_50h", "&5[👑 전설]"});

        ADV_TITLES.put(10,  new String[]{"auto_adv_10",  "&a[📜 탐험가]"});
        ADV_TITLES.put(25,  new String[]{"auto_adv_25",  "&e[📜 모험가]"});
        ADV_TITLES.put(50,  new String[]{"auto_adv_50",  "&6[📜 탐구자]"});
        ADV_TITLES.put(75,  new String[]{"auto_adv_75",  "&b[📜 개척자]"});
        ADV_TITLES.put(100, new String[]{"auto_adv_100", "&d&l[📜 완성자]"});
    }

    private final JeminSMPPlugin plugin;

    public AutoTitleManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        registerAutoTitles();
        // 5분마다 모든 온라인 플레이어 플레이타임 체크
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) checkPlaytime(p);
        }, 6000L, 6000L); // 6000틱 = 5분
    }

    private void registerAutoTitles() {
        var tm = plugin.getTitleManager();
        KILL_TITLES.values().forEach(arr -> {
            if (tm.getTitle(arr[0]) == null) tm.createTitle(arr[0], 0, arr[1]);
        });
        TIME_TITLES.values().forEach(arr -> {
            if (tm.getTitle(arr[0]) == null) tm.createTitle(arr[0], 0, arr[1]);
        });
        ADV_TITLES.values().forEach(arr -> {
            if (tm.getTitle(arr[0]) == null) tm.createTitle(arr[0], 0, arr[1]);
        });
    }

    // ── 킬 달성 체크 (KillsListener에서 킬마다 호출) ──
    public void checkKills(Player player) {
        var stats = plugin.getKillsManager().getStats(player.getUniqueId());
        if (stats == null) return;
        int kills = stats.kills;

        String bestId = null, bestDisplay = null;
        for (var entry : KILL_TITLES.entrySet()) {
            if (kills >= entry.getKey()) { bestId = entry.getValue()[0]; bestDisplay = entry.getValue()[1]; }
        }
        if (bestId != null) grantIfNew(player, bestId, bestDisplay, "킬 달성");
    }

    // ── 플레이타임 체크 (5분마다 + 접속 시 호출) ──
    public void checkPlaytime(Player player) {
        int ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        int hours = ticks / 72000;

        String bestId = null, bestDisplay = null;
        for (var entry : TIME_TITLES.entrySet()) {
            if (hours >= entry.getKey()) { bestId = entry.getValue()[0]; bestDisplay = entry.getValue()[1]; }
        }
        if (bestId != null) grantIfNew(player, bestId, bestDisplay, "플레이타임 달성");
    }

    private void grantIfNew(Player player, String id, String display, String reason) {
        var tm = plugin.getTitleManager();
        if (tm.hasTitle(player.getUniqueId(), id)) return;

        tm.giveTitle(player.getUniqueId(), id);
        String coloredDisplay = LegacyComponentSerializer.legacySection().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(display));
        player.sendMessage("§6🏅 §f새 칭호 획득! " + coloredDisplay + " §7(" + reason + ")");

        // 장착 중인 칭호 없으면 자동 장착
        if (tm.getEquippedDisplay(player.getUniqueId()) == null) {
            tm.equipTitle(player.getUniqueId(), id);
            player.sendMessage("§7칭호가 자동으로 장착되었습니다. §e/title equip <id> §7로 변경 가능");
        }
    }

    // ── 도전과제 달성 체크 (PlayerAdvancementDoneEvent에서 호출) ──
    public void checkAdvancements(Player player) {
        int count = countAdvancements(player);
        String bestId = null, bestDisplay = null;
        for (var entry : ADV_TITLES.entrySet()) {
            if (count >= entry.getKey()) { bestId = entry.getValue()[0]; bestDisplay = entry.getValue()[1]; }
        }
        if (bestId != null) grantIfNew(player, bestId, bestDisplay, "발전과제 " + count + "개 달성");
    }

    /**
     * 달성한 발전과제 수 카운트
     * - 레시피 발전과제 제외
     * - display == null 인 루트/숨김 발전과제 제외
     * - 발전과제(task) + 목표(goal) + 도전과제(challenge) 전부 포함
     */
    public int countAdvancements(Player player) {
        int count = 0;
        Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement adv = it.next();
            String key = adv.getKey().getKey();
            if (key.startsWith("recipes/")) continue;   // 레시피 제외
            if (adv.getDisplay() == null) continue;      // 루트·숨김 제외
            AdvancementProgress progress = player.getAdvancementProgress(adv);
            if (progress.isDone()) count++;
        }
        return count;
    }

    // ── PlaytimeCommand에서 진척도 표시용 ──
    public int getKillsNextThreshold(int kills) {
        for (int threshold : KILL_TITLES.keySet()) {
            if (kills < threshold) return threshold;
        }
        return -1; // 최대 달성
    }

    public int getTimeNextThreshold(int hours) {
        for (int threshold : TIME_TITLES.keySet()) {
            if (hours < threshold) return threshold;
        }
        return -1;
    }

    public String getKillsNextDisplay(int kills) {
        for (var entry : KILL_TITLES.entrySet()) {
            if (kills < entry.getKey()) return entry.getValue()[1];
        }
        return null;
    }

    public String getTimeNextDisplay(int hours) {
        for (var entry : TIME_TITLES.entrySet()) {
            if (hours < entry.getKey()) return entry.getValue()[1];
        }
        return null;
    }

    public int getAdvNextThreshold(int count) {
        for (int threshold : ADV_TITLES.keySet()) {
            if (count < threshold) return threshold;
        }
        return -1;
    }

    public String getAdvNextDisplay(int count) {
        for (var entry : ADV_TITLES.entrySet()) {
            if (count < entry.getKey()) return entry.getValue()[1];
        }
        return null;
    }
}
