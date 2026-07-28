package jeminsmp.nick.commands;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NickCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;

    public NickCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§e사용법: §f/nick <닉네임>  §7또는  §f/nick reset");
            if (plugin.getNickManager().hasNick(player.getUniqueId()))
                player.sendMessage("§7현재 닉네임: " + plugin.getNickManager().getRawNick(player.getUniqueId()));
            return true;
        }
        if (args[0].equalsIgnoreCase("reset")) {
            plugin.getNickManager().resetNick(player);
            player.sendMessage("§a닉네임이 초기화되었습니다. (본래 이름: §f" + player.getName() + "§a)");
            return true;
        }
        String rawNick = String.join(" ", args);
        String error = plugin.getNickManager().setNick(player, rawNick);
        if (error != null) { player.sendMessage(error); return true; }
        player.sendMessage("§a닉네임이 설정되었습니다! §r"
                + LegacyComponentSerializer.legacySection().serialize(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(rawNick)));
        return true;
    }
}
