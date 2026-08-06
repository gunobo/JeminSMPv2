package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** 스킬마다 별도 아이템(전부 종이 베이스, 아이콘만 다름). 핫바에서 원하는 스킬템으로 바꿔 들고 좌/우클릭으로 발동 */
public class JobSkillItemListener implements Listener {

    private final JeminSMPPlugin plugin;
    private final SkillCommand skillCommand;

    public JobSkillItemListener(JeminSMPPlugin plugin, SkillCommand skillCommand) {
        this.plugin = plugin;
        this.skillCommand = skillCommand;
    }

    // 우클릭도 좌클릭과 동일하게 발동
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!JobItems.isSkillItem(plugin, item)) return;

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            fire(player, item);
        }
    }

    // 좌클릭(팔 휘두름) 감지 — 허공/블록/엔티티 상관없이 항상 옴, PlayerInteractEvent의 LEFT_CLICK_AIR는
    // 클라이언트가 허공 연타 시 매번 안 보내는 경우가 많아서 이쪽으로 처리
    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!JobItems.isSkillItem(plugin, item)) return;
        fire(player, item);
    }

    // 엔티티를 때렸을 때 원래 공격 피해만 취소 (스킬 발동은 onSwing이 처리)
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!JobItems.isSkillItem(plugin, item)) return;
        event.setCancelled(true);
    }

    // 죽을 때 스킬템은 드롭 안 되게
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(i -> JobItems.isSkillItem(plugin, i));
    }

    // 직접 버리는 것도 막기 (취소하면 원래 칸에 그대로 남음)
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!JobItems.isSkillItem(plugin, event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§c스킬템은 버릴 수 없습니다.");
    }

    // 상자/모루/제작대 등 다른 인벤토리로 옮기는 것도 막기 (플레이어 자기 인벤토리 안에서 슬롯 이동은 허용)
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getType() == org.bukkit.event.inventory.InventoryType.PLAYER
                || top.getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) return;

        // 내 인벤토리 칸에서 shift-클릭으로 위(다른 인벤토리)로 보내려는 경우
        if (event.getClick().isShiftClick() && event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getBottomInventory())
                && JobItems.isSkillItem(plugin, event.getCurrentItem())) {
            event.setCancelled(true);
            player.sendMessage("§c스킬템은 다른 곳에 넣을 수 없습니다.");
            return;
        }

        // 커서에 들고 위(다른 인벤토리) 칸을 직접 클릭해서 넣으려는 경우
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(top)
                && JobItems.isSkillItem(plugin, event.getCursor())) {
            event.setCancelled(true);
            player.sendMessage("§c스킬템은 다른 곳에 넣을 수 없습니다.");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!JobItems.isSkillItem(plugin, event.getOldCursor())) return;
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (touchesTop) {
            event.setCancelled(true);
            player.sendMessage("§c스킬템은 다른 곳에 넣을 수 없습니다.");
        }
    }

    // 조합(제작대/인벤토리 2x2)에 재료로 들어가는 것도 막기 — 재료 칸에 있으면 결과물 자체가 안 나오게 함
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (JobItems.isSkillItem(plugin, item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    // 부활 시 해금된 스킬 있는데 스킬템이 없으면 다시 지급
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> regrantIfMissing(player));
    }

    private void fire(Player player, ItemStack item) {
        SkillType type = JobItems.getSkillItemType(plugin, item);
        if (type == null) return;
        skillCommand.handleUse(player, type.name().toLowerCase());
    }

    /** 전직 직후 호출: 들고 있는 스킬템들 라벨을 새 직업으로 갱신하고, 새 직업에서 잠긴 스킬템은 회수, 새로 풀린 건 지급 */
    public void onJobChanged(Player player) {
        plugin.getJobManager().refreshTier3Perk(player);
        var d = plugin.getJobManager().getData(player.getUniqueId());
        if (d.job == null) return;

        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (!JobItems.isSkillItem(plugin, item)) continue;
            SkillType type = JobItems.getSkillItemType(plugin, item);
            if (type != null && d.skillLevel(type) > 0) {
                JobItems.applySkillType(plugin, item, d.job, type);
            } else {
                inv.setItem(i, null);
            }
        }
        regrantIfMissing(player);
    }

    /** 해금됐는데 아직 없는 스킬템을 전부 지급 (레벨업 직후, 전직 직후, 부활 시) */
    public void regrantIfMissing(Player player) {
        var d = plugin.getJobManager().getData(player.getUniqueId());
        if (d.job == null) return;

        for (SkillType type : SkillType.values()) {
            if (d.skillLevel(type) <= 0) continue;
            if (JobItems.findSkillItemSlot(plugin, player, type) != -1) continue; // 이미 있음

            ItemStack skillItem = JobItems.createSkillItem(plugin, d.job, type);
            var leftover = player.getInventory().addItem(skillItem);
            String name = JobSkills.skillName(d.job, type);
            if (leftover.isEmpty()) {
                player.sendMessage("§b⚡ " + name + " 스킬템을 획득했습니다! §7(좌/우클릭으로 사용)");
            } else {
                leftover.values().forEach(i -> plugin.getMailboxManager().addItem(player.getUniqueId(), i));
                player.sendMessage("§b⚡ " + name + " 스킬템 획득! §7인벤토리가 가득 차서 우편함으로 보냈습니다 (§f/우편함§7).");
            }
        }
    }
}
