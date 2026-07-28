package jeminsmp.waypoint;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class WaypointManager {

    public record Waypoint(String name, String world, int x, int y, int z, boolean shared) {
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    private final JeminSMPPlugin plugin;
    private final File file;
    // uuid → (name → waypoint)
    private final Map<UUID, Map<String, Waypoint>> data = new HashMap<>();

    public WaypointManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "waypoints.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String uuidStr : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, Waypoint> map = new LinkedHashMap<>();
                var section = cfg.getConfigurationSection(uuidStr);
                if (section == null) continue;
                for (String name : section.getKeys(false)) {
                    String world = section.getString(name + ".world", "world");
                    int x = section.getInt(name + ".x");
                    int y = section.getInt(name + ".y");
                    int z = section.getInt(name + ".z");
                    boolean shared = section.getBoolean(name + ".shared", false);
                    map.put(name.toLowerCase(), new Waypoint(name, world, x, y, z, shared));
                }
                data.put(uuid, map);
            } catch (Exception ignored) {}
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        data.forEach((uuid, map) -> map.forEach((name, wp) -> {
            String path = uuid + "." + name;
            cfg.set(path + ".world", wp.world());
            cfg.set(path + ".x", wp.x());
            cfg.set(path + ".y", wp.y());
            cfg.set(path + ".z", wp.z());
            cfg.set(path + ".shared", wp.shared());
        }));
        try { cfg.save(file); } catch (IOException e) {
            plugin.getLogger().warning("waypoints.yml 저장 실패: " + e.getMessage());
        }
    }

    private Map<String, Waypoint> getMap(UUID uuid) {
        return data.computeIfAbsent(uuid, u -> new LinkedHashMap<>());
    }

    public boolean add(UUID uuid, String name, Location loc) {
        var map = getMap(uuid);
        if (map.size() >= 10) return false; // 최대 10개
        map.put(name.toLowerCase(), new Waypoint(name, loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), false));
        save();
        return true;
    }

    public boolean delete(UUID uuid, String name) {
        boolean removed = getMap(uuid).remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public boolean share(UUID uuid, String name) {
        var wp = getMap(uuid).get(name.toLowerCase());
        if (wp == null) return false;
        getMap(uuid).put(name.toLowerCase(), new Waypoint(wp.name(), wp.world(), wp.x(), wp.y(), wp.z(), !wp.shared()));
        save();
        return true;
    }

    public Waypoint get(UUID uuid, String name) {
        return getMap(uuid).get(name.toLowerCase());
    }

    // 내 웨이포인트 + 같은 팀원의 공유 웨이포인트
    public List<Map.Entry<String, Waypoint>> getVisible(UUID uuid, Set<UUID> teammates) {
        List<Map.Entry<String, Waypoint>> list = new ArrayList<>();
        // 내 것
        getMap(uuid).forEach((k, v) -> list.add(Map.entry("[나] " + v.name(), v)));
        // 팀원 공유
        for (UUID mate : teammates) {
            if (mate.equals(uuid)) continue;
            getMap(mate).values().stream()
                    .filter(Waypoint::shared)
                    .forEach(v -> {
                        String mName = Bukkit.getOfflinePlayer(mate).getName();
                        list.add(Map.entry("[" + (mName != null ? mName : "?") + "] " + v.name(), v));
                    });
        }
        return list;
    }

    public Collection<String> getNames(UUID uuid) {
        return getMap(uuid).keySet();
    }
}
