package jeminsmp.death;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LastWillCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;

    public LastWillCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }

        LastWillManager manager = plugin.getLastWillManager();

        if (args.length == 0) {
            String will = manager.getWill(player.getUniqueId());
            if (will == null) {
                player.sendMessage("§7설정된 유언이 없습니다.");
            } else {
                player.sendMessage("§8[§c유언§8] §7현재 유언: §f\"" + will + "\"");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("clear")) {
            manager.clearWill(player.getUniqueId());
            player.sendMessage("§a유언이 삭제되었습니다.");
            return true;
        }

        String message = String.join(" ", args);
        manager.setWill(player.getUniqueId(), message);
        player.sendMessage("§a유언이 설정되었습니다: §f\"" + message + "\"");
        return true;
    }
}
