package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class JobItemListener implements Listener {

    private final JeminSMPPlugin plugin;

    public JobItemListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        var item = event.getItem();
        if (!JobItems.isJobChangeScroll(plugin, item)) return;

        event.setCancelled(true);
        item.setAmount(item.getAmount() - 1);

        Component msg = Component.text("📜 전직할 직업을 선택하세요: ", NamedTextColor.LIGHT_PURPLE);
        for (JobType t : JobType.values()) {
            msg = msg.append(Component.text("[" + t.icon() + t.display() + "] ", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/job forceselect " + t.name().toLowerCase()))
                    .hoverEvent(HoverEvent.showText(Component.text("클릭하여 " + t.display() + "로 전직"))));
        }
        player.sendMessage(msg);
    }
}
