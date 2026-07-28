package jeminsmp.combat;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatManager implements Listener {

    private final JeminSMPPlugin plugin;
    private final Map<UUID, BukkitTask> combatTasks = new HashMap<>();
    private final Map<UUID, Boolean> inCombat = new HashMap<>();

    public CombatManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamage() <= 0) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        // 피해자가 플레이어인 경우에만 PvP 전투 판정
        if (!(event.getEntity() instanceof Player victim)) return;

        // 공격자도 플레이어여야 전투 태그 (몹 → 플레이어는 전투 아님)
        if (attacker == null) return;

        tagPlayer(attacker);
        tagPlayer(victim);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isInCombat(player)) {
            inCombat.remove(player.getUniqueId());
            BukkitTask task = combatTasks.remove(player.getUniqueId());
            if (task != null) task.cancel();
            // 전투 중 로그아웃 → 사망
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // 이미 오프라인이므로 EntityDamageEvent로 처리하지 않고 바로 처리
                // 오프라인 플레이어는 이미 disconnected, 기록만 남김
                plugin.getLogger().info("[전투로그아웃] " + player.getName() + " 님이 전투 중 로그아웃 → 사망 처리");
            });
            event.quitMessage(Component.text("☠ " + player.getName() + " 님이 전투 중 도망쳤습니다!", NamedTextColor.RED));
            // 플레이어가 이미 서버를 떠나는 중이므로 직접 kill은 불가 → 다음 접속 시 처리 위해 스탯만 기록
            // 실제 사망 처리: 오프라인이라 inventory clear + death stat 증가로 대체
            player.setHealth(0); // Paper에서는 이벤트 처리 중 가능
        }
    }

    public void tagPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        int seconds = plugin.getConfig().getInt("combat-tag-seconds", 10);

        BukkitTask existing = combatTasks.remove(uuid);
        if (existing != null) existing.cancel();

        inCombat.put(uuid, true);

        BukkitTask task = new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    inCombat.remove(uuid);
                    combatTasks.remove(uuid);
                    cancel();
                    return;
                }
                if (remaining <= 0) {
                    inCombat.remove(uuid);
                    combatTasks.remove(uuid);
                    player.sendActionBar(Component.text("✔ 전투 종료").color(NamedTextColor.GREEN));
                    cancel();
                } else {
                    player.sendActionBar(Component.text("⚔ 전투 중! (" + remaining + "초)").color(NamedTextColor.RED));
                    remaining--;
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        combatTasks.put(uuid, task);
    }

    public boolean isInCombat(Player player) {
        return inCombat.getOrDefault(player.getUniqueId(), false);
    }
    public boolean isInCombat(UUID uuid) {
        return inCombat.getOrDefault(uuid, false);
    }

    public void cleanup() {
        combatTasks.values().forEach(BukkitTask::cancel);
        combatTasks.clear();
        inCombat.clear();
    }
}
