package jeminsmp.trade;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TradeManager {

    private final JeminSMPPlugin plugin;

    // 요청 대기: requester UUID → target UUID
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    // 활성 거래: 두 플레이어 UUID 중 하나 → 세션
    private final Map<UUID, TradeSession> activeTrades = new HashMap<>();

    // GUI 레이아웃 상수
    static final int[] SLOTS_A     = {0,1,2,3, 9,10,11,12, 18,19,20,21, 27,28,29,30};
    static final int[] SLOTS_B     = {5,6,7,8, 14,15,16,17, 23,24,25,26, 32,33,34,35};
    static final int[] DIVIDER     = {4,13,22,31,40};
    static final int CONFIRM_A     = 45;
    static final int CONFIRM_B     = 53;
    static final int CANCEL_BTN    = 49;
    static final String GUI_TITLE  = "§8거래 중...";

    public TradeManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    // ── 요청 ──

    public void sendRequest(Player from, Player to) {
        if (isInTrade(from)) { from.sendMessage("§c이미 거래 중입니다."); return; }
        if (isInTrade(to))   { from.sendMessage("§c" + to.getName() + " 님은 이미 거래 중입니다."); return; }
        if (hasPendingRequest(from)) { from.sendMessage("§c이미 거래 요청을 보냈습니다."); return; }

        pendingRequests.put(from.getUniqueId(), to.getUniqueId());
        from.sendMessage("§e" + to.getName() + " 님에게 거래 요청을 보냈습니다.");

        // 상대방에게 클릭 가능한 수락/거절 버튼
        Component msg = Component.text("§e[거래] §f" + from.getName() + " 님이 거래를 요청합니다.  ")
                .append(Component.text("[수락]")
                        .color(NamedTextColor.GREEN)
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/거래 수락 " + from.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("거래 수락"))))
                .append(Component.text("  "))
                .append(Component.text("[거절]")
                        .color(NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/거래 거절 " + from.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("거래 거절"))));
        to.sendMessage(msg);

        // 30초 후 자동 만료
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequests.containsKey(from.getUniqueId())) {
                pendingRequests.remove(from.getUniqueId());
                if (from.isOnline()) from.sendMessage("§7거래 요청이 만료되었습니다.");
                if (to.isOnline())   to.sendMessage("§7" + from.getName() + " 님의 거래 요청이 만료되었습니다.");
            }
        }, 600L);
    }

    public void acceptRequest(Player acceptor, Player requester) {
        if (!pendingRequests.containsKey(requester.getUniqueId())
                || !pendingRequests.get(requester.getUniqueId()).equals(acceptor.getUniqueId())) {
            acceptor.sendMessage("§c유효한 거래 요청이 없습니다.");
            return;
        }
        pendingRequests.remove(requester.getUniqueId());
        openTrade(requester, acceptor);
    }

    public void denyRequest(Player denier, Player requester) {
        pendingRequests.remove(requester.getUniqueId());
        denier.sendMessage("§c거래를 거절했습니다.");
        if (requester.isOnline()) requester.sendMessage("§c" + denier.getName() + " 님이 거래를 거절했습니다.");
    }

    // ── GUI 열기 ──

    private void openTrade(Player a, Player b) {
        TradeSession session = new TradeSession(a, b);

        Inventory guiA = buildGui(a, b);
        Inventory guiB = buildGui(b, a);
        session.guiA = guiA;
        session.guiB = guiB;

        activeTrades.put(a.getUniqueId(), session);
        activeTrades.put(b.getUniqueId(), session);

        a.openInventory(guiA);
        b.openInventory(guiB);

        a.sendMessage("§a거래 창이 열렸습니다. 아이템을 올린 후 §l확인§r§a 버튼을 누르세요.");
        b.sendMessage("§a거래 창이 열렸습니다. 아이템을 올린 후 §l확인§r§a 버튼을 누르세요.");
    }

    private Inventory buildGui(Player owner, Player opponent) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);

        // 구분선
        ItemStack divider = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§7");
        for (int s : DIVIDER) inv.setItem(s, divider);

        // 확인 버튼 (owner 쪽)
        boolean ownerIsA = owner.getUniqueId().equals(
                activeTrades.containsKey(owner.getUniqueId())
                        ? activeTrades.get(owner.getUniqueId()).playerA.getUniqueId()
                        : owner.getUniqueId());
        inv.setItem(CONFIRM_A, makeConfirmBtn(false));
        inv.setItem(CONFIRM_B, makeConfirmBtn(false));
        inv.setItem(CANCEL_BTN, makeItem(Material.BARRIER, "§c§l거래 취소"));

        // 라벨
        inv.setItem(36, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a§l" + owner.getName() + "의 제안"));
        inv.setItem(44, makeItem(Material.BLUE_STAINED_GLASS_PANE, "§b§l" + opponent.getName() + "의 제안"));

        return inv;
    }

    // ── 확인 버튼 처리 ──

    public void handleConfirm(Player player) {
        TradeSession session = activeTrades.get(player.getUniqueId());
        if (session == null) return;

        boolean wasLocked = session.isLockedBy(player);
        session.setLocked(player, !wasLocked); // 토글

        // 양쪽 GUI 확인 버튼 업데이트
        refreshButtons(session);

        Player opponent = session.getOpponent(player);
        if (session.isLockedBy(player)) {
            player.sendMessage("§a✔ 확인했습니다. 상대방을 기다리는 중...");
            if (opponent.isOnline()) opponent.sendMessage("§e" + player.getName() + " 님이 거래를 확인했습니다.");
        } else {
            player.sendMessage("§7확인을 취소했습니다.");
        }

        if (session.bothLocked()) {
            executeTrade(session);
        }
    }

    // ── 아이템 이동 시 상대 GUI 미리보기 동기화 ──

    public void syncPreview(TradeSession session) {
        // A의 GUI에서 SLOTS_A 내용을 → B의 GUI SLOTS_B에 복사 (읽기 전용)
        Inventory guiA = session.guiA;
        Inventory guiB = session.guiB;

        for (int i = 0; i < SLOTS_A.length; i++) {
            ItemStack item = guiA.getItem(SLOTS_A[i]);
            guiB.setItem(SLOTS_B[i], item != null ? grayOut(item.clone()) : null);
        }
        for (int i = 0; i < SLOTS_B.length; i++) {
            ItemStack item = guiB.getItem(SLOTS_B[i]);
            guiA.setItem(SLOTS_A[i], item != null ? grayOut(item.clone()) : null);
        }
    }

    private ItemStack grayOut(ItemStack item) {
        // 상대방 아이템은 그대로 보여줌 (클릭 불가는 Listener에서 처리)
        return item;
    }

    // ── 거래 실행 ──

    private void executeTrade(TradeSession session) {
        Player a = session.playerA;
        Player b = session.playerB;

        // A 제안 아이템 수집
        List<ItemStack> itemsFromA = collectItems(session.guiA, SLOTS_A);
        List<ItemStack> itemsFromB = collectItems(session.guiB, SLOTS_B);

        // GUI 닫기
        session.state = TradeSession.State.DONE;
        activeTrades.remove(a.getUniqueId());
        activeTrades.remove(b.getUniqueId());
        if (a.isOnline()) a.closeInventory();
        if (b.isOnline()) b.closeInventory();

        // 아이템 지급
        giveItems(a, itemsFromB);
        giveItems(b, itemsFromA);

        if (a.isOnline()) a.sendMessage("§a§l거래 완료! §f" + b.getName() + " 님과의 거래가 성사되었습니다.");
        if (b.isOnline()) b.sendMessage("§a§l거래 완료! §f" + a.getName() + " 님과의 거래가 성사되었습니다.");
    }

    // ── 취소 ──

    public void cancelTrade(Player player) {
        TradeSession session = activeTrades.remove(player.getUniqueId());
        if (session == null) return;

        Player opponent = session.getOpponent(player);
        activeTrades.remove(opponent.getUniqueId());
        session.state = TradeSession.State.DONE;

        // 본인 아이템 반환
        returnItems(player, session.guiA, session.guiB, session);

        if (player.isOnline())   { player.closeInventory();   player.sendMessage("§c거래를 취소했습니다."); }
        if (opponent.isOnline()) { opponent.closeInventory(); opponent.sendMessage("§c" + player.getName() + " 님이 거래를 취소했습니다."); }
    }

    private void returnItems(Player canceller, Inventory guiA, Inventory guiB, TradeSession session) {
        Player a = session.playerA;
        Player b = session.playerB;
        // A 제안 슬롯 → A에게 반환
        for (int slot : SLOTS_A) {
            ItemStack item = guiA.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                giveItems(a, List.of(item));
                guiA.setItem(slot, null);
            }
        }
        // B 제안 슬롯 → B에게 반환
        for (int slot : SLOTS_B) {
            ItemStack item = guiB.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                giveItems(b, List.of(item));
                guiB.setItem(slot, null);
            }
        }
    }

    // ── 유틸 ──

    public TradeSession getSession(Player p) { return activeTrades.get(p.getUniqueId()); }
    public boolean isInTrade(Player p)       { return activeTrades.containsKey(p.getUniqueId()); }
    public boolean hasPendingRequest(Player p) { return pendingRequests.containsKey(p.getUniqueId()); }

    public UUID getPendingTarget(Player requester) { return pendingRequests.get(requester.getUniqueId()); }

    private void refreshButtons(TradeSession session) {
        session.guiA.setItem(CONFIRM_A, makeConfirmBtn(session.lockedA));
        session.guiB.setItem(CONFIRM_B, makeConfirmBtn(session.lockedB));
        // 상대 쪽 상태도 표시
        session.guiA.setItem(CONFIRM_B, makeOpponentStatus(session.lockedB));
        session.guiB.setItem(CONFIRM_A, makeOpponentStatus(session.lockedA));
    }

    private List<ItemStack> collectItems(Inventory gui, int[] slots) {
        List<ItemStack> list = new ArrayList<>();
        for (int slot : slots) {
            ItemStack item = gui.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                list.add(item.clone());
                gui.setItem(slot, null);
            }
        }
        return list;
    }

    private void giveItems(Player player, List<ItemStack> items) {
        if (!player.isOnline()) return;
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack drop : leftover.values())
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private ItemStack makeItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeConfirmBtn(boolean confirmed) {
        ItemStack item = new ItemStack(confirmed ? Material.LIME_WOOL : Material.GRAY_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(confirmed ? "§a§l✔ 확인 완료 (클릭 시 취소)" : "§7§l확인하기");
        meta.setLore(List.of(confirmed ? "§7다시 클릭하면 취소" : "§7아이템 배치 후 클릭"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeOpponentStatus(boolean confirmed) {
        ItemStack item = new ItemStack(confirmed ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(confirmed ? "§a상대방 확인 완료" : "§c상대방 대기 중");
        item.setItemMeta(meta);
        return item;
    }
}
