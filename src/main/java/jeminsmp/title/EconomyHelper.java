package jeminsmp.title;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class EconomyHelper {

    private final JeminSMPPlugin plugin;
    private final File file;
    private FileConfiguration config;

    public EconomyHelper(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "balance.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) {
                plugin.getLogger().severe("balance.yml 생성 실패: " + e.getMessage());
            }
        }
        reload();
    }

    public long getBalance(UUID uuid) { return config.getLong(uuid.toString(), 0L); }

    public boolean withdraw(UUID uuid, long amount) {
        long current = getBalance(uuid);
        if (current < amount) return false;
        config.set(uuid.toString(), current - amount);
        save();
        return true;
    }

    public void deposit(UUID uuid, long amount) {
        config.set(uuid.toString(), getBalance(uuid) + amount);
        save();
    }

    public void set(UUID uuid, long amount) {
        config.set(uuid.toString(), Math.max(0, amount));
        save();
    }

    public void reload() { config = YamlConfiguration.loadConfiguration(file); }

    private void save() {
        try { config.save(file); } catch (IOException e) {
            plugin.getLogger().severe("balance.yml 저장 실패: " + e.getMessage());
        }
    }
}
