package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class JobSkillItemListener implements Listener {

    private final JeminSMPPlugin plugin;
    private final SkillCommand skillCommand;

    public JobSkillItemListener(JeminSMPPlugin plugin, SkillCommand skillCommand) {
        this.plugin = plugin;
        this.skillCommand = skillCommand;
    }

    // 우클릭: 스킬 전환 (좌클릭 발동은 onSwing에서 처리 — PlayerInteractEvent의 LEFT_CLICK_AIR는
    // 클라이언트가 허공 연타 시 매번 안 보내는 경우가 많아서 신뢰 못 함)
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!JobItems.isSkillItem(plugin, item)) return;

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            cycle(player, item);
        }
    }

    // 좌클릭(팔 휘두름) 감지 — 허공/블록/엔티티 상관없이 항상 옴, 스킬 발동은 여기서만 처리
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

    // 부활 시 해금된 스킬 있는데 스킬템이 없으면 다시 지급
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> regrantIfMissing(player));
    }

    private void cycle(Player player, ItemStack item) {
        var d = plugin.getJobManager().getData(player.getUniqueId());
        if (d.job == null) return;
        SkillType current = JobItems.getSkillItemType(plugin, item);
        SkillType[] all = SkillType.values();
        int startIdx = current == null ? 0 : current.ordinal();

        for (int i = 1; i <= all.length; i++) {
            SkillType candidate = all[(startIdx + i) % all.length];
            if (d.skillLevel(candidate) > 0) {
                JobItems.applySkillType(plugin, item, d.job, candidate);
                player.sendMessage("§b⚡ 스킬 전환: §e" + candidate.display());
                return;
            }
        }
        player.sendMessage("§c해금된 스킬이 없습니다.");
    }

    private void fire(Player player, ItemStack item) {
        SkillType type = JobItems.getSkillItemType(plugin, item);
        if (type == null) return;
        skillCommand.handleUse(player, type.name().toLowerCase());
    }

    /** 전직 직후 호출: 들고 있는 스킬템이 있으면 새 직업 아이콘으로 갱신, 없으면 새로 지급 시도 */
    public void onJobChanged(Player player) {
        int slot = JobItems.findSkillItemSlot(plugin, player);
        if (slot == -1) { regrantIfMissing(player); return; }

        var d = plugin.getJobManager().getData(player.getUniqueId());
        if (d.job == null) return;
        ItemStack item = player.getInventory().getItem(slot);
        SkillType current = JobItems.getSkillItemType(plugin, item);

        if (current != null && d.skillLevel(current) > 0) {
            JobItems.applySkillType(plugin, item, d.job, current);
            return;
        }
        for (SkillType t : SkillType.values()) {
            if (d.skillLevel(t) > 0) {
                JobItems.applySkillType(plugin, item, d.job, t);
                return;
            }
        }
    }

    /** 첫 스킬 해금 시 / 사망 후 부활 시 스킬템이 없으면 만들어 지급 */
    public void regrantIfMissing(Player player) {
        var d = plugin.getJobManager().getData(player.getUniqueId());
        if (d.job == null) return;

        SkillType firstUnlocked = null;
        for (SkillType t : SkillType.values()) {
            if (d.skillLevel(t) > 0) { firstUnlocked = t; break; }
        }
        if (firstUnlocked == null) return; // 아직 아무 스킬도 안 풀림

        if (JobItems.findSkillItemSlot(plugin, player) != -1) return; // 이미 있음

        ItemStack skillItem = JobItems.createSkillItem(plugin, d.job, firstUnlocked);
        var leftover = player.getInventory().addItem(skillItem);
        leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        player.sendMessage("§b⚡ 스킬템을 획득했습니다! §7(우클릭: 전환, 좌클릭: 사용)");
    }
}
