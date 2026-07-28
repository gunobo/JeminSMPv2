package jeminsmp.letter;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class LetterListener implements Listener {

    private final JeminSMPPlugin plugin;

    public LetterListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
            plugin.getLetterManager().deliverPending(player), 1L);
    }
}
