package jeminsmp.tpa;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TpaManager {

    private static final int EXPIRE_SECONDS = 30;
    private static final int WARMUP_SECONDS = 3;

    private final JeminSMPPlugin plugin;
    private final Map<UUID, TpaRequest> pending = new HashMap<>();

    public TpaManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendRequest(Player requester, Player target) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            requester.sendMessage("§c자기 자신에게는 요청할 수 없습니다.");
            return;
        }

        TpaRequest existing = pending.get(target.getUniqueId());
        if (existing != null) {
            existing.expireTask().cancel();
            Player prevRequester = Bukkit.getPlayer(existing.requesterUUID());
            if (prevRequester != null) prevRequester.sendMessage("§7이전 요청이 새 요청으로 대체되었습니다.");
        }

        BukkitTask expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending.remove(target.getUniqueId());
            if (requester.isOnline()) requester.sendMessage("§c" + target.getName() + " 님이 응답하지 않아 요청이 만료되었습니다.");
            if (target.isOnline()) target.sendMessage("§7" + requester.getName() + " 님의 텔레포트 요청이 만료되었습니다.");
        }, EXPIRE_SECONDS * 20L);

        pending.put(target.getUniqueId(), new TpaRequest(requester.getUniqueId(), expireTask));
        requester.sendMessage("§a" + target.getName() + " 님에게 텔레포트 요청을 보냈습니다. §7(" + EXPIRE_SECONDS + "초 후 만료)");
        target.sendMessage("§e" + requester.getName() + " §f님이 텔레포트를 요청합니다.");
        target.sendMessage("§a/tpaccept §f수락  §c/tpdeny §f거절");
    }

    public void acceptRequest(Player target) {
        TpaRequest request = pending.remove(target.getUniqueId());
        if (request == null) { target.sendMessage("§c대기 중인 텔레포트 요청이 없습니다."); return; }
        request.expireTask().cancel();

        Player requester = Bukkit.getPlayer(request.requesterUUID());
        if (requester == null || !requester.isOnline()) { target.sendMessage("§c요청자가 서버를 떠났습니다."); return; }

        target.sendMessage("§a" + requester.getName() + " 님의 요청을 수락했습니다.");
        requester.sendMessage("§e3초 뒤 " + target.getName() + " 님에게 이동합니다. 움직이지 마세요!");

        int startX = requester.getLocation().getBlockX();
        int startY = requester.getLocation().getBlockY();
        int startZ = requester.getLocation().getBlockZ();

        new BukkitRunnable() {
            int countdown = WARMUP_SECONDS;
            @Override
            public void run() {
                if (!requester.isOnline() || !target.isOnline()) {
                    if (requester.isOnline()) requester.sendMessage("§c상대방이 서버를 떠나 텔레포트가 취소되었습니다.");
                    cancel(); return;
                }
                Location current = requester.getLocation();
                if (current.getBlockX() != startX || current.getBlockY() != startY || current.getBlockZ() != startZ) {
                    requester.sendMessage("§c이동하여 텔레포트가 취소되었습니다.");
                    cancel(); return;
                }
                countdown--;
                if (countdown <= 0) {
                    requester.teleport(target.getLocation());
                    requester.sendMessage("§a" + target.getName() + " 님에게 이동했습니다!");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void denyRequest(Player target) {
        TpaRequest request = pending.remove(target.getUniqueId());
        if (request == null) { target.sendMessage("§c대기 중인 텔레포트 요청이 없습니다."); return; }
        request.expireTask().cancel();
        target.sendMessage("§c텔레포트 요청을 거절했습니다.");
        Player requester = Bukkit.getPlayer(request.requesterUUID());
        if (requester != null && requester.isOnline())
            requester.sendMessage("§c" + target.getName() + " 님이 텔레포트 요청을 거절했습니다.");
    }

    public void cleanup() {
        pending.values().forEach(r -> r.expireTask().cancel());
        pending.clear();
    }

    record TpaRequest(UUID requesterUUID, BukkitTask expireTask) {}
}
