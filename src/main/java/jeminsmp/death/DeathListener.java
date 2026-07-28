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

        // 유언 전달
        plugin.getLastWillManager().deliverWill(player);
    }
}
