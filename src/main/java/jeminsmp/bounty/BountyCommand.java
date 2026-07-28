package jeminsmp.bounty;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BountyCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;

    public BountyCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용 가능합니다.");
            return true;
        }

        BountyManager bm = plugin.getBountyManager();

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            handleList(player, bm);
            return true;
        }

        if (args[0].equalsIgnoreCase("cancel")) {
            if (args.length < 2) { player.sendMessage("§c사용법: /bounty cancel <플레이어>"); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            UUID targetUUID = target != null ? target.getUniqueId() : findUUIDByName(bm, args[1]);
            if (targetUUID == null) { player.sendMessage("§c현상금이 걸린 플레이어를 찾을 수 없습니다."); return true; }
            if (!bm.cancelBounty(player, targetUUID, args[1]))
                player.sendMessage("§c" + args[1] + "님에게 걸어둔 현상금이 없습니다.");
            return true;
        }

        // /bounty <플레이어> <금액>
        if (args.length < 2) { player.sendMessage("§c사용법: /bounty <플레이어> <다이아개수>  |  /bounty list  |  /bounty cancel <플레이어>"); return true; }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { player.sendMessage("§c온라인 플레이어만 지정할 수 있습니다."); return true; }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage("§c금액은 1 이상의 숫자여야 합니다.");
            return true;
        }

        bm.addBounty(player, target.getUniqueId(), target.getName(), amount);
        return true;
    }

    private void handleList(Player player, BountyManager bm) {
        var all = bm.getAllBounties();
        if (all.isEmpty()) {
            player.sendMessage("§7현재 걸린 현상금이 없습니다.");
            return;
        }
        player.sendMessage("§6§l=== 현상금 목록 ===");
        for (var entry : all.entrySet()) {
            UUID targetUUID = entry.getKey();
            int total = entry.getValue().stream().mapToInt(BountyManager.BountyEntry::amount).sum();
            // 이름 찾기
            Player online = Bukkit.getPlayer(targetUUID);
            String name = online != null ? online.getName()
                    : entry.getValue().isEmpty() ? "알 수 없음"
                    : targetUUID.toString().substring(0, 8);
            String placers = entry.getValue().stream()
                    .map(e -> e.placerName() + "(💎" + e.amount() + ")")
                    .reduce((a, b) -> a + ", " + b).orElse("");
            player.sendMessage("§c" + name + " §7— §b💎 " + total + "개 §8(" + placers + ")");
        }
    }

    private UUID findUUIDByName(BountyManager bm, String name) {
        for (UUID uuid : bm.getAllBounties().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.getName().equalsIgnoreCase(name)) return uuid;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(List.of("list", "cancel"));
            Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(list::add);
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cancel")) {
            // 현상금 걸린 플레이어 목록
            return plugin.getBountyManager().getAllBounties().keySet().stream()
                    .map(uuid -> { Player p = Bukkit.getPlayer(uuid); return p != null ? p.getName() : null; })
                    .filter(n -> n != null)
                    .toList();
        }
        if (args.length == 2) return List.of("<다이아개수>");
        return List.of();
    }
}
