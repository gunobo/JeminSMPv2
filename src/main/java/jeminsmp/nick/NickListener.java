package jeminsmp.nick;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class NickListener implements Listener {

    private final JeminSMPPlugin plugin;

    public NickListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 접속 시:
     * - 닉네임 복원 (displayName, scoreboard team, playerListName)
     * - 플레이타임 자동 칭호 체크 (1틱 뒤 실행 — 통계 로드 완료 후)
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getNickManager().applyNick(event.getPlayer());
        // 통계가 로드된 다음 틱에 체크
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline())
                plugin.getAutoTitleManager().checkPlaytime(event.getPlayer());
        }, 20L);
    }

    /** 퇴장 시 scoreboard 팀 정리 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getNickManager().cleanupTeam(event.getPlayer());
    }
}
