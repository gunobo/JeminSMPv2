package jeminsmp.leaderboard;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.kills.KillsManager.PlayerStats;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private static final int TOP_N = 10;
    private final JeminSMPPlugin plugin;

    public LeaderboardCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String type = args.length > 0 ? args[0].toLowerCase() : "kills";

        switch (type) {
            case "kills" -> showKills(sender);
            case "deaths" -> showDeaths(sender);
            case "kda"    -> showKda(sender);
            case "streak" -> showStreak(sender);
            default -> {
                sender.sendMessage("§c사용법: /top <kills|deaths|kda|streak>");
                return true;
            }
        }
        return true;
    }

    private void showKills(CommandSender sender) {
        List<PlayerStats> list = plugin.getKillsManager().getTopByKills(TOP_N);
        sender.sendMessage("§6§l🏆 킬 순위 TOP " + Math.min(TOP_N, list.size()));
        for (int i = 0; i < list.size(); i++) {
            PlayerStats s = list.get(i);
            sender.sendMessage(rank(i + 1) + " §f" + s.name + " §7— §e" + s.kills + "킬 §7/ §c" + s.deaths + "데스");
        }
        if (list.isEmpty()) sender.sendMessage("§7아직 데이터가 없습니다.");
    }

    private void showDeaths(CommandSender sender) {
        List<PlayerStats> list = plugin.getKillsManager().getTopByDeaths(TOP_N);
        sender.sendMessage("§c§l💀 데스 순위 TOP " + Math.min(TOP_N, list.size()));
        for (int i = 0; i < list.size(); i++) {
            PlayerStats s = list.get(i);
            sender.sendMessage(rank(i + 1) + " §f" + s.name + " §7— §c" + s.deaths + "데스 §7/ §e" + s.kills + "킬");
        }
        if (list.isEmpty()) sender.sendMessage("§7아직 데이터가 없습니다.");
    }

    private void showKda(CommandSender sender) {
        List<PlayerStats> list = plugin.getKillsManager().getTopByKda(TOP_N);
        sender.sendMessage("§a§l⚔ KDA 순위 TOP " + Math.min(TOP_N, list.size()));
        for (int i = 0; i < list.size(); i++) {
            PlayerStats s = list.get(i);
            sender.sendMessage(rank(i + 1) + " §f" + s.name
                    + " §7— §aKDA: §e" + String.format("%.2f", s.kda())
                    + " §7(§e" + s.kills + "§7/§c" + s.deaths + "§7)");
        }
        if (list.isEmpty()) sender.sendMessage("§7아직 데이터가 없습니다.");
    }

    private void showStreak(CommandSender sender) {
        List<PlayerStats> list = plugin.getKillsManager().getTopByStreak(TOP_N);
        sender.sendMessage("§d§l🔥 최고 연속킬 순위 TOP " + Math.min(TOP_N, list.size()));
        for (int i = 0; i < list.size(); i++) {
            PlayerStats s = list.get(i);
            sender.sendMessage(rank(i + 1) + " §f" + s.name + " §7— §d" + s.maxStreak + "연속킬");
        }
        if (list.isEmpty()) sender.sendMessage("§7아직 데이터가 없습니다.");
    }

    private String rank(int i) {
        return switch (i) {
            case 1 -> "§6§l#1";
            case 2 -> "§7§l#2";
            case 3 -> "§c§l#3";
            default -> "§8#" + i;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("kills", "deaths", "kda", "streak");
        return List.of();
    }
}
