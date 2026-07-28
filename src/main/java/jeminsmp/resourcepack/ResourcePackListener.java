package jeminsmp.resourcepack;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** config.yml의 resourcepack.url이 설정돼 있으면 접속하는 플레이어에게 적용 */
public class ResourcePackListener implements Listener {

    private final JeminSMPPlugin plugin;

    public ResourcePackListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String url = plugin.getConfig().getString("resourcepack.url", "");
        if (url == null || url.isBlank()) return;
        event.getPlayer().setResourcePack(url);
    }
}
