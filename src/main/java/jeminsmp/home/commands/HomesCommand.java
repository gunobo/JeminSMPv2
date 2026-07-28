package jeminsmp.home.commands;

import jeminsmp.home.HomeManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class HomesCommand implements CommandExecutor {

    private final HomeManager homeManager;

    public HomesCommand(HomeManager homeManager) {
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        Map<String, Location> homes = homeManager.getAllHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            player.sendMessage("§e저장된 홈이 없습니다. /sethome 으로 홈을 저장하세요.");
            return true;
        }
        player.sendMessage("§6▶ 내 홈 목록 (" + homes.size() + "개)");
        for (Map.Entry<String, Location> entry : homes.entrySet()) {
            Location loc = entry.getValue();
            player.sendMessage(String.format("§e  - %s §7| 월드: %s | X:%.1f Y:%.1f Z:%.1f",
                    entry.getKey(), loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ()));
        }
        return true;
    }
}
