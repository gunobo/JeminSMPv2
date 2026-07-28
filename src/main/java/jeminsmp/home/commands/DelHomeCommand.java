package jeminsmp.home.commands;

import jeminsmp.home.HomeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DelHomeCommand implements CommandExecutor {

    private final HomeManager homeManager;

    public DelHomeCommand(HomeManager homeManager) {
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        String homeName = args.length > 0 ? args[0] : "home";
        if (homeManager.deleteHome(player.getUniqueId(), homeName)) {
            player.sendMessage("§a홈 §e'" + homeName + "'§a을(를) 삭제했습니다.");
        } else {
            player.sendMessage("§c홈 §e'" + homeName + "'§c을(를) 찾을 수 없습니다.");
        }
        return true;
    }
}
