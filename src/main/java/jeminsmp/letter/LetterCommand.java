package jeminsmp.letter;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class LetterCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;

    public LetterCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }

        LetterManager manager = plugin.getLetterManager();
        String cmd = command.getName().toLowerCase();

        // /편지함
        if (cmd.equals("편지함")) {
            List<LetterManager.LetterData> pending = manager.getPending(player.getUniqueId());
            if (pending.isEmpty()) {
                player.sendMessage("§7편지함이 비어 있습니다.");
            } else {
                player.sendMessage("§6✉ §e편지함에 §f" + pending.size() + "§e통의 편지가 있습니다.");
                player.sendMessage("§7편지는 로그인 시 자동으로 전달됩니다.");
            }
            return true;
        }

        // /편지 <닉네임> <메시지...>
        if (args.length < 2) {
            player.sendMessage("§c사용법: /편지 <닉네임> <메시지>");
            return true;
        }

        String targetName = args[0];
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(" ");
            sb.append(args[i]);
        }
        String message = sb.toString();

        // Find target
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        UUID targetUUID;
        String resolvedName;

        if (onlineTarget != null) {
            targetUUID = onlineTarget.getUniqueId();
            resolvedName = onlineTarget.getName();
        } else {
            @SuppressWarnings("deprecation")
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
            targetUUID = offlinePlayer.getUniqueId();
            resolvedName = offlinePlayer.getName() != null ? offlinePlayer.getName() : targetName;
        }

        manager.sendLetter(player.getName(), targetUUID, resolvedName, message);
        player.sendMessage("§a편지를 §f" + resolvedName + "§a님에게 보냈습니다.");
        return true;
    }
}
