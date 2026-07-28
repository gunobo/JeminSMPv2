package jeminsmp.death.commands;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DeathPointCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;

    public DeathPointCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용 가능합니다.");
            return true;
        }

        Location loc = plugin.getDeathManager().getLastDeath(player.getUniqueId());
        if (loc == null || loc.getWorld() == null) {
            player.sendMessage("§c기록된 사망 위치가 없습니다.");
            return true;
        }

        player.sendMessage("§c☠ §f마지막 사망 위치: §e"
                + loc.getWorld().getName()
                + " §7(§fX: §e" + loc.getBlockX()
                + " §fY: §e" + loc.getBlockY()
                + " §fZ: §e" + loc.getBlockZ() + "§7)");
        return true;
    }
}
