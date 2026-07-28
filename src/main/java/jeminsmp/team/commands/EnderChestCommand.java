package jeminsmp.team.commands;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.team.TeamLevelManager;
import jeminsmp.team.TeamManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EnderChestCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;

    public EnderChestCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage("§c팀에 소속되어 있지 않습니다.");
            return true;
        }
        int level = plugin.getTeamLevelManager().getLevel(team.name);
        if (level < 3) {
            player.sendMessage("§c이 기능은 팀 레벨 3 이상에서 사용할 수 있습니다.");
            player.sendMessage("§7현재 팀 레벨: " + TeamLevelManager.getLevelColor(level) + "Lv" + level
                    + " §7/ §e필요 레벨: §bLv3 §7(엔더상자 명령어)");
            return true;
        }
        player.openInventory(player.getEnderChest());
        return true;
    }
}
