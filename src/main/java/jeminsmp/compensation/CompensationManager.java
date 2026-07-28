package jeminsmp.compensation;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CompensationManager {

    private final JeminSMPPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    public record CompEvent(
            String id,
            String description,
            long expiresAt,      // epoch ms, -1 = 무기한
            List<ItemStack> items
    ) {}

    public CompensationManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "compensations.yml");
        load();
    }

    private void load() {
        cfg = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
    }

    private void save() {
        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().warning("compensations.yml 저장 실패: " + e.getMessage()); }
    }

    // ── 보상 등록 ──
    public boolean create(String id, long expiresAt, String description,
                          List<ItemStack> items) {
        if (cfg.contains("events." + id)) return false;

        cfg.set("events." + id + ".description", description);
        cfg.set("events." + id + ".expires",     expiresAt);
        cfg.set("events." + id + ".itemCount",   items.size());
        for (int i = 0; i < items.size(); i++) {
            cfg.set("events." + id + ".item_" + i, items.get(i));
        }
        save();
        return true;
    }

    // ── 보상 삭제 ──
    public boolean delete(String id) {
        if (!cfg.contains("events." + id)) return false;
        cfg.set("events." + id,     null);
        cfg.set("claimed." + id,    null);
        save();
        return true;
    }

    // ── 보상 목록 ──
    public List<CompEvent> getActiveEvents() {
        List<CompEvent> list = new ArrayList<>();
        ConfigurationSection sec = cfg.getConfigurationSection("events");
        if (sec == null) return list;
        long now = System.currentTimeMillis();
        for (String id : sec.getKeys(false)) {
            long exp = cfg.getLong("events." + id + ".expires", -1);
            if (exp != -1 && now > exp) continue;  // 만료
            list.add(load(id));
        }
        return list;
    }

    public List<CompEvent> getAllEvents() {
        List<CompEvent> list = new ArrayList<>();
        ConfigurationSection sec = cfg.getConfigurationSection("events");
        if (sec == null) return list;
        for (String id : sec.getKeys(false)) list.add(load(id));
        return list;
    }

    private CompEvent load(String id) {
        String desc    = cfg.getString("events." + id + ".description", "");
        long   exp     = cfg.getLong("events." + id + ".expires", -1);
        List<ItemStack> items = new ArrayList<>();
        int itemCount = cfg.getInt("events." + id + ".itemCount", 0);
        for (int i = 0; i < itemCount; i++) {
            ItemStack is = cfg.getItemStack("events." + id + ".item_" + i);
            if (is != null && is.getType() != Material.AIR) items.add(is);
        }
        return new CompEvent(id, desc, exp, items);
    }

    public CompEvent getEvent(String id) {
        if (!cfg.contains("events." + id)) return null;
        return load(id);
    }

    // ── 수령 여부 ──
    public boolean hasClaimed(UUID uuid, String eventId) {
        List<String> list = cfg.getStringList("claimed." + eventId);
        return list.contains(uuid.toString());
    }

    // ── 수령 처리 ──
    public boolean claim(UUID uuid, String eventId) {
        CompEvent ev = getEvent(eventId);
        if (ev == null) return false;
        long now = System.currentTimeMillis();
        if (ev.expiresAt() != -1 && now > ev.expiresAt()) return false;
        if (hasClaimed(uuid, eventId)) return false;

        List<String> list = new ArrayList<>(cfg.getStringList("claimed." + eventId));
        list.add(uuid.toString());
        cfg.set("claimed." + eventId, list);
        save();
        return true;
    }

    // ── 수령자 수 ──
    public int claimedCount(String eventId) {
        return cfg.getStringList("claimed." + eventId).size();
    }

    // ── 만료 여부 ──
    public boolean isExpired(CompEvent ev) {
        return ev.expiresAt() != -1 && System.currentTimeMillis() > ev.expiresAt();
    }

    // ── 표시용 기간 문자열 ──
    public String formatExpiry(long expiresAt) {
        if (expiresAt == -1) return "무기한";
        return DATE_FMT.format(Instant.ofEpochMilli(expiresAt)) + " 까지";
    }

    // ── 보상 내용 요약 문자열 ──
    public String formatRewards(CompEvent ev) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack is : ev.items()) {
            sb.append(formatItem(is)).append(" ×").append(is.getAmount()).append("  ");
        }
        return sb.toString().trim();
    }

    private String formatItem(ItemStack is) {
        if (is.getType() == Material.ENCHANTED_BOOK
                && is.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            StringBuilder sb = new StringBuilder("📖 ");
            for (var entry : meta.getStoredEnchants().entrySet()) {
                sb.append(entry.getKey().getKey().getKey())
                  .append(" ").append(toRoman(entry.getValue())).append(" ");
            }
            return sb.toString().trim();
        }
        return switch (is.getType()) {
            case DIAMOND               -> "💎 다이아몬드";
            case EMERALD               -> "💚 에메랄드";
            case GOLD_INGOT            -> "🟡 금괴";
            case IRON_INGOT            -> "⚙ 철괴";
            case COOKED_BEEF           -> "🥩 구운소고기";
            case BREAD                 -> "🍞 빵";
            case GOLDEN_APPLE          -> "🍎 황금사과";
            case ENCHANTED_GOLDEN_APPLE-> "✨ 인챈황금사과";
            case NETHERITE_INGOT       -> "🔱 네더라이트";
            case EXPERIENCE_BOTTLE     -> "💚 경험치병";
            default -> is.getType().name().toLowerCase().replace("_", " ");
        };
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; case 6 -> "VI";
            case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    // ── 기간 파싱: "7d", "24h", "30m" → epoch ms ──
    public static long parseDuration(String s) {
        s = s.trim().toLowerCase();
        long now = System.currentTimeMillis();
        if (s.equals("0") || s.equals("none") || s.equals("무기한")) return -1;
        try {
            if (s.endsWith("d"))  return now + Long.parseLong(s.replace("d",""))  * 86400_000L;
            if (s.endsWith("h"))  return now + Long.parseLong(s.replace("h",""))  * 3600_000L;
            if (s.endsWith("m"))  return now + Long.parseLong(s.replace("m",""))  * 60_000L;
        } catch (NumberFormatException ignored) {}
        return -1;  // 파싱 실패 → 무기한
    }

    // ── 아이템 파싱 ──
    // 일반: "diamond:32"
    // 인챈트 북: "enchanted_book:sharpness:5:1"  (마지막이 개수)
    public static List<ItemStack> parseItems(String s) {
        if (s == null || s.isBlank()) return new ArrayList<>();
        List<ItemStack> items = new ArrayList<>();
        for (String part : s.split(",")) {
            part = part.trim();
            if (part.isBlank()) continue;

            String[] kv = part.split(":");
            if (kv.length < 2) continue;

            String matName = kv[0].trim().toUpperCase().replace("-", "_").replace(" ", "_");

            // 인챈트 북: enchanted_book:인챈트:레벨:개수
            if (matName.equals("ENCHANTED_BOOK") && kv.length >= 3) {
                String enchName = kv[1].trim().toLowerCase();
                int level  = 1;
                int amount = 1;
                try {
                    if (kv.length == 3) {
                        // enchanted_book:sharpness:1  → 레벨만
                        level  = Integer.parseInt(kv[2].trim());
                    } else if (kv.length >= 4) {
                        // enchanted_book:sharpness:5:1  → 레벨:개수
                        level  = Integer.parseInt(kv[2].trim());
                        amount = Integer.parseInt(kv[3].trim());
                    }
                } catch (NumberFormatException ignored) {}

                Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchName));
                if (ench == null) continue;

                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, Math.max(1, amount));
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
                meta.addStoredEnchant(ench, level, true);
                book.setItemMeta(meta);
                items.add(book);
                continue;
            }

            // 일반 아이템: material:amount
            int amount;
            try { amount = Integer.parseInt(kv[kv.length - 1].trim()); }
            catch (NumberFormatException e) { continue; }
            if (amount <= 0) continue;
            try {
                Material mat = Material.valueOf(matName);
                items.add(new ItemStack(mat, amount));
            } catch (IllegalArgumentException ignored) {}
        }
        return items;
    }
}
