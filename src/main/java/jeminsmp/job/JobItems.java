package jeminsmp.job;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** 직업 관련 아이템(전직의 서, 스킬템) 생성/판별. 리소스팩 텍스처가 추가되면 CustomModelData로 바로 반영됨. */
public class JobItems {

    public static final int JOB_SCROLL_PRICE = 15; // 다이아 가격
    public static final int JOB_SCROLL_MODEL_DATA = 1001;

    private static NamespacedKey scrollKey(org.bukkit.plugin.Plugin plugin) {
        return new NamespacedKey(plugin, "job_change_scroll");
    }

    private static NamespacedKey skillItemKey(org.bukkit.plugin.Plugin plugin) {
        return new NamespacedKey(plugin, "job_skill_item");
    }

    /** 직업 + 스킬 종류 조합마다 다른 아이콘: 2000 + (직업순번+1)*10 + (스킬순번+1) */
    private static int skillModelData(JobType job, SkillType type) {
        return 2000 + (job.ordinal() + 1) * 10 + (type.ordinal() + 1);
    }

    public static ItemStack createJobChangeScroll(org.bukkit.plugin.Plugin plugin) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("전직의 서", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("우클릭하여 사용", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("직업을 다시 선택할 수 있습니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("(레벨/스킬포인트 초기화됨)", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setCustomModelData(JOB_SCROLL_MODEL_DATA);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(scrollKey(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isJobChangeScroll(org.bukkit.plugin.Plugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(scrollKey(plugin), PersistentDataType.BYTE);
    }

    /** 채팅에 직업 선택 버튼(클릭 시 즉시 확정)을 보냄. 전직의 서 사용, 최초 접속 안내에서 공용으로 씀 */
    public static void sendJobSelectPrompt(Player player, String header) {
        Component msg = Component.text(header, NamedTextColor.LIGHT_PURPLE);
        for (JobType t : JobType.values()) {
            msg = msg.append(Component.newline())
                    .append(Component.text("  [" + t.icon() + " " + t.display() + "]", NamedTextColor.AQUA, TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/job forceselect " + t.name().toLowerCase()))
                            .hoverEvent(HoverEvent.showText(Component.text("클릭하여 " + t.display() + " 선택"))));
        }
        player.sendMessage(msg);
    }

    // ── 스킬템 (우클릭: 전환 / 좌클릭: 발동) ──

    public static ItemStack createSkillItem(org.bukkit.plugin.Plugin plugin, JobType job, SkillType type) {
        ItemStack item = new ItemStack(Material.STICK);
        applySkillType(plugin, item, job, type);
        return item;
    }

    public static boolean isSkillItem(org.bukkit.plugin.Plugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(skillItemKey(plugin), PersistentDataType.STRING);
    }

    public static SkillType getSkillItemType(org.bukkit.plugin.Plugin plugin, ItemStack item) {
        if (!isSkillItem(plugin, item)) return null;
        String name = item.getItemMeta().getPersistentDataContainer().get(skillItemKey(plugin), PersistentDataType.STRING);
        return name == null ? null : SkillType.fromString(name);
    }

    /** 같은 ItemStack 인스턴스에 타입/아이콘/이름을 다시 씀 (전환용). 직업+스킬 조합마다 아이콘이 다름 */
    public static void applySkillType(org.bukkit.plugin.Plugin plugin, ItemStack item, JobType job, SkillType type) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("⚡ " + job.icon() + " " + job.display() + " · " + type.display() + " 스킬템",
                        NamedTextColor.AQUA, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("우클릭: 스킬 전환", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("좌클릭: 스킬 사용", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setCustomModelData(skillModelData(job, type));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(skillItemKey(plugin), PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
    }

    /** 인벤토리에 스킬템이 이미 있는지(칸 인덱스, 없으면 -1) */
    public static int findSkillItemSlot(org.bukkit.plugin.Plugin plugin, Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isSkillItem(plugin, inv.getItem(i))) return i;
        }
        return -1;
    }
}
