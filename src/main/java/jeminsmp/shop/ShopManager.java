package jeminsmp.shop;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ShopManager {

    private final JeminSMPPlugin plugin;
    private final File file;
    private FileConfiguration config;

    private final Map<Integer, Listing> listings = new LinkedHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public ShopManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        load();
    }

    private void load() {
        config = YamlConfiguration.loadConfiguration(file);
        listings.clear();
        int maxId = 0;
        var section = config.getConfigurationSection("listings");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                String path = "listings." + key;
                UUID sellerUUID = UUID.fromString(Objects.requireNonNull(config.getString(path + ".sellerUUID")));
                String sellerName = config.getString(path + ".sellerName", "Unknown");
                ItemStack item = config.getItemStack(path + ".item");
                double price = config.getDouble(path + ".price");
                if (item == null) continue;
                listings.put(id, new Listing(id, sellerUUID, sellerName, item, price));
                if (id > maxId) maxId = id;
            } catch (Exception ignored) {}
        }
        nextId.set(maxId + 1);
    }

    public void save() {
        config.set("listings", null);
        for (Listing l : listings.values()) {
            String path = "listings." + l.id();
            config.set(path + ".sellerUUID", l.sellerUUID().toString());
            config.set(path + ".sellerName", l.sellerName());
            config.set(path + ".item", l.item());
            config.set(path + ".price", l.price());
        }
        try { config.save(file); } catch (IOException e) {
            plugin.getLogger().severe("shop.yml 저장 실패: " + e.getMessage());
        }
    }

    public int addListing(UUID sellerUUID, String sellerName, ItemStack item, long price) {
        int id = nextId.getAndIncrement();
        listings.put(id, new Listing(id, sellerUUID, sellerName, item, price));
        save();
        return id;
    }

    public void removeListing(int id) {
        listings.remove(id);
        save();
    }

    public Listing getListing(int id) { return listings.get(id); }

    public List<Listing> getAllListings() { return new ArrayList<>(listings.values()); }

    public List<Listing> getListingsBySeller(UUID uuid) {
        return listings.values().stream().filter(l -> l.sellerUUID().equals(uuid)).toList();
    }

    public record Listing(int id, UUID sellerUUID, String sellerName, ItemStack item, double price) {}
}
