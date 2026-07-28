package jeminsmp.job;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 채팅 명령어 없이 키 입력으로 스킬 사용:
 *  - F(오프핸드 교체): 평소엔 힐, 웅크린 채로 누르면 딜
 *  - Shift 빠르게 두 번(더블탭): 포스
 */
public class JobHotkeyListener implements Listener {

    private static final long DOUBLE_TAP_WINDOW_MS = 400;

    private final SkillCommand skillCommand;
    private final Map<UUID, Long> lastSneakStart = new HashMap<>();

    public JobHotkeyListener(SkillCommand skillCommand) {
        this.skillCommand = skillCommand;
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        String skill = player.isSneaking() ? "deal" : "heal";
        skillCommand.handleUse(player, skill);
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return; // 웅크리기 시작하는 순간만 체크
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastSneakStart.put(uuid, now);
        if (last != null && now - last <= DOUBLE_TAP_WINDOW_MS) {
            lastSneakStart.remove(uuid);
            skillCommand.handleUse(player, "force");
        }
    }
}
