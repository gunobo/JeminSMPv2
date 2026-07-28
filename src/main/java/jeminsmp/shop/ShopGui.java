package jeminsmp.shop;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.shop.ShopManager.Listing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ShopGui {

    public static final Component TITLE_MAIN    = Component.text("유저 상점").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
    public static final Component TITLE_MYLIST  = Component.text("내 판매 목록").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
    public static final Component TITLE_CONFIRM = Component.text("구매 확인").color(NamedTextColor.RED).decorate(TextDecoration.BOLD);

    static final int PAGE_SIZE = 45;

    private final JeminSMPPlugin plugin;
    final ShopManager shopManager;
    final MailboxManager mailboxManager;

    final Map<UUID, GuiState> openStates = new HashMap<>();
    final Map<UUID, ItemStack> awaitingPrice = new HashMap<>();

    public ShopGui(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        this.shopManager = plugin.getShopManager();
        this.mailboxManager = plugin.getMailboxManager();
    }

    public void openMainShop(Player player, int page) {
        List<Listing> all = shopManager.getAllListings();
        int totalPages = Math.max(1, (int) Math.ceil((double) all.size() / PAGE_SIZE));
        page = Math.max(1, Math.min(page, totalPages));

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MAIN);
        Map<Integer, Integer> slotMap = new HashMap<>();

        int from = (page - 1) * PAGE_SIZE;
        List<Listing> pageItems = all.subList(from, Math.min(from + PAGE_SIZE, all.size()));

        for (int i = 0; i < pageItems.size(); i++) {
            Listing l = pageItems.get(i);
            inv.setItem(i, buildListingDisplay(l, player.getUniqueId()));
            slotMap.put(i, l.id());
        }
        for (int i = pageItems.size(); i < PAGE_SIZE; i++) inv.setItem(i, grayPane());
        buildNavBar(inv, page, totalPages, false);
        openStates.put(player.getUniqueId(), new GuiState("main", page, slotMap, -1));
        player.openInventory(inv);
    }

    public void openMyListings(Player player, int page) {
        List<Listing> mine = shopManager.getListingsBySeller(player.getUniqueId());
        int totalPages = Math.max(1, (int) Math.ceil((double) mine.size() / PAGE_SIZE));
        page = Math.max(1, Math.min(page, totalPages));

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MYLIST);
        Map<Integer, Integer> slotMap = new HashMap<>();

        int from = (page - 1) * PAGE_SIZE;
        List<Listing> pageItems = mine.subList(from, Math.min(from + PAGE_SIZE, mine.size()));

        for (int i = 0; i < pageItems.size(); i++) {
            Listing l = pageItems.get(i);
            inv.setItem(i, buildMyListingDisplay(l));
            slotMap.put(i, l.id());
        }
        for (int i = pageItems.size(); i < PAGE_SIZE; i++) inv.setItem(i, grayPane());
        buildNavBar(inv, page, totalPages, true);
        openStates.put(player.getUniqueId(), new GuiState("mylist", page, slotMap, -1));
        player.openInventory(inv);
    }

    public void openBuyConfirm(Player player, Listing listing, int fromPage) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_CONFIRM);
        for (int i = 0; i < 27; i++) inv.setItem(i, grayPane());

        ItemStack display = listing.item().clone();
        ItemMeta meta = display.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : new ArrayList<>());
        lore.add(Component.empty());
        lore.add(Component.text("가격: ").color(NamedTextColor.YELLOW)
                .append(Component.text("💎 " + listing.price() + " 다이아몬드").color(NamedTextColor.AQUA)));
        lore.add(Component.text("판매자: " + listing.sellerName()).color(NamedTextColor.GRAY));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        display.setItemMeta(meta);
        inv.setItem(13, display);

        int buyerDiamonds = countDiamonds(player);
        boolean canAfford = buyerDiamonds >= listing.price();

        inv.setItem(10, createItem(Material.RED_STAINED_GLASS_PANE,
                Component.text("❌ 취소").color(NamedTextColor.RED)));
        inv.setItem(16, createItemWithLore(Material.GREEN_STAINED_GLASS_PANE,
                Component.text("✅ 구매 확인").color(canAfford ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY),
                List.of(
                        Component.text("필요: 💎 " + listing.price()).color(NamedTextColor.GRAY),
                        Component.text("보유: 💎 " + buyerDiamonds)
                                .color(canAfford ? NamedTextColor.GREEN : NamedTextColor.RED)
                )));

        openStates.put(player.getUniqueId(), new GuiState("confirm", fromPage, Map.of(), listing.id()));
        player.openInventory(inv);
    }

    private ItemStack buildListingDisplay(Listing l, UUID viewerUuid) {
        ItemStack item = l.item().clone();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("가격: ").color(NamedTextColor.YELLOW)
                .append(Component.text("💎 " + l.price() + " 다이아몬드").color(NamedTextColor.AQUA)));
        lore.add(Component.text("판매자: " + l.sellerName()).color(NamedTextColor.GRAY));
        lore.add(Component.text("수량: " + l.item().getAmount() + "개").color(NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (l.sellerUUID().equals(viewerUuid)) {
            lore.add(Component.text("★ 내 상품 (내 목록에서 취소 가능)").color(NamedTextColor.AQUA));
        } else {
            lore.add(Component.text("좌클릭 → 구매 확인").color(NamedTextColor.GREEN));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildMyListingDisplay(Listing l) {
        ItemStack item = l.item().clone();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("가격: ").color(NamedTextColor.YELLOW)
                .append(Component.text("💎 " + l.price() + " 다이아몬드").color(NamedTextColor.AQUA)));
        lore.add(Component.text("수량: " + l.item().getAmount() + "개").color(NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("좌클릭 → 판매 취소 (아이템 반환)").color(NamedTextColor.RED));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private void buildNavBar(Inventory inv, int page, int totalPages, boolean isMyList) {
        for (int i = 45; i < 54; i++) inv.setItem(i, grayPane());
        if (page > 1) inv.setItem(45, createItem(Material.ARROW,
                Component.text("◀ 이전 페이지").color(NamedTextColor.YELLOW)));
        inv.setItem(48, createItem(Material.PAPER,
                Component.text(page + " / " + totalPages + " 페이지").color(NamedTextColor.WHITE)));
        if (!isMyList) {
            inv.setItem(49, createItemWithLore(Material.EMERALD,
                    Component.text("+ 판매 등록").color(NamedTextColor.GREEN),
                    List.of(Component.text("손에 든 아이템을 판매합니다.").color(NamedTextColor.GRAY),
                            Component.text("클릭 후 채팅으로 다이아 가격 입력").color(NamedTextColor.GRAY))));
            inv.setItem(50, createItemWithLore(Material.BOOK,
                    Component.text("내 판매 목록").color(NamedTextColor.YELLOW),
                    List.of(Component.text("내가 등록한 상품 보기").color(NamedTextColor.GRAY))));
        } else {
            inv.setItem(49, createItem(Material.DARK_OAK_DOOR,
                    Component.text("← 상점으로 돌아가기").color(NamedTextColor.WHITE)));
        }
        if (page < totalPages) inv.setItem(53, createItem(Material.ARROW,
                Component.text("다음 페이지 ▶").color(NamedTextColor.YELLOW)));
    }

    int countDiamonds(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) count += item.getAmount();
        }
        return count;
    }

    void removeDiamonds(Player player, int amount) {
        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, amount));
    }

    void giveDiamonds(Player player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64);
            var leftover = player.getInventory().addItem(new ItemStack(Material.DIAMOND, stackSize));
            leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            remaining -= stackSize;
        }
    }

    ItemStack createItem(Material mat, Component name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack createItemWithLore(Material mat, Component name, List<Component> lore) {
        ItemStack item = createItem(mat, name);
        ItemMeta meta = item.getItemMeta();
        meta.lore(lore.stream().map(l -> l.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack grayPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        return pane;
    }

    record GuiState(String type, int page, Map<Integer, Integer> slotToListingId, int confirmListingId) {}
}
