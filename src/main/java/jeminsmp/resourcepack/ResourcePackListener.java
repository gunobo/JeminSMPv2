package jeminsmp.resourcepack;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

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
        plugin.getLogger().info("[리소스팩] " + event.getPlayer().getName() + "님에게 적용 시도: " + url);
        event.getPlayer().setResourcePack(url);
    }

    // 클라이언트가 실제로 어떻게 됐는지(성공/거부/다운로드 실패 등) 콘솔에 남김
    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        plugin.getLogger().info("[리소스팩] " + event.getPlayer().getName() + " 결과: " + event.getStatus());
    }
}
