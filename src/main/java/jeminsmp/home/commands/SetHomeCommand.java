package jeminsmp.home.commands;

import jeminsmp.home.HomeManager;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetHomeCommand implements CommandExecutor {

    private static final long PLAYTIME_THRESHOLD_SECONDS = 86_400L;
    private final HomeManager homeManager;

    public SetHomeCommand(HomeManager homeManager) {
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        String homeName = args.length > 0 ? args[0] : "home";
        boolean homeExists = homeManager.homeExists(player.getUniqueId(), homeName);
        int count = homeManager.getHomeCount(player.getUniqueId());

        if (!homeExists) {
            long secondsPlayed = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
            if (count >= 1 && secondsPlayed < PLAYTIME_THRESHOLD_SECONDS) {
                player.sendMessage("§624시간 이상 출석 시 홈을 2개까지 사용할 수 있습니다.");
                return true;
            }
            if (count >= 2) {
                player.sendMessage("§c더 이상 홈을 생성할 수 없습니다.");
                return true;
            }
        }

        homeManager.setHome(player.getUniqueId(), homeName, player.getLocation());
        player.sendMessage("§a홈 §e'" + homeName + "'§a이(가) 저장되었습니다!");
        return true;
    }
}
