package jeminsmp.title.commands;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.title.TitleManager.TitleInfo;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TitleCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;

    public TitleCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        switch (args[0].toLowerCase()) {
            case "list"     -> handleList(sender);
            case "buy"      -> handleBuy(sender, args);
            case "equip"    -> handleEquip(sender, args);
            case "remove"   -> handleRemove(sender);
            case "mytitles" -> handleMyTitles(sender);
            case "admin"    -> handleAdmin(sender, args);
            default         -> sendHelp(sender);
        }
        return true;
    }

    // ── 탭 컴플리터 ──
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("list", "buy", "equip", "remove", "mytitles"));
            if (sender.isOp()) subs.add("admin");
            return subs;
        }
        if (args.length == 2) {
            UUID uuid = sender instanceof Player p ? p.getUniqueId() : null;
            switch (args[0].toLowerCase()) {
                case "buy" -> {
                    // 구매 가능한 칭호 (자동완성 = 전체 - 보유)
                    return plugin.getTitleManager().getAllTitles().stream()
                            .filter(t -> uuid == null || !plugin.getTitleManager().hasTitle(uuid, t.id()))
                            .map(TitleInfo::id).toList();
                }
                case "equip" -> {
                    if (uuid == null) return List.of();
                    return new ArrayList<>(plugin.getTitleManager().getOwnedTitles(uuid));
                }
                case "admin" -> {
                    if (!sender.isOp()) return List.of();
                    return List.of("create", "delete", "give", "edit");
                }
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            switch (args[1].toLowerCase()) {
                case "delete", "edit" -> {
                    return plugin.getTitleManager().getAllTitles().stream().map(TitleInfo::id).toList();
                }
                case "give" -> {
                    return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
                }
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            switch (args[1].toLowerCase()) {
                case "give" -> {
                    return plugin.getTitleManager().getAllTitles().stream().map(TitleInfo::id).toList();
                }
                case "edit" -> {
                    return List.of("price", "display");
                }
            }
        }
        // /title admin edit <id> <price|display> <값> — 값 자리는 힌트만
        if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("edit")) {
            return switch (args[3].toLowerCase()) {
                case "price"   -> List.of("<다이아개수>");
                case "display" -> List.of("<표시텍스트> (예: &6[VIP])");
                default        -> List.of();
            };
        }
        return List.of();
    }

    // ── 목록 (다이아 가격으로 표시) ──
    private void handleList(CommandSender sender) {
        var titles = plugin.getTitleManager().getAllTitles().stream()
                .filter(t -> !t.id().startsWith("auto_")) // 자동 칭호는 숨김
                .toList();
        if (titles.isEmpty()) { sender.sendMessage("§7등록된 칭호가 없습니다."); return; }
        sender.sendMessage("§6§l=== 칭호 목록 (다이아몬드 구매) ===");
        UUID uuid = sender instanceof Player p ? p.getUniqueId() : null;
        for (TitleInfo t : titles) {
            String status = "";
            if (uuid != null) {
                if (plugin.getTitleManager().hasTitle(uuid, t.id()) &&
                        t.id().equals(getEquippedId(uuid))) status = " §a[장착 중]";
                else if (plugin.getTitleManager().hasTitle(uuid, t.id())) status = " §e[보유 중]";
            }
            String displayText = LegacyComponentSerializer.legacySection().serialize(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(t.display()));
            sender.sendMessage("§7[§e" + t.id() + "§7] " + displayText
                    + " §7| §b💎 " + t.price() + "개" + status);
        }
        if (sender instanceof Player p) {
            int diamonds = countDiamonds(p);
            sender.sendMessage("§7보유 다이아몬드: §b💎 " + diamonds + "개");
        }
    }

    // ── 구매 (다이아몬드 차감) ──
    private void handleBuy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용 가능합니다."); return; }
        if (args.length < 2) { player.sendMessage("§c사용법: /title buy <칭호ID>"); return; }
        TitleInfo info = plugin.getTitleManager().getTitle(args[1]);
        if (info == null) { player.sendMessage("§c존재하지 않는 칭호입니다: §e" + args[1]); return; }
        if (info.id().startsWith("auto_")) { player.sendMessage("§c이 칭호는 구매할 수 없습니다."); return; }
        if (plugin.getTitleManager().hasTitle(player.getUniqueId(), info.id())) {
            player.sendMessage("§c이미 보유 중인 칭호입니다."); return;
        }
        int have = countDiamonds(player);
        if (have < info.price()) {
            player.sendMessage("§c다이아몬드가 부족합니다! §b💎 " + info.price() + "개 필요 §7(보유: " + have + "개)");
            return;
        }
        // 다이아 차감
        removeDiamonds(player, (int) info.price());
        plugin.getTitleManager().giveTitle(player.getUniqueId(), info.id());
        String displayText = LegacyComponentSerializer.legacySection().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(info.display()));
        player.sendMessage("§a칭호 " + displayText + " §a구매 완료! §7(/title equip " + info.id() + ")");
    }

    // ── 장착 (1개 제한 — 자동으로 이전 칭호 교체) ──
    private void handleEquip(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용 가능합니다."); return; }
        if (args.length < 2) { player.sendMessage("§c사용법: /title equip <칭호ID>"); return; }

        // 현재 장착 중인 칭호 확인
        String prevId = getEquippedId(player.getUniqueId());

        if (!plugin.getTitleManager().equipTitle(player.getUniqueId(), args[1])) {
            player.sendMessage("§c해당 칭호를 보유하고 있지 않습니다."); return;
        }

        // 이전 칭호가 있으면 교체 메시지
        if (prevId != null && !prevId.equals(args[1])) {
            TitleInfo prev = plugin.getTitleManager().getTitle(prevId);
            String prevName = prev != null
                    ? LegacyComponentSerializer.legacySection().serialize(
                            LegacyComponentSerializer.legacyAmpersand().deserialize(prev.display()))
                    : prevId;
            player.sendMessage("§7이전 칭호 " + prevName + " §7→ 해제됨");
        }

        TitleInfo info = plugin.getTitleManager().getTitle(args[1]);
        String newName = info != null
                ? LegacyComponentSerializer.legacySection().serialize(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(info.display()))
                : args[1];
        player.sendMessage("§a" + newName + " §a장착 완료! §7(칭호는 1개만 장착 가능)");
    }

    // ── 해제 ──
    private void handleRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용 가능합니다."); return; }
        plugin.getTitleManager().removeEquipped(player.getUniqueId());
        player.sendMessage("§a칭호를 해제했습니다.");
    }

    // ── 내 칭호 ──
    private void handleMyTitles(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용 가능합니다."); return; }
        var owned = plugin.getTitleManager().getOwnedTitles(player.getUniqueId());
        if (owned.isEmpty()) { player.sendMessage("§7보유한 칭호가 없습니다."); return; }
        player.sendMessage("§6§l=== 내 칭호 목록 ===");
        String equippedId = getEquippedId(player.getUniqueId());
        for (String id : owned) {
            TitleInfo info = plugin.getTitleManager().getTitle(id);
            String name = info != null ? LegacyComponentSerializer.legacySection().serialize(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(info.display())) : id;
            String tag = id.equals(equippedId) ? " §a[장착 중]" : "";
            player.sendMessage("§7• " + name + tag + " §8(/title equip " + id + ")");
        }
    }

    // ── 관리자 ──
    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.isOp()) { sender.sendMessage("§c관리자만 사용할 수 있습니다."); return; }
        if (args.length < 2) { sender.sendMessage("§c사용법: /title admin <create|delete|give>"); return; }
        switch (args[1].toLowerCase()) {
            case "create" -> {
                if (args.length < 5) { sender.sendMessage("§c사용법: /title admin create <id> <가격💎> <표시>"); return; }
                try {
                    long price = Long.parseLong(args[3]);
                    String display = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                    if (plugin.getTitleManager().createTitle(args[2], price, display))
                        sender.sendMessage("§a칭호 '" + args[2] + "' 생성 완료. (💎 " + price + "개)");
                    else sender.sendMessage("§c이미 존재하는 칭호 ID입니다.");
                } catch (NumberFormatException e) { sender.sendMessage("§c가격은 숫자(다이아 개수)여야 합니다."); }
            }
            case "edit" -> {
                if (args.length < 5) { sender.sendMessage("§c사용법: /title admin edit <id> <price|display> <값>"); return; }
                String field = args[3].toLowerCase();
                if (!field.equals("price") && !field.equals("display")) {
                    sender.sendMessage("§c수정 항목은 §eprice §c또는 §edisplay §c만 가능합니다."); return;
                }
                if (plugin.getTitleManager().getTitle(args[2]) == null) {
                    sender.sendMessage("§c존재하지 않는 칭호 ID입니다: §e" + args[2]); return;
                }
                String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                if (plugin.getTitleManager().editTitle(args[2], field, value)) {
                    String label = field.equals("price") ? "가격" : "표시명";
                    sender.sendMessage("§a칭호 '" + args[2] + "' 의 " + label + " → §e" + value + " §a수정 완료.");
                } else {
                    sender.sendMessage("§c수정 실패. 가격은 숫자여야 합니다.");
                }
            }
            case "delete" -> {
                if (args.length < 3) { sender.sendMessage("§c사용법: /title admin delete <id>"); return; }
                sender.sendMessage(plugin.getTitleManager().deleteTitle(args[2])
                        ? "§a칭호 '" + args[2] + "' 삭제 완료." : "§c존재하지 않는 칭호입니다.");
            }
            case "give" -> {
                if (args.length < 4) { sender.sendMessage("§c사용법: /title admin give <플레이어> <id>"); return; }
                Player target = sender.getServer().getPlayerExact(args[2]);
                if (target == null) { sender.sendMessage("§c온라인 플레이어가 아닙니다."); return; }
                if (plugin.getTitleManager().getTitle(args[3]) == null) { sender.sendMessage("§c존재하지 않는 칭호입니다."); return; }
                plugin.getTitleManager().giveTitle(target.getUniqueId(), args[3]);
                sender.sendMessage("§a" + target.getName() + " 님에게 칭호 지급 완료.");
                target.sendMessage("§6🏅 §f관리자가 칭호를 지급했습니다!");
            }
            default -> sender.sendMessage("§c알 수 없는 서브커맨드입니다.");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== /title 명령어 ===");
        sender.sendMessage("§e/title list §7— 칭호 목록 (다이아 가격)");
        sender.sendMessage("§e/title buy <id> §7— 다이아몬드로 구매");
        sender.sendMessage("§e/title equip <id> §7— 장착");
        sender.sendMessage("§e/title remove §7— 해제");
        sender.sendMessage("§e/title mytitles §7— 내 칭호 목록");
        if (sender.isOp()) {
            sender.sendMessage("§7--- 관리자 ---");
            sender.sendMessage("§e/title admin create <id> <가격> <표시> §7— 생성");
            sender.sendMessage("§e/title admin edit <id> <price|display> <값> §7— 수정");
            sender.sendMessage("§e/title admin delete <id> §7— 삭제");
            sender.sendMessage("§e/title admin give <플레이어> <id> §7— 지급");
        }
    }

    private int countDiamonds(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND)
                count += item.getAmount();
        }
        return count;
    }

    private void removeDiamonds(Player player, int amount) {
        ItemStack toRemove = new ItemStack(Material.DIAMOND, amount);
        player.getInventory().removeItem(toRemove);
    }

    private String getEquippedId(UUID uuid) {
        String equippedDisplay = plugin.getTitleManager().getEquippedDisplay(uuid);
        if (equippedDisplay == null) return null;
        return plugin.getTitleManager().getOwnedTitles(uuid).stream()
                .filter(id -> {
                    TitleInfo info = plugin.getTitleManager().getTitle(id);
                    return info != null && info.display().equals(equippedDisplay);
                }).findFirst().orElse(null);
    }
}
