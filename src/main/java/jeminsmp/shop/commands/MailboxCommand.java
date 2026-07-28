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
        int pending = plugin.getMailboxManager().getDiamonds(player.getUniqueId());
        if (pending == 0) {
            player.sendMessage(Component.text("📭 받을 다이아몬드가 없습니다.", NamedTextColor.GRAY));
            return true;
        }
        int claimed = plugin.getMailboxManager().claimAndClear(player.getUniqueId());
        int remaining = claimed;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64);
            var leftover = player.getInventory().addItem(new ItemStack(Material.DIAMOND, stackSize));
            leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            remaining -= stackSize;
        }
        player.sendMessage(Component.text("📬 우편함에서 💎 " + claimed + "개의 다이아몬드를 수령했습니다!", NamedTextColor.GOLD));
        return true;
    }
}
