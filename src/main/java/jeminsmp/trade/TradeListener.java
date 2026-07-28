package jeminsmp.trade;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

public class TradeListener implements Listener {

    private final JeminSMPPlugin plugin;

    public TradeListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().equals(TradeManager.GUI_TITLE)) return;

        TradeManager tm = plugin.getTradeManager();
        TradeSession session = tm.getSession(player);
        if (session == null || session.state == TradeSession.State.DONE) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getRawSlot();
        int size  = event.getView().getTopInventory().getSize();

        // 하단 인벤토리 → 자기 제안 슬롯에만 넣기
        if (slot >= size) {
            // 플레이어 인벤토리 클릭 — 상단 GUI의 자기 슬롯으로 shift-click 허용
            if (!event.isShiftClick()) return; // 그냥 클릭은 허용
            // shift-click 대상이 자기 제안 슬롯인지 확인은 아래에서
        }

        Inventory topInv = event.getView().getTopInventory();

        // 확인 버튼
        if (slot == TradeManager.CONFIRM_A && session.isA(player)) {
            event.setCancelled(true);
            if (!session.lockedA || !session.lockedB) handleConfirmClick(player, session, tm);
            return;
        }
        if (slot == TradeManager.CONFIRM_B && !session.isA(player)) {
            event.setCancelled(true);
            if (!session.lockedA || !session.lockedB) handleConfirmClick(player, session, tm);
            return;
        }

        // 취소 버튼
        if (slot == TradeManager.CANCEL_BTN) {
            event.setCancelled(true);
            tm.cancelTrade(player);
            return;
        }

        // 구분선, 라벨 클릭 차단
        if (isFixed(slot)) { event.setCancelled(true); return; }

        // 상대방 제안 슬롯 클릭 차단
        boolean isPlayerA = session.isA(player);
        int[] mySlots     = isPlayerA ? TradeManager.SLOTS_A : TradeManager.SLOTS_B;
        int[] theirSlots  = isPlayerA ? TradeManager.SLOTS_B : TradeManager.SLOTS_A;

        if (contains(theirSlots, slot)) { event.setCancelled(true); return; }

        // 이미 확인 눌렀으면 아이템 변경 불가
        if (session.isLockedBy(player)) {
            event.setCancelled(true);
            player.sendMessage("§c확인 상태에서는 아이템을 변경할 수 없습니다. 다시 클릭하여 확인을 취소하세요.");
            return;
        }

        // 자기 슬롯 내 조작 → 허용 후 상대 GUI에 미리보기 동기화
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (session.state != TradeSession.State.DONE)
                tm.syncPreview(session);
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TradeManager.GUI_TITLE)) return;

        TradeManager tm = plugin.getTradeManager();
        TradeSession session = tm.getSession(player);
        if (session == null) return;

        boolean isA     = session.isA(player);
        int[] mySlots   = isA ? TradeManager.SLOTS_A : TradeManager.SLOTS_B;
        int[] theirSlots= isA ? TradeManager.SLOTS_B : TradeManager.SLOTS_A;
        int size = event.getView().getTopInventory().getSize();

        for (int slot : event.getRawSlots()) {
            if (slot < size) {
                if (contains(theirSlots, slot) || isFixed(slot)) {
                    event.setCancelled(true);
                    return;
                }
                if (session.isLockedBy(player)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (session.state != TradeSession.State.DONE)
                tm.syncPreview(session);
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TradeManager.GUI_TITLE)) return;

        TradeManager tm = plugin.getTradeManager();
        TradeSession session = tm.getSession(player);
        if (session == null || session.state == TradeSession.State.DONE) return;

        // GUI 강제 닫힌 경우 → 거래 취소
        tm.cancelTrade(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        TradeManager tm = plugin.getTradeManager();
        if (tm.isInTrade(event.getPlayer())) {
            tm.cancelTrade(event.getPlayer());
        }
    }

    private void handleConfirmClick(Player player, TradeSession session, TradeManager tm) {
        tm.handleConfirm(player);
    }

    private boolean isFixed(int slot) {
        for (int s : TradeManager.DIVIDER) if (s == slot) return true;
        return slot == 36 || slot == 44; // 라벨 슬롯
    }

    private boolean contains(int[] arr, int val) {
        for (int v : arr) if (v == val) return true;
        return false;
    }
}
