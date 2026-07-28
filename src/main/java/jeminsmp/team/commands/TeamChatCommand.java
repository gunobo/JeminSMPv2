package jeminsmp.team.commands;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.team.TeamListener;
import jeminsmp.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeamChatCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;

    public TeamChatCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("플레이어만 사용할 수 있습니다."); return true; }
        if (args.length == 0) { player.sendMessage(Component.text("사용법: /tc <메시지>", NamedTextColor.RED)); return true; }
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(Component.text("팀에 소속되어 있지 않습니다.", NamedTextColor.RED)); return true; }
        TeamListener.sendTeamChat(player, team, String.join(" ", args));
        return true;
    }
}
