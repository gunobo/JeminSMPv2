package jeminsmp.compensation;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class ClaimCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;

    public ClaimCommand(JeminSMPPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }

        CompensationManager cm = plugin.getCompensationManager();

        // /claim — 목록
        if (args.length == 0) {
            List<CompensationManager.CompEvent> events = cm.getActiveEvents();
            if (events.isEmpty()) {
                player.sendMessage(Component.text("📦 현재 수령 가능한 보상이 없습니다.", NamedTextColor.GRAY));
                return true;
            }
            player.sendMessage(Component.text("━━━ 📦 수령 가능한 보상 ━━━", NamedTextColor.GOLD));
            for (var ev : events) {
                boolean claimed = cm.hasClaimed(player.getUniqueId(), ev.id());
                String status = claimed ? " §7[수령완료]" : " §a[/claim " + ev.id() + "]";
                player.sendMessage(Component.text(
                        "§e[" + ev.id() + "] §f" + ev.description() + status, NamedTextColor.WHITE));
                player.sendMessage(Component.text(
                        "   보상: " + cm.formatRewards(ev) + "  §7(" + cm.formatExpiry(ev.expiresAt()) + ")",
                        NamedTextColor.GRAY));
            }
            return true;
        }

        // /claim <id>
        String id = args[0];
        CompensationManager.CompEvent ev = cm.getEvent(id);

        if (ev == null) {
            player.sendMessage(Component.text("❌ 존재하지 않는 보상 ID입니다: " + id, NamedTextColor.RED));
            return true;
        }
        if (cm.isExpired(ev)) {
            player.sendMessage(Component.text("❌ 이미 기간이 만료된 보상입니다.", NamedTextColor.RED));
            return true;
        }
        if (cm.hasClaimed(player.getUniqueId(), id)) {
            player.sendMessage(Component.text("❌ 이미 수령한 보상입니다.", NamedTextColor.RED));
            return true;
        }

        // 수령 처리 (먼저 DB에 기록)
        if (!cm.claim(player.getUniqueId(), id)) {
            player.sendMessage(Component.text("❌ 보상 수령에 실패했습니다.", NamedTextColor.RED));
            return true;
        }

        // 아이템 지급 — 못 들어가는 건 바닥 드랍
        boolean dropped = false;
        for (var item : ev.items()) {
            var leftover = player.getInventory().addItem(item.clone());
            if (!leftover.isEmpty()) {
                leftover.values().forEach(i ->
                        player.getWorld().dropItemNaturally(player.getLocation(), i));
                dropped = true;
            }
        }

        player.sendMessage(Component.text("━━━ 📦 보상 수령 완료 ━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  " + ev.description(), NamedTextColor.WHITE));
        player.sendMessage(Component.text("  " + cm.formatRewards(ev), NamedTextColor.AQUA));
        if (dropped) {
            player.sendMessage(Component.text(
                    "  ⚠ 인벤 초과 아이템은 바닥에 드랍되었습니다.", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return plugin.getCompensationManager().getActiveEvents().stream()
                    .map(CompensationManager.CompEvent::id)
                    .filter(id -> id.startsWith(args[0]))
                    .toList();
        }
        return List.of();
    }
}
