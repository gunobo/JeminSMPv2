package jeminsmp.schedule;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;

import java.util.UUID;

public class ScheduleListener implements Listener {

    private final JeminSMPPlugin plugin;

    public ScheduleListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    // ── 접속 시 대기열 처리 (인벤토리 로드 후 1틱 대기) ──
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            ScheduleManager sm = plugin.getScheduleManager();
            sm.getWaitingManager().onJoin(event.getPlayer(), sm.isOpen());
        }, 1L);
    }

    // ── 퇴장 시 메모리 정리 ──
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getScheduleManager().getWaitingManager().onQuit(event.getPlayer().getUniqueId());
    }

    // ── 대기 월드에서 다른 월드로 텔레포트 차단 ──
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // 플러그인이 직접 이동시키는 건 허용
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;

        UUID uuid = event.getPlayer().getUniqueId();
        WaitingManager wm = plugin.getScheduleManager().getWaitingManager();
        if (!wm.isQueued(uuid)) return;

        World waitingWorld = wm.getWaitingWorld();
        if (waitingWorld == null) return;

        // 대기 월드 밖으로 나가는 텔레포트 차단
        if (event.getPlayer().getWorld().equals(waitingWorld)
                && !waitingWorld.equals(event.getTo().getWorld())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    Component.text("⏳ 대기 중에는 이동할 수 없습니다.", NamedTextColor.RED));
        }
    }

    // ── 대기 월드에서 피해 차단 ──
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        World ww = plugin.getScheduleManager().getWaitingManager().getWaitingWorld();
        if (ww != null && p.getWorld().equals(ww)) {
            event.setCancelled(true);
        }
    }

    // ── 대기 중 허기 변화 차단 ──
    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (plugin.getScheduleManager().getWaitingManager().isQueued(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ── 대기 중 아이템 드롭 차단 ──
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getScheduleManager().getWaitingManager().isQueued(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ── 대기 월드에서 채팅 외 커맨드 차단 ──
    @EventHandler(priority = EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        WaitingManager wm = plugin.getScheduleManager().getWaitingManager();
        if (!wm.isQueued(uuid)) return;
        World ww = wm.getWaitingWorld();
        if (ww == null || !event.getPlayer().getWorld().equals(ww)) return;

        // 허용 커맨드 (채팅 관련, 상태 확인 등)
        String cmd = event.getMessage().toLowerCase();
        if (cmd.startsWith("/list") || cmd.startsWith("/ping")
                || cmd.startsWith("/w ") || cmd.startsWith("/msg ")
                || cmd.startsWith("/tell ") || cmd.startsWith("/r ")) {
            return; // 허용
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(
                Component.text("⏳ 대기 중에는 명령어를 사용할 수 없습니다.", NamedTextColor.RED));
    }
}
