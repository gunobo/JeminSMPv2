package jeminsmp.death;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {

    private final JeminSMPPlugin plugin;

    public DeathListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        var player = event.getEntity();
        var loc = player.getLocation();
        plugin.getDeathManager().recordDeath(player.getUniqueId(), loc);

        // 죽은 플레이어 본인에게만 좌표 전송
        plugin.getServer().getScheduler().runTask(plugin, () ->
            player.sendMessage("§c☠ §f사망 위치: §e"
                    + loc.getWorld().getName()
                    + " §7(§fX: §e" + loc.getBlockX()
                    + " §fY: §e" + loc.getBlockY()
                    + " §fZ: §e" + loc.getBlockZ() + "§7)"
                    + " §8(/deathpoint 로 다시 확인)")
        );

        // 유언 전달
        plugin.getLastWillManager().deliverWill(player);
    }
}
