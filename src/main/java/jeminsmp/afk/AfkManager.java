package jeminsmp.afk;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AfkManager {

    private final JeminSMPPlugin plugin;
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Map<UUID, Boolean> afkState = new HashMap<>();
    private final long afkMillis;

    public AfkManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        int minutes = plugin.getConfig().getInt("afk.timeout-minutes", 5);
        afkMillis = minutes * 60_000L;

        // 10초마다 AFK 체크
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkAll, 200L, 200L);
    }

    public void activity(UUID uuid) {
        boolean wasAfk = afkState.getOrDefault(uuid, false);
        lastActivity.put(uuid, System.currentTimeMillis());
        if (wasAfk) {
            afkState.put(uuid, false);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                        "§e" + p.getName() + " §f님이 AFK에서 돌아왔습니다."));
                plugin.getTabManager().update(p);
            }
        }
    }

    public void remove(UUID uuid) {
        lastActivity.remove(uuid);
        afkState.remove(uuid);
    }

    public boolean isAfk(UUID uuid) {
        return afkState.getOrDefault(uuid, false);
    }

    private void checkAll() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long last = lastActivity.getOrDefault(uuid, now);
            boolean currently = afkState.getOrDefault(uuid, false);
            boolean shouldBeAfk = (now - last) >= afkMillis;

            if (shouldBeAfk && !currently) {
                afkState.put(uuid, true);
                Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                        "§7" + player.getName() + " 님이 AFK 상태가 되었습니다."));
                plugin.getTabManager().update(player);
            }
        }
    }
}
