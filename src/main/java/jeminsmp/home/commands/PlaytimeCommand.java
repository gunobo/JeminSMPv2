package jeminsmp.home.commands;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.autotitle.AutoTitleManager;
import jeminsmp.kills.KillsManager.PlayerStats;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PlaytimeCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;

    public PlaytimeCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        // ── 플레이타임 ──
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long totalSeconds = ticks / 20L;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        // ── 킬 ──
        PlayerStats stats = plugin.getKillsManager().getStats(player.getUniqueId());
        int kills  = stats != null ? stats.kills  : 0;
        int deaths = stats != null ? stats.deaths : 0;

        AutoTitleManager atm = plugin.getAutoTitleManager();

        player.sendMessage("§6§l=== 나의 통계 ===");

        // 플레이타임
        player.sendMessage(String.format("§e⏱ 플레이타임: §f%d시간 %d분 %d초", hours, minutes, seconds));

        // 플레이타임 자동 칭호 진척도
        int nextTimeH   = atm.getTimeNextThreshold((int) hours);
        String nextTimeDisplay = atm.getTimeNextDisplay((int) hours);
        if (nextTimeH == -1) {
            player.sendMessage("§a  └ 모든 플레이타임 칭호 달성! 👑");
        } else {
            long remainSec = (nextTimeH * 3600L) - totalSeconds;
            long rh = remainSec / 3600, rm = (remainSec % 3600) / 60;
            String nextName = colorStrip(nextTimeDisplay);
            player.sendMessage(String.format("§7  └ 다음 칭호 §f%s §7까지: §c%d시간 %d분 남음", nextName, rh, rm));
        }

        // 킬
        player.sendMessage(String.format("§e⚔ 킬: §f%d  §e💀 데스: §f%d", kills, deaths));

        // 킬 자동 칭호 진척도
        int nextKill = atm.getKillsNextThreshold(kills);
        String nextKillDisplay = atm.getKillsNextDisplay(kills);
        if (nextKill == -1) {
            player.sendMessage("§a  └ 모든 킬 칭호 달성! ☠");
        } else {
            String nextName = colorStrip(nextKillDisplay);
            player.sendMessage(String.format("§7  └ 다음 칭호 §f%s §7까지: §c%d킬 남음", nextName, nextKill - kills));
        }

        // 발전과제 (발전과제+목표+도전과제 전부 포함, 레시피·루트 제외)
        int advCount = atm.countAdvancements(player);
        player.sendMessage(String.format("§e📜 발전과제: §f%d개 달성", advCount));
        int nextAdv = atm.getAdvNextThreshold(advCount);
        String nextAdvDisplay = atm.getAdvNextDisplay(advCount);
        if (nextAdv == -1) {
            player.sendMessage("§a  └ 모든 발전과제 칭호 달성! 📜");
        } else {
            player.sendMessage(String.format("§7  └ 다음 칭호 §f%s §7까지: §c%d개 남음",
                    colorStrip(nextAdvDisplay), nextAdv - advCount));
        }

        // 현재 장착 칭호
        String equipped = plugin.getTitleManager().getEquippedDisplay(player.getUniqueId());
        if (equipped != null) {
            String eq = LegacyComponentSerializer.legacySection().serialize(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(equipped));
            player.sendMessage("§e🏅 장착 칭호: " + eq);
        } else {
            player.sendMessage("§7🏅 장착 칭호: §8없음");
        }

        return true;
    }

    private String colorStrip(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)[&§][0-9a-fk-or]", "");
    }
}
