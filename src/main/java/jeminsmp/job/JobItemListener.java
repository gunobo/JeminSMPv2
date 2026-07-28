package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
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

        JobItems.sendJobSelectPrompt(player, "📜 전직할 직업을 선택하세요: ");
    }
}
