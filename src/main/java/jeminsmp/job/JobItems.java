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

import java.util.ArrayList;
import java.util.List;

/** 직업 관련 아이템(전직의 서, 스킬템) 생성/판별. 리소스팩 텍스처가 추가되면 CustomModelData로 바로 반영됨. */
public class JobItems {

    public static final int JOB_SCROLL_PRICE = 32; // 다이아 가격
    public static final String JOB_SCROLL_MODEL_KEY = "job_change_scroll";

    private static NamespacedKey scrollKey(org.bukkit.plugin.Plugin plugin) {
        return new NamespacedKey(plugin, "job_change_scroll");
    }

    private static NamespacedKey skillItemKey(org.bukkit.plugin.Plugin plugin) {
        return new NamespacedKey(plugin, "job_skill_item");
    }

    /** 스킬 종류별 쿨다운 그룹 키. 아이템에 박아두면 엔더펄/황금사과처럼 쿨다운 중엔 아이콘이 회색으로 사선 표시됨 */
    public static NamespacedKey cooldownGroupKey(org.bukkit.plugin.Plugin plugin, SkillType type) {
        return new NamespacedKey(plugin, "skill_cd_" + type.name().toLowerCase());
    }

    /** 직업 + 스킬 종류 조합마다 다른 아이콘 키. resourcepack의 skill_<직업>_<스킬>.png 파일명과 일치해야 함 */
    private static String skillModelKey(JobType job, SkillType type) {
        return "skill_" + job.name().toLowerCase() + "_" + type.name().toLowerCase();
    }

    /** 1.21.4+ 새 아이템 모델 시스템은 CustomModelData가 정수 하나가 아니라 문자열 리스트 컴포넌트라 이걸로 설정 */
    private static void setModelKey(ItemMeta meta, String key) {
        var component = meta.getCustomModelDataComponent();
        component.setStrings(List.of(key));
        meta.setCustomModelDataComponent(component);
    }

    public static ItemStack createJobChangeScroll(org.bukkit.plugin.Plugin plugin) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("전직의 서", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("우클릭하여 사용", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("직업을 다시 선택할 수 있습니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("(직업별로 레벨이 따로 저장돼요)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        setModelKey(meta, JOB_SCROLL_MODEL_KEY);
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
                    .append(Component.text("  [" + t.display() + "]", NamedTextColor.AQUA, TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/job forceselect " + t.name().toLowerCase()))
                            .hoverEvent(HoverEvent.showText(Component.text("클릭하여 " + t.display() + " 선택"))));
        }
        player.sendMessage(msg);
    }

    // ── 스킬템 (스킬마다 별도 아이템, 핫바 슬롯 전환으로 바꿔 씀 / 좌클릭·우클릭: 발동) ──

    public static ItemStack createSkillItem(org.bukkit.plugin.Plugin plugin, JobType job, SkillType type, int level, int tier) {
        ItemStack item = new ItemStack(Material.PAPER);
        applySkillType(plugin, item, job, type, level, tier);
        return item;
    }

    public static boolean isSkillItem(org.bukkit.plugin.Plugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(skillItemKey(plugin), PersistentDataType.STRING);
    }

    public static SkillType getSkillItemType(org.bukkit.plugin.Plugin plugin, ItemStack item) {
        if (!isSkillItem(plugin, item)) return null;
        String name = item.getItemMeta().getPersistentDataContainer().get(skillItemKey(plugin), PersistentDataType.STRING);
        return name == null ? null : SkillType.fromString(name);
    }

    /** 같은 ItemStack 인스턴스에 아이콘/이름/설명을 다시 씀 (전직/레벨업으로 직업·수치가 바뀌었을 때 갱신용). 타입 자체는 안 바뀜 */
    public static void applySkillType(org.bukkit.plugin.Plugin plugin, ItemStack item, JobType job, SkillType type, int level, int tier) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("⚡ " + JobSkills.skillName(job, type), NamedTextColor.AQUA, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        int skillCap = JobManager.skillCapForTier(tier);
        int cooldown = JobManager.cooldownSeconds(level, tier);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(job.display() + " · " + type.display() + " 스킬", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        for (String line : JobSkills.describe(job, type, level)) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("레벨 " + level + "/" + skillCap + " · 쿨다운 " + cooldown + "초", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("좌클릭 또는 우클릭: 스킬 사용", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        setModelKey(meta, skillModelKey(job, type));
        var useCooldown = meta.getUseCooldown();
        useCooldown.setCooldownGroup(cooldownGroupKey(plugin, type));
        meta.setUseCooldown(useCooldown);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(skillItemKey(plugin), PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
    }

    /** 인벤토리에 스킬템이 하나라도 있는지(칸 인덱스, 없으면 -1) */
    public static int findSkillItemSlot(org.bukkit.plugin.Plugin plugin, Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isSkillItem(plugin, inv.getItem(i))) return i;
        }
        return -1;
    }

    /** 특정 스킬 종류의 스킬템이 인벤토리에 있는지(칸 인덱스, 없으면 -1) */
    public static int findSkillItemSlot(org.bukkit.plugin.Plugin plugin, Player player, SkillType type) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isSkillItem(plugin, item) && getSkillItemType(plugin, item) == type) return i;
        }
        return -1;
    }
}
