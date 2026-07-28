package jeminsmp.kills;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class KillsListener implements Listener {

    private final JeminSMPPlugin plugin;
    private static final int[] ANNOUNCE_STREAKS = {3, 5, 10, 15, 20};

    // 스트릭 보상: {다이아, XP레벨}
    private static final int[][] STREAK_REWARDS = {
        // index 0 = streak 3: 다이아 3개
        {3,  0},   // 3킬
        {5,  0},   // 5킬
        {10, 3},   // 10킬 + 레벨 3
        {15, 5},   // 15킬 + 레벨 5
        {20, 10},  // 20킬 + 레벨 10
    };

    public KillsListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player dead   = event.getEntity();
        Player killer = dead.getKiller();

        int lostStreak = plugin.getKillsManager().registerDeath(dead.getUniqueId(), dead.getName());
        if (lostStreak >= 3) {
            dead.sendMessage(Component.text("💔 " + lostStreak + "연속킬이 끊겼습니다.", NamedTextColor.GRAY));
        }

        // 현상금 지급 (킬러 없어도 현상금은 소멸)
        if (killer != null && !killer.equals(dead)) {
            plugin.getBountyManager().onKill(killer, dead.getUniqueId(), dead.getName());
        } else {
            // 자살 또는 환경사 → 현상금 소멸 없이 유지 (선택: 그냥 둠)
        }

        if (killer == null || killer.equals(dead)) return;

        int streak = plugin.getKillsManager().registerKill(killer.getUniqueId(), killer.getName());
        plugin.getKillsManager().save();

        killer.sendMessage(Component.text("⚔ " + dead.getName() + " 처치! (연속킬: " + streak + ")", NamedTextColor.YELLOW));

        // 자동 칭호 체크
        if (plugin.getAutoTitleManager() != null)
            plugin.getAutoTitleManager().checkKills(killer);

        // 킬 스트릭 방송 & 보상
        for (int i = 0; i < ANNOUNCE_STREAKS.length; i++) {
            if (streak == ANNOUNCE_STREAKS[i]) {
                announceStreak(killer, streak);
                giveStreakReward(killer, streak, STREAK_REWARDS[i]);
                break;
            }
        }
    }

    // ── 연속킬 전체 방송 ──
    private void announceStreak(Player killer, int streak) {
        String icon   = streak >= 15 ? "☠" : streak >= 10 ? "💀" : "🔥";
        String suffix = streak >= 15 ? " 멈출 수 없다!" : streak >= 10 ? " 막아야 합니다!" : "";
        Component msg = Component.text(icon + " ", NamedTextColor.RED)
                .append(Component.text(killer.getName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" 님이 ", NamedTextColor.RED))
                .append(Component.text(streak + "킬", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" 연속!" + suffix, NamedTextColor.RED));
        Bukkit.broadcast(msg);
    }

    // ── 킬 스트릭 보상 지급 (KDA 보정) ──
    private void giveStreakReward(Player killer, int streak, int[] reward) {
        int baseDiamonds = reward[0];
        int xpLevels     = reward[1];

        // KDA 에 따라 다이아 보정
        int diamonds = applyKdaScaling(killer, baseDiamonds);

        killer.getInventory().addItem(new ItemStack(Material.DIAMOND, diamonds));

        StringBuilder msg = new StringBuilder("§6⚡ §e" + streak + "연속킬 보상! §b💎 다이아 " + diamonds + "개");
        if (diamonds != baseDiamonds) {
            double kda = getKda(killer);
            msg.append(String.format(" §7(기본 %d개 × KDA %.1f 보정)", baseDiamonds, kda));
        }
        if (xpLevels > 0) {
            killer.giveExpLevels(xpLevels);
            msg.append(" §a+ ").append(xpLevels).append(" 레벨");
        }
        killer.sendMessage(msg.toString());
    }

    /**
     * KDA 기반 다이아 보정
     * KDA < 1.0  → ×0.75
     * KDA 1~2    → ×1.0  (기본)
     * KDA 2~3    → ×1.25
     * KDA 3~5    → ×1.5
     * KDA 5+     → ×2.0
     */
    private int applyKdaScaling(Player killer, int base) {
        double kda = getKda(killer);
        double mult = kda < 1.0 ? 0.75
                    : kda < 2.0 ? 1.0
                    : kda < 3.0 ? 1.25
                    : kda < 5.0 ? 1.5
                    : 2.0;
        return Math.max(1, (int) Math.round(base * mult));
    }

    private double getKda(Player killer) {
        var stats = plugin.getKillsManager().getStats(killer.getUniqueId());
        return stats != null ? stats.kda() : 1.0;
    }
}
