package jeminsmp.shop;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class MailboxManager {

    private final JeminSMPPlugin plugin;
    private final File file;
    private FileConfiguration config;

    public MailboxManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "mailbox.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void addDiamonds(UUID uuid, int amount) {
        int current = config.getInt(uuid.toString(), 0);
        config.set(uuid.toString(), current + amount);
        save();
    }

    public int getDiamonds(UUID uuid) {
        return config.getInt(uuid.toString(), 0);
    }

    public int claimAndClear(UUID uuid) {
        int amount = getDiamonds(uuid);
        config.set(uuid.toString(), 0);
        save();
        return amount;
    }

    private void save() {
        try { config.save(file); } catch (IOException e) {
            plugin.getLogger().severe("mailbox.yml 저장 실패: " + e.getMessage());
        }
    }
}
