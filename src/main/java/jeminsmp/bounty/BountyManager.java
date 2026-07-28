package jeminsmp.bounty;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class BountyManager {

    private final JeminSMPPlugin plugin;

    // target UUID → 이 플레이어에게 걸린 현상금 목록 (여러 명이 각자 걸 수 있음)
    private final Map<UUID, List<BountyEntry>> bounties = new HashMap<>();

    public BountyManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    // ── 현상금 등록 ──
    public boolean addBounty(Player placer, UUID targetUUID, String targetName, int amount) {
        // 자기 자신에게 걸기 불가
        if (placer.getUniqueId().equals(targetUUID)) {
            placer.sendMessage("§c자기 자신에게는 현상금을 걸 수 없습니다.");
            return false;
        }
        // 다이아몬드 확인 & 차감
        if (countDiamonds(placer) < amount) {
            placer.sendMessage("§c다이아몬드가 부족합니다! §b💎 " + amount + "개 필요");
            return false;
        }
        removeDiamonds(placer, amount);

        bounties.computeIfAbsent(targetUUID, k -> new ArrayList<>())
                .add(new BountyEntry(placer.getUniqueId(), placer.getName(), amount));

        int total = getTotalBounty(targetUUID);

        // 전체 방송
        Bukkit.broadcast(Component.text("💰 ", NamedTextColor.GOLD)
                .append(Component.text(placer.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("님이 ", NamedTextColor.GOLD))
                .append(Component.text(targetName, NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("님에게 ", NamedTextColor.GOLD))
                .append(Component.text("💎 " + amount + "개", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text("의 현상금을 걸었습니다! (총 💎 " + total + "개)", NamedTextColor.GOLD)));
        return true;
    }

    // ── 본인이 건 현상금 취소 (환불) ──
    public boolean cancelBounty(Player placer, UUID targetUUID, String targetName) {
        List<BountyEntry> list = bounties.get(targetUUID);
        if (list == null) return false;

        BountyEntry mine = list.stream()
                .filter(e -> e.placerUUID.equals(placer.getUniqueId()))
                .findFirst().orElse(null);
        if (mine == null) return false;

        list.remove(mine);
        if (list.isEmpty()) bounties.remove(targetUUID);

        // 환불
        placer.getInventory().addItem(new ItemStack(Material.DIAMOND, mine.amount));
        placer.sendMessage("§a현상금이 취소되었습니다. §b💎 " + mine.amount + "개 환불.");
        return true;
    }

    // ── 사망 처리: 현상금 지급 ──
    public void onKill(Player killer, UUID deadUUID, String deadName) {
        List<BountyEntry> list = bounties.remove(deadUUID);
        if (list == null || list.isEmpty()) return;

        int total = list.stream().mapToInt(e -> e.amount).sum();
        killer.getInventory().addItem(new ItemStack(Material.DIAMOND, total));

        Bukkit.broadcast(Component.text("💰 ", NamedTextColor.GOLD)
                .append(Component.text(killer.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("님이 현상금 목표 ", NamedTextColor.GOLD))
                .append(Component.text(deadName, NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("님을 처치하여 ", NamedTextColor.GOLD))
                .append(Component.text("💎 " + total + "개", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text("의 현상금을 획득했습니다!", NamedTextColor.GOLD)));
    }

    // ── 조회 ──
    public int getTotalBounty(UUID targetUUID) {
        List<BountyEntry> list = bounties.get(targetUUID);
        if (list == null) return 0;
        return list.stream().mapToInt(e -> e.amount).sum();
    }

    public boolean hasBounty(UUID targetUUID) {
        return bounties.containsKey(targetUUID) && !bounties.get(targetUUID).isEmpty();
    }

    public Map<UUID, List<BountyEntry>> getAllBounties() { return Collections.unmodifiableMap(bounties); }

    // ── 다이아몬드 유틸 ──
    private int countDiamonds(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) count += item.getAmount();
        }
        return count;
    }

    private void removeDiamonds(Player player, int amount) {
        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, amount));
    }

    public record BountyEntry(UUID placerUUID, String placerName, int amount) {}
}
