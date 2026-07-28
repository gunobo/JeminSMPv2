package jeminsmp.sleep;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SleepListener implements Listener {

    private final JeminSMPPlugin plugin;

    public SleepListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        // 1틱 뒤 실행 (실제로 누운 뒤)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isSleeping())
                plugin.getSleepManager().startVote(event.getPlayer(), event.getPlayer().getWorld());
        }, 1L);
    }

    @EventHandler
    public void onBedLeave(PlayerBedLeaveEvent event) {
        plugin.getSleepManager().onSleeperWakeup(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getSleepManager().removeVoter(event.getPlayer().getUniqueId());
    }
}
