package jeminsmp.team;

import io.papermc.paper.event.player.AsyncChatEvent;
import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class TeamListener implements Listener {

    private final JeminSMPPlugin plugin;

    public TeamListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getTeamManager().onPlayerJoin(player.getUniqueId(), player.getName());
        plugin.getTeamManager().recordLogin(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getTeamManager().recordQuit(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        plugin.getTeamLevelManager().onChestClose(player, inv);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!plugin.getTeamLevelManager().hasPendingWarmup(uuid)) return;
        // Cancel if moved more than 0.1 blocks (ignore head rotation)
        if (!player.hasMetadata("team_tp_start_x")) return;
        double sx = player.getMetadata("team_tp_start_x").get(0).asDouble();
        double sy = player.getMetadata("team_tp_start_y").get(0).asDouble();
        double sz = player.getMetadata("team_tp_start_z").get(0).asDouble();
        double dx = player.getLocation().getX() - sx;
        double dy = player.getLocation().getY() - sy;
        double dz = player.getLocation().getZ() - sz;
        if (dx * dx + dy * dy + dz * dz > 0.01) {
            plugin.getTeamLevelManager().cancelWarmup(uuid);
            player.sendMessage("§c움직여서 텔레포트가 취소되었습니다.");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (plugin.getTeamLevelManager().hasPendingWarmup(uuid)) {
            plugin.getTeamLevelManager().cancelWarmup(uuid);
            player.sendMessage("§c피해를 받아 텔레포트가 취소되었습니다.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!raw.startsWith("@")) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("팀에 소속되어 있지 않습니다.", NamedTextColor.RED));
            return;
        }
        String message = raw.substring(1).trim();
        if (message.isEmpty()) {
            player.sendMessage(Component.text("메시지를 입력하세요.", NamedTextColor.RED));
            return;
        }
        sendTeamChat(player, team, message);
    }

    public static void sendTeamChat(Player sender, TeamManager.TeamData team, String message) {
        Component chatMsg = Component.text("[팀:" + team.name + "] ", NamedTextColor.GREEN)
                .append(Component.text(sender.getName(), NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.WHITE));
        for (UUID uuid : team.members) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.sendMessage(chatMsg);
        }
    }
}
