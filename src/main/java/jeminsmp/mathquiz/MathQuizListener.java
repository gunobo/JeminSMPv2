package jeminsmp.mathquiz;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class MathQuizListener implements Listener {

    private final JeminSMPPlugin plugin;

    public MathQuizListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        MathQuizManager mgr = plugin.getMathQuizManager();
        if (mgr == null || !mgr.isActive()) return;

        String msg = event.getMessage().trim();
        // 숫자만 입력된 경우에만 정답 체크 (일반 채팅과 구분)
        if (!msg.matches("-?\\d+")) return;

        // 메인 스레드에서 아이템 지급
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            boolean correct = mgr.checkAnswer(event.getPlayer(), msg);
            if (correct) {
                // 정답 메시지는 채팅에 표시하지 않음 (broadcast로 대체)
                event.setCancelled(true);
            }
        });
    }
}
