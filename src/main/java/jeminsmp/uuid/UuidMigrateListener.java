package jeminsmp.uuid;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class UuidMigrateListener implements Listener {

    private final JeminSMPPlugin plugin;

    public UuidMigrateListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Copy world files (playerdata, advancements, stats) before the server
     * loads the player's data for this session.  PlayerLoginEvent fires on
     * the main thread before the player object is fully set up, so the file
     * copy here ensures the server reads the correct (online-UUID) data file.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        UuidMigrationManager mgr = plugin.getUuidMigrationManager();
        if (!mgr.needsMigration(player)) return;

        UUID offlineUUID = UuidMigrationManager.computeOfflineUUID(player.getName());
        mgr.migrateWorldFilesPublic(offlineUUID, player.getUniqueId());
    }

    /**
     * After the player is fully online, migrate plugin YAML files and
     * in-memory data, then mark the player as migrated.
     * A 1-tick delay ensures all other join handlers have run first.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UuidMigrationManager mgr = plugin.getUuidMigrationManager();
        if (!mgr.needsMigration(player)) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> mgr.migrate(player));
    }
}
