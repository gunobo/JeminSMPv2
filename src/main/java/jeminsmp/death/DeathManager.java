package jeminsmp.death;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeathManager {

    private final JeminSMPPlugin plugin;
    private final File file;
    private final Map<UUID, Location> lastDeath = new HashMap<>();

    public DeathManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "deathpoints.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String worldName = cfg.getString(key + ".world");
                double x = cfg.getDouble(key + ".x");
                double y = cfg.getDouble(key + ".y");
                double z = cfg.getDouble(key + ".z");
                World world = Bukkit.getWorld(worldName);
                if (world != null) lastDeath.put(uuid, new Location(world, x, y, z));
            } catch (Exception ignored) {}
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        lastDeath.forEach((uuid, loc) -> {
            if (loc.getWorld() == null) return;
            cfg.set(uuid + ".world", loc.getWorld().getName());
            cfg.set(uuid + ".x", loc.getBlockX());
            cfg.set(uuid + ".y", loc.getBlockY());
            cfg.set(uuid + ".z", loc.getBlockZ());
        });
        try { cfg.save(file); } catch (IOException e) {
            plugin.getLogger().warning("deathpoints.yml 저장 실패: " + e.getMessage());
        }
    }

    public void recordDeath(UUID uuid, Location location) {
        lastDeath.put(uuid, location.clone());
        save();
    }

    public Location getLastDeath(UUID uuid) {
        return lastDeath.get(uuid);
    }
}
