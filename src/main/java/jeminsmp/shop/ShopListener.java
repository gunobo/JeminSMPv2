package jeminsmp.shop;

import io.papermc.paper.event.player.AsyncChatEvent;
import jeminsmp.JeminSMPPlugin;
import jeminsmp.shop.ShopManager.Listing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ShopListener implements Listener {

    private final JeminSMPPlugin plugin;
    private final ShopGui gui;

    public ShopListener(JeminSMPPlugin plugin, ShopGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        ShopGui.GuiState state = gui.openStates.get(uuid);
        if (state == null) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory().equals(event.getView().getBottomInventory())) return;

        int slot = event.getSlot();
        switch (state.type()) {
            case "main"    -> handleMainClick(player, state, slot);
            case "mylist"  -> handleMyListClick(player, state, slot);
            case "confirm" -> handleConfirmClick(player, state, slot);
        }
    }

    private void handleMainClick(Player player, ShopGui.GuiState state, int slot) {
        if (slot < ShopGui.PAGE_SIZE) {
            Integer listingId = state.slotToListingId().get(slot);
            if (listingId == null) return;
            Listing listing = gui.shopManager.getListing(listingId);
            if (listing == null) {
                player.sendMessage(Component.text("이미 판매 완료된 상품입니다.").color(NamedTextColor.RED));
                gui.openMainShop(player, state.page());
                return;
            }
            if (listing.sellerUUID().equals(player.getUniqueId())) {
                player.sendMessage(Component.text("내 상품은 '내 판매 목록'에서 취소할 수 있습니다.").color(NamedTextColor.YELLOW));
                return;
            }
            gui.openBuyConfirm(player, listing, state.page());
            return;
        }
        switch (slot) {
            case 45 -> gui.openMainShop(player, state.page() - 1);
            case 49 -> handleSellButton(player, state.page());
            case 50 -> gui.openMyListings(player, 1);
            case 53 -> gui.openMainShop(player, state.page() + 1);
        }
    }

    private void handleSellButton(Player player, int returnPage) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            player.sendMessage(Component.text("손에 판매할 아이템을 들고 클릭하세요!").color(NamedTextColor.RED));
            return;
        }
        gui.awaitingPrice.put(player.getUniqueId(), held.clone());
        player.closeInventory();
        player.sendMessage(Component.text("──────────────────────────────").color(NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("판매 가격(다이아몬드 개수)을 채팅으로 입력하세요.").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("예: 5  →  💎 다이아몬드 5개").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("취소하려면 'q' 를 입력하세요.").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("──────────────────────────────").color(NamedTextColor.DARK_GRAY));
    }

    private void handleMyListClick(Player player, ShopGui.GuiState state, int slot) {
        if (slot < ShopGui.PAGE_SIZE) {
            Integer listingId = state.slotToListingId().get(slot);
            if (listingId == null) return;
            Listing listing = gui.shopManager.getListing(listingId);
            if (listing == null) {
                player.sendMessage(Component.text("이미 판매 완료된 상품입니다.").color(NamedTextColor.GRAY));
                gui.openMyListings(player, state.page());
                return;
            }
            if (!listing.sellerUUID().equals(player.getUniqueId())) return;
            gui.shopManager.removeListing(listingId);
            giveItem(player, listing.item());
            player.sendMessage(Component.text("판매를 취소하고 아이템을 돌려받았습니다.").color(NamedTextColor.GREEN));
            gui.openMyListings(player, state.page());
            return;
        }
        switch (slot) {
            case 45 -> gui.openMyListings(player, state.page() - 1);
            case 49 -> gui.openMainShop(player, 1);
            case 53 -> gui.openMyListings(player, state.page() + 1);
        }
    }

    private void handleConfirmClick(Player player, ShopGui.GuiState state, int slot) {
        if (slot == 10) { gui.openMainShop(player, state.page()); return; }
        if (slot == 16) {
            int listingId = state.confirmListingId();
            Listing listing = gui.shopManager.getListing(listingId);
            if (listing == null) {
                player.sendMessage(Component.text("이미 판매 완료된 상품입니다.").color(NamedTextColor.RED));
                gui.openMainShop(player, state.page());
                return;
            }
            if (listing.sellerUUID().equals(player.getUniqueId())) {
                player.sendMessage(Component.text("자신의 상품은 구매할 수 없습니다.").color(NamedTextColor.RED));
                gui.openMainShop(player, state.page());
                return;
            }
            int price = (int) listing.price();
            int buyerDiamonds = gui.countDiamonds(player);
            if (buyerDiamonds < price) {
                player.sendMessage(Component.text("💎 다이아몬드 부족! (필요: " + price + "개 / 보유: " + buyerDiamonds + "개)").color(NamedTextColor.RED));
                gui.openMainShop(player, state.page());
                return;
            }
            gui.removeDiamonds(player, price);
            gui.shopManager.removeListing(listingId);
            giveItem(player, listing.item());
            player.sendMessage(Component.text("✅ 구매 완료! 💎 " + price + "개 다이아몬드 차감").color(NamedTextColor.GREEN));

            Player seller = player.getServer().getPlayer(listing.sellerUUID());
            if (seller != null) {
                gui.giveDiamonds(seller, price);
                seller.sendMessage(Component.text("💰 " + player.getName() + " 님이 구매 → 💎 " + price + "개 다이아몬드 지급!").color(NamedTextColor.GOLD));
            } else {
                gui.mailboxManager.addDiamonds(listing.sellerUUID(), price);
            }
            gui.openMainShop(player, state.page());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (gui.awaitingPrice.containsKey(uuid)) return;
        // 1틱 후에 확인: openInventory() 호출로 인한 close면 새 GUI가 이미 열려 있음
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) { gui.openStates.remove(uuid); return; }
            var topInv = player.getOpenInventory().getTopInventory();
            Component title = player.getOpenInventory().title();
            // 상점 GUI가 아닌 일반 인벤토리면 상태 제거
            if (!title.equals(ShopGui.TITLE_MAIN)
                    && !title.equals(ShopGui.TITLE_MYLIST)
                    && !title.equals(ShopGui.TITLE_CONFIRM)) {
                gui.openStates.remove(uuid);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ItemStack pendingItem = gui.awaitingPrice.get(player.getUniqueId());
        if (pendingItem == null) return;

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (input.equalsIgnoreCase("q") || input.equalsIgnoreCase("취소")) {
            gui.awaitingPrice.remove(player.getUniqueId());
            player.sendMessage(Component.text("판매 등록을 취소했습니다.").color(NamedTextColor.GRAY));
            plugin.getServer().getScheduler().runTask(plugin, () -> gui.openMainShop(player, 1));
            return;
        }

        long price;
        try {
            price = Long.parseLong(input.replace(",", ""));
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("올바른 숫자를 입력하세요. (예: 5)").color(NamedTextColor.RED));
            return;
        }

        ItemStack currentHeld = player.getInventory().getItemInMainHand();
        if (currentHeld.getType().isAir() || !currentHeld.isSimilar(pendingItem)) {
            player.sendMessage(Component.text("손에 든 아이템이 변경되었습니다. 다시 시도하세요.").color(NamedTextColor.RED));
            gui.awaitingPrice.remove(player.getUniqueId());
            return;
        }

        final long finalPrice = price;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack toSell = player.getInventory().getItemInMainHand().clone();
            player.getInventory().setItemInMainHand(null);
            int id = gui.shopManager.addListing(player.getUniqueId(), player.getName(), toSell, finalPrice);
            player.sendMessage(Component.text("✅ 판매 등록 완료! " + toSell.getAmount() + "개 × 💎 " + finalPrice + " 다이아몬드 (번호: " + id + ")").color(NamedTextColor.GREEN));
            gui.awaitingPrice.remove(player.getUniqueId());
            gui.openMainShop(player, 1);
        });
    }

    private void giveItem(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item.clone());
        leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }
}
