package jeminsmp.waypoint;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class WaypointCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;

    public WaypointCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용 가능합니다.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 2) { player.sendMessage("§c사용법: /wp add <이름>"); return true; }
                String name = args[1];
                if (!plugin.getWaypointManager().add(player.getUniqueId(), name, player.getLocation())) {
                    player.sendMessage("§c웨이포인트는 최대 10개까지 저장할 수 있습니다.");
                    return true;
                }
                Location loc = player.getLocation();
                player.sendMessage("§a웨이포인트 §e" + name + " §a저장! §7("
                        + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
            }
            case "list" -> {
                Set<UUID> teammates = plugin.getTeamManager().getTeammates(player.getUniqueId());
                var list = plugin.getWaypointManager().getVisible(player.getUniqueId(), teammates);
                if (list.isEmpty()) { player.sendMessage("§7저장된 웨이포인트가 없습니다."); return true; }
                player.sendMessage("§6§l📍 웨이포인트 목록");
                for (var entry : list) {
                    var wp = entry.getValue();
                    player.sendMessage("§7• §f" + entry.getKey()
                            + " §7— " + wp.world() + " §e(" + wp.x() + ", " + wp.y() + ", " + wp.z() + ")"
                            + (wp.shared() ? " §a[공유중]" : ""));
                }
            }
            case "del", "delete", "remove" -> {
                if (args.length < 2) { player.sendMessage("§c사용법: /wp del <이름>"); return true; }
                if (!plugin.getWaypointManager().delete(player.getUniqueId(), args[1])) {
                    player.sendMessage("§c웨이포인트를 찾을 수 없습니다."); return true;
                }
                player.sendMessage("§a웨이포인트 §e" + args[1] + " §a삭제 완료.");
            }
            case "share" -> {
                if (args.length < 2) { player.sendMessage("§c사용법: /wp share <이름>"); return true; }
                if (!plugin.getWaypointManager().share(player.getUniqueId(), args[1])) {
                    player.sendMessage("§c웨이포인트를 찾을 수 없습니다."); return true;
                }
                var wp = plugin.getWaypointManager().get(player.getUniqueId(), args[1]);
                player.sendMessage(wp != null && wp.shared()
                        ? "§a웨이포인트 §e" + args[1] + " §a팀 공유 활성화."
                        : "§7웨이포인트 §e" + args[1] + " §7팀 공유 비활성화.");
            }
            default -> showHelp(player);
        }
        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage("§6§l📍 웨이포인트 명령어");
        player.sendMessage("§e/wp add <이름> §7— 현재 위치 저장 (최대 10개)");
        player.sendMessage("§e/wp list §7— 목록 보기 (팀 공유 포함)");
        player.sendMessage("§e/wp del <이름> §7— 삭제");
        player.sendMessage("§e/wp share <이름> §7— 팀 공유 토글");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) return List.of("add", "list", "del", "share");
        if (args.length == 2 && !args[0].equalsIgnoreCase("add") && !args[0].equalsIgnoreCase("list")) {
            return new ArrayList<>(plugin.getWaypointManager().getNames(player.getUniqueId()));
        }
        return List.of();
    }
}
