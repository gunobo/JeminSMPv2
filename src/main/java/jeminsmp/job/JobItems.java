package jeminsmp.job;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** 직업 관련 아이템(전직의 서) 생성/판별. 리소스팩 텍스처가 추가되면 CustomModelData로 바로 반영됨. */
public class JobItems {

    public static final int JOB_SCROLL_PRICE = 15; // 다이아 가격
    public static final int JOB_SCROLL_MODEL_DATA = 1001;

    private static NamespacedKey scrollKey(org.bukkit.plugin.Plugin plugin) {
        return new NamespacedKey(plugin, "job_change_scroll");
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
}
