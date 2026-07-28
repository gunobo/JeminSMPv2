package jeminsmp.kills.commands;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.kills.KillsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class KillsCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;

    public KillsCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("플레이어만 인수 없이 사용할 수 있습니다. /kills top 을 사용하세요.");
                return true;
            }
            showStats(sender, player.getUniqueId(), player.getName(),
                    plugin.getKillsManager().getCurrentStreak(player.getUniqueId()));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "top" -> {
                String mode = args.length >= 2 ? args[1].toLowerCase() : "kills";
                showLeaderboard(sender, mode);
            }
            case "reset" -> {
                if (!sender.hasPermission("jeminsmp.kills.reset")) {
                    sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("사용법: /kills reset <플레이어>", NamedTextColor.RED));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                plugin.getKillsManager().resetStats(target.getUniqueId());
                sender.sendMessage(Component.text(args[1] + " 님의 통계를 초기화했습니다.", NamedTextColor.GREEN));
            }
            default -> {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                KillsManager.PlayerStats s = plugin.getKillsManager().getStats(target.getUniqueId());
                if (s == null) {
                    sender.sendMessage(Component.text(args[0] + " 님의 기록이 없습니다.", NamedTextColor.GRAY));
                    return true;
                }
                int streak = plugin.getKillsManager().getCurrentStreak(target.getUniqueId());
                showStats(sender, s.uuid, s.name, streak);
            }
        }
        return true;
    }

    private void showStats(CommandSender sender, UUID uuid, String name, int currentStreak) {
        KillsManager.PlayerStats s = plugin.getKillsManager().getStats(uuid);
        if (s == null) {
            sender.sendMessage(Component.text("기록이 없습니다.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("⚔ ", NamedTextColor.GOLD)
                .append(Component.text(name, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" 님의 전투 통계", NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("  킬       : ", NamedTextColor.GRAY)
                .append(Component.text(s.kills + "킬", NamedTextColor.GREEN)));
        sender.sendMessage(Component.text("  데스     : ", NamedTextColor.GRAY)
                .append(Component.text(s.deaths + "회", NamedTextColor.RED)));
        sender.sendMessage(Component.text("  KDA      : ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f", s.kda()), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("  최대연속 : ", NamedTextColor.GRAY)
                .append(Component.text(s.maxStreak + "킬", NamedTextColor.YELLOW)));
        if (currentStreak > 0) {
            sender.sendMessage(Component.text("  현재연속 : ", NamedTextColor.GRAY)
                    .append(Component.text(currentStreak + "킬 진행 중 🔥", NamedTextColor.GOLD)));
        }
        sender.sendMessage(Component.text(""));
    }

    private void showLeaderboard(CommandSender sender, String mode) {
        List<KillsManager.PlayerStats> top;
        String title;
        switch (mode) {
            case "kda" -> { top = plugin.getKillsManager().getTopByKda(10); title = "KDA"; }
            case "streak" -> { top = plugin.getKillsManager().getTopByStreak(10); title = "최대 연속킬"; }
            default -> { top = plugin.getKillsManager().getTopByKills(10); title = "킬 수"; }
        }
        if (top.isEmpty()) {
            sender.sendMessage(Component.text("아직 기록이 없습니다.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("🏆 킬 랭킹 — " + title, NamedTextColor.GOLD, TextDecoration.BOLD));
        String[] medals = {"🥇", "🥈", "🥉"};
        for (int i = 0; i < top.size(); i++) {
            KillsManager.PlayerStats s = top.get(i);
            String rank = i < 3 ? medals[i] : "  " + (i + 1) + ".";
            String value = switch (mode) {
                case "kda" -> String.format("%.2f KDA", s.kda()) + "  (" + s.kills + "킬 / " + s.deaths + "데스)";
                case "streak" -> s.maxStreak + "킬 연속";
                default -> s.kills + "킬  (" + s.deaths + "데스 / KDA " + String.format("%.2f", s.kda()) + ")";
            };
            sender.sendMessage(Component.text(rank + " ", NamedTextColor.YELLOW)
                    .append(Component.text(s.name, NamedTextColor.WHITE))
                    .append(Component.text("  " + value, NamedTextColor.GRAY)));
        }
        sender.sendMessage(Component.text(""));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("top", "reset");
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) return List.of("kills", "kda", "streak");
        if (args.length == 2 && args[0].equalsIgnoreCase("reset"))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
