package jeminsmp.smp;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.smp.commands.StartSMPCommand;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Random;

public class SMPListener implements Listener {

    private final JeminSMPPlugin plugin;

    public SMPListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    // 새로 접속한 플레이어 → smp_world로 이동
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.isSmpActive()) return;

        String worldName = plugin.getConfig().getString("smp.world-name", "smp_world");
        World smpWorld = plugin.getServer().getWorld(worldName);
        if (smpWorld == null) return;

        var player = event.getPlayer();
        if (player.getWorld().equals(smpWorld)) return;

        // smp 관련 월드면 그냥 둠
        if (isSmpWorld(player.getWorld().getName(), worldName)) return;

        int radius = plugin.getConfig().getInt("smp.scatter-radius", 500);
        Random random = new Random();
        int x = random.nextInt(radius * 2 + 1) - radius;
        int z = random.nextInt(radius * 2 + 1) - radius;
        int y = smpWorld.getHighestBlockYAt(x, z) + 1;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.isSmpResetData()) StartSMPCommand.resetPlayer(player);
            player.teleport(new Location(smpWorld, x + 0.5, y, z + 0.5));
            player.sendMessage("§aSMP 월드로 이동합니다!");
        });
    }

    // 포탈 → smp 월드끼리 연결
    @EventHandler(priority = EventPriority.HIGH)
    public void onPortal(PlayerPortalEvent event) {
        String worldName = plugin.getConfig().getString("smp.world-name", "smp_world");
        String fromWorld = event.getPlayer().getWorld().getName();

        if (!isSmpWorld(fromWorld, worldName)) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        Location to = event.getTo();
        if (to == null) return;

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (fromWorld.equals(worldName)) {
                // 오버 → 네더
                World nether = plugin.getServer().getWorld(worldName + "_nether");
                if (nether != null) event.setTo(new Location(nether, to.getX(), to.getY(), to.getZ()));
            } else if (fromWorld.equals(worldName + "_nether")) {
                // 네더 → 오버
                World overworld = plugin.getServer().getWorld(worldName);
                if (overworld != null) event.setTo(new Location(overworld, to.getX(), to.getY(), to.getZ()));
            }
        } else if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (fromWorld.equals(worldName)) {
                // 오버 → 엔드
                World end = plugin.getServer().getWorld(worldName + "_the_end");
                if (end == null) {
                    end = new org.bukkit.WorldCreator(worldName + "_the_end")
                            .environment(World.Environment.THE_END)
                            .createWorld();
                }
                if (end != null) event.setTo(new Location(end, to.getX(), to.getY(), to.getZ()));
            } else if (fromWorld.equals(worldName + "_the_end")) {
                // 엔드 출구 → 오버
                World overworld = plugin.getServer().getWorld(worldName);
                if (overworld != null) event.setTo(overworld.getSpawnLocation());
            }
        }
    }

    private boolean isSmpWorld(String worldName, String smpBase) {
        return worldName.equals(smpBase)
                || worldName.equals(smpBase + "_nether")
                || worldName.equals(smpBase + "_the_end");
    }
}
