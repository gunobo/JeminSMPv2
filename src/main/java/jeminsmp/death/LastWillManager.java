package jeminsmp.death;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class LastWillManager {

    private final JeminSMPPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public LastWillManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "last_will.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void setWill(UUID uuid, String message) {
        config.set(uuid.toString(), message);
        save();
    }

    public void clearWill(UUID uuid) {
        config.set(uuid.toString(), null);
        save();
    }

    public String getWill(UUID uuid) {
        return config.getString(uuid.toString(), null);
    }

    public void deliverWill(Player player) {
        String will = getWill(player.getUniqueId());
        if (will == null) return;

        String name = player.getName();
        String msg = "§8[§c유언§8] §7" + name + "§f: \"" + will + "\"";

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
            p.sendTitle(
                "§c☠ " + name,
                "§f\"" + will + "\"",
                10, 80, 20
            );
        }
    }

    private void save() {
        try { config.save(file); } catch (IOException e) {
            plugin.getLogger().warning("last_will.yml 저장 실패: " + e.getMessage());
        }
    }
}
