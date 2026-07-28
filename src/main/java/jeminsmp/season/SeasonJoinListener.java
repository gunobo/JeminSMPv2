package jeminsmp.season;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class SeasonJoinListener implements Listener {

    private final JeminSMPPlugin plugin;

    public SeasonJoinListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        SeasonRewardManager rm = plugin.getSeasonRewardManager();
        if (rm == null) return;
        // 20틱 지연 — 다른 join 로직(인벤 복구 등)이 먼저 처리된 후 지급
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                rm.givePendingIfAny(event.getPlayer());
            }
        }, 20L);
    }
}
