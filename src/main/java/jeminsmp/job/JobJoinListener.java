package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** 접속했는데 아직 직업을 선택하지 않았으면(최초 접속이든 그 이후든) 매번 선택 안내를 보냄 */
public class JobJoinListener implements Listener {

    private final JeminSMPPlugin plugin;

    public JobJoinListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        plugin.getJobManager().refreshTier3Perk(player);
        if (plugin.getJobManager().getJob(player.getUniqueId()) != null) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (plugin.getJobManager().getJob(player.getUniqueId()) != null) return;
            JobItems.sendJobSelectPrompt(player, "§d👋 아직 직업을 선택하지 않았습니다! 원하는 직업을 선택하세요: ");
        }, 40L);
    }
}
