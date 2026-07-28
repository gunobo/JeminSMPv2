package jeminsmp.kills;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class KillsListener implements Listener {

    private final JeminSMPPlugin plugin;

    public KillsListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player dead   = event.getEntity();
        Player killer = dead.getKiller();

        int lostStreak = plugin.getKillsManager().registerDeath(dead.getUniqueId(), dead.getName());
        if (lostStreak >= 3) {
            dead.sendMessage(Component.text("💔 " + lostStreak + "연속킬이 끊겼습니다.", NamedTextColor.GRAY));
        }

        if (killer == null || killer.equals(dead)) return;

        int streak = plugin.getKillsManager().registerKill(killer.getUniqueId(), killer.getName());
        plugin.getKillsManager().save();

        killer.sendMessage(Component.text("⚔ " + dead.getName() + " 처치! (연속킬: " + streak + ")", NamedTextColor.YELLOW));

        // 자동 칭호 체크
        if (plugin.getAutoTitleManager() != null)
            plugin.getAutoTitleManager().checkKills(killer);
    }
}
