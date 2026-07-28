package jeminsmp.title;

import io.papermc.paper.event.player.AsyncChatEvent;
import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class TitleListener implements Listener {

    private final JeminSMPPlugin plugin;

    public TitleListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncChatEvent event) {
        String display = plugin.getTitleManager().getEquippedDisplay(event.getPlayer().getUniqueId());
        if (display == null) return;
        Component prefix = LegacyComponentSerializer.legacyAmpersand().deserialize(display + " ");
        event.renderer((source, sourceDisplayName, message, viewer) ->
                prefix.append(sourceDisplayName).append(Component.text(": ")).append(message));
    }
}
