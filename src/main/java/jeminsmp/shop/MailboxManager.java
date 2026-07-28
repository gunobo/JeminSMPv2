package jeminsmp.shop;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    // ── 아이템 우편함 (다이아몬드와 별도 저장, 인벤토리 꽉 찼을 때 아이템 지급용) ──

    public void addItem(UUID uuid, ItemStack item) {
        List<ItemStack> items = getItems(uuid);
        items.add(item);
        config.set("items." + uuid, items);
        save();
    }

    public List<ItemStack> getItems(UUID uuid) {
        List<?> raw = config.getList("items." + uuid);
        List<ItemStack> result = new ArrayList<>();
        if (raw != null) {
            for (Object o : raw) if (o instanceof ItemStack item) result.add(item);
        }
        return result;
    }

    public List<ItemStack> claimAndClearItems(UUID uuid) {
        List<ItemStack> items = getItems(uuid);
        config.set("items." + uuid, null);
        save();
        return items;
    }

    private void save() {
        try { config.save(file); } catch (IOException e) {
            plugin.getLogger().severe("mailbox.yml 저장 실패: " + e.getMessage());
        }
    }
}
