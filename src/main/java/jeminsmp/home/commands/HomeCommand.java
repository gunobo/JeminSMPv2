package jeminsmp.home.commands;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.combat.CombatManager;
import jeminsmp.home.HomeManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class HomeCommand implements CommandExecutor {

    private final HomeManager homeManager;
    private final CombatManager combatManager;
    private final JeminSMPPlugin plugin;

    public HomeCommand(HomeManager homeManager, CombatManager combatManager, JeminSMPPlugin plugin) {
        this.homeManager = homeManager;
        this.combatManager = combatManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        if (combatManager.isInCombat(player)) {
            player.sendMessage("§c전투 중에는 홈으로 이동할 수 없습니다!");
            return true;
        }
        String homeName = args.length > 0 ? args[0] : "home";
        Location destination = homeManager.getHome(player.getUniqueId(), homeName);
        if (destination == null) {
            player.sendMessage("§c홈 §e'" + homeName + "'§c을(를) 찾을 수 없습니다. /sethome 으로 먼저 저장하세요.");
            return true;
        }
        player.sendMessage("§e3초 뒤 이동합니다. 움직이지 마세요!");
        int startX = player.getLocation().getBlockX();
        int startY = player.getLocation().getBlockY();
        int startZ = player.getLocation().getBlockZ();

        new BukkitRunnable() {
            int countdown = 3;
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (combatManager.isInCombat(player)) {
                    player.sendMessage("§c전투 상태가 되어 이동이 취소되었습니다.");
                    cancel(); return;
                }
                Location cur = player.getLocation();
                if (cur.getBlockX() != startX || cur.getBlockY() != startY || cur.getBlockZ() != startZ) {
                    player.sendMessage("§c이동하여 텔레포트가 취소되었습니다.");
                    cancel(); return;
                }
                countdown--;
                if (countdown <= 0) {
                    player.teleport(destination);
                    player.sendMessage("§a홈 §e'" + homeName + "'§a으로 이동했습니다!");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
        return true;
    }
}
