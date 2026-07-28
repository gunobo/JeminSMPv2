package jeminsmp.shop.commands;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class MailboxCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;

    public MailboxCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        var uuid = player.getUniqueId();
        int pendingDiamonds = plugin.getMailboxManager().getDiamonds(uuid);
        var pendingItems = plugin.getMailboxManager().getItems(uuid);
        if (pendingDiamonds == 0 && pendingItems.isEmpty()) {
            player.sendMessage(Component.text("📭 받을 게 없습니다.", NamedTextColor.GRAY));
            return true;
        }

        if (pendingDiamonds > 0) {
            int claimed = plugin.getMailboxManager().claimAndClear(uuid);
            int remaining = claimed;
            while (remaining > 0) {
                int stackSize = Math.min(remaining, 64);
                var leftover = player.getInventory().addItem(new ItemStack(Material.DIAMOND, stackSize));
                leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
                remaining -= stackSize;
            }
            player.sendMessage(Component.text("📬 우편함에서 💎 " + claimed + "개의 다이아몬드를 수령했습니다!", NamedTextColor.GOLD));
        }

        if (!pendingItems.isEmpty()) {
            var claimed = plugin.getMailboxManager().claimAndClearItems(uuid);
            for (var item : claimed) {
                var leftover = player.getInventory().addItem(item);
                leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            }
            player.sendMessage(Component.text("📬 우편함에서 아이템 " + claimed.size() + "개를 수령했습니다!", NamedTextColor.GOLD));
        }
        return true;
    }
}
