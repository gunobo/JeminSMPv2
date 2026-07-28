package jeminsmp.home;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HomeManager {

    private final JeminSMPPlugin plugin;
    private FileConfiguration homesConfig;
    private File homesFile;

    public HomeManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        homesFile = new File(plugin.getDataFolder(), "homes.yml");
        if (!homesFile.exists()) {
            try { homesFile.createNewFile(); } catch (IOException e) {
                plugin.getLogger().severe("homes.yml 생성 실패: " + e.getMessage());
            }
        }
        homesConfig = YamlConfiguration.loadConfiguration(homesFile);
    }

    private void save() {
        try { homesConfig.save(homesFile); } catch (IOException e) {
            plugin.getLogger().severe("homes.yml 저장 실패: " + e.getMessage());
        }
    }

    public int getHomeCount(UUID uuid) {
        ConfigurationSection section = homesConfig.getConfigurationSection(uuid.toString());
        return section != null ? section.getKeys(false).size() : 0;
    }

    public boolean homeExists(UUID uuid, String name) {
        return homesConfig.contains(uuid + "." + name);
    }

    public Location getHome(UUID uuid, String name) {
        String path = uuid + "." + name;
        if (!homesConfig.contains(path)) return null;
        World world = Bukkit.getWorld(homesConfig.getString(path + ".world", ""));
        if (world == null) return null;
        return new Location(world,
                homesConfig.getDouble(path + ".x"),
                homesConfig.getDouble(path + ".y"),
                homesConfig.getDouble(path + ".z"),
                (float) homesConfig.getDouble(path + ".yaw"),
                (float) homesConfig.getDouble(path + ".pitch"));
    }

    public Map<String, Location> getAllHomes(UUID uuid) {
        ConfigurationSection section = homesConfig.getConfigurationSection(uuid.toString());
        if (section == null) return Collections.emptyMap();
        Map<String, Location> homes = new HashMap<>();
        for (String homeName : section.getKeys(false)) {
            Location loc = getHome(uuid, homeName);
            if (loc != null) homes.put(homeName, loc);
        }
        return homes;
    }

    public void setHome(UUID uuid, String name, Location location) {
        String path = uuid + "." + name;
        homesConfig.set(path + ".world", location.getWorld().getName());
        homesConfig.set(path + ".x", location.getX());
        homesConfig.set(path + ".y", location.getY());
        homesConfig.set(path + ".z", location.getZ());
        homesConfig.set(path + ".yaw", (double) location.getYaw());
        homesConfig.set(path + ".pitch", (double) location.getPitch());
        save();
    }

    public boolean deleteHome(UUID uuid, String name) {
        if (!homeExists(uuid, name)) return false;
        homesConfig.set(uuid + "." + name, null);
        save();
        return true;
    }
}
