package jeminsmp.trade;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class TradeCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;

    public TradeCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return true;
        }

        TradeManager tm = plugin.getTradeManager();

        if (args.length == 0) {
            player.sendMessage("§c사용법: /거래 <플레이어명> | /거래 수락 <플레이어명> | /거래 거절 <플레이어명>");
            return true;
        }

        // 수락
        if (args[0].equalsIgnoreCase("수락") || args[0].equalsIgnoreCase("accept")) {
            if (args.length < 2) { player.sendMessage("§c사용법: /거래 수락 <플레이어명>"); return true; }
            Player requester = Bukkit.getPlayerExact(args[1]);
            if (requester == null) { player.sendMessage("§c해당 플레이어가 없습니다."); return true; }
            tm.acceptRequest(player, requester);
            return true;
        }

        // 거절
        if (args[0].equalsIgnoreCase("거절") || args[0].equalsIgnoreCase("deny")) {
            if (args.length < 2) { player.sendMessage("§c사용법: /거래 거절 <플레이어명>"); return true; }
            Player requester = Bukkit.getPlayerExact(args[1]);
            if (requester == null) { player.sendMessage("§c해당 플레이어가 없습니다."); return true; }
            tm.denyRequest(player, requester);
            return true;
        }

        // 취소
        if (args[0].equalsIgnoreCase("취소") || args[0].equalsIgnoreCase("cancel")) {
            if (!tm.isInTrade(player)) { player.sendMessage("§c현재 거래 중이 아닙니다."); return true; }
            tm.cancelTrade(player);
            return true;
        }

        // 거래 요청
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null)             { player.sendMessage("§c" + args[0] + " 님이 온라인 상태가 아닙니다."); return true; }
        if (target.equals(player))      { player.sendMessage("§c자기 자신에게 거래를 요청할 수 없습니다."); return true; }

        tm.sendRequest(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> opts = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            opts.add("수락");
            opts.add("거절");
            opts.add("취소");
            return opts.stream().filter(s -> s.toLowerCase().startsWith(input)).toList();
        }
        if (args.length == 2 && (args[0].equals("수락") || args[0].equals("거절"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
