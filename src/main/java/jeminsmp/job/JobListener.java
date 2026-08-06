package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.Set;

/** 미션(개수) 기반 진행도 — 광물 종류/몹 종류 상관없이 자격 있는 행동 1회 = 1. 같은 행동이 직업 패시브도 발동시킴 */
public class JobListener implements Listener {

    private final JeminSMPPlugin plugin;

    private static final Set<Material> ORES = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.ANCIENT_DEBRIS
    );

    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART
    );

    public JobListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        if (ORES.contains(type)) {
            plugin.getJobManager().addExp(player, JobType.MINER, 1L);
            var d = plugin.getJobManager().getData(player.getUniqueId());
            if (d.job == JobType.MINER) JobPassives.onMinerOre(player, d.level, block.getLocation());
            return;
        }

        if (CROPS.contains(type) && block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() == ageable.getMaximumAge()) {
                plugin.getJobManager().addExp(player, JobType.FARMER, 1L);
                var d = plugin.getJobManager().getData(player.getUniqueId());
                if (d.job == JobType.FARMER) JobPassives.onFarmerHarvest(player, d.level, block.getLocation());
            }
        }
    }

    // 2차 전직 "드랍 2배" — 광부는 광물, 농부는 작물 캘 때 드랍량 2배
    @EventHandler
    public void onBlockDropItem(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlockState().getType();
        JobType job = ORES.contains(type) ? JobType.MINER : CROPS.contains(type) ? JobType.FARMER : null;
        if (job == null) return;
        if (!plugin.getJobManager().isDoubleYieldActive(player.getUniqueId(), job)) return;
        for (Item item : event.getItems()) {
            item.getItemStack().setAmount(item.getItemStack().getAmount() * 2);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        plugin.getJobManager().addExp(killer, JobType.WARRIOR, 1L);
        var d = plugin.getJobManager().getData(killer.getUniqueId());
        if (d.job == JobType.WARRIOR) JobPassives.onWarriorKill(killer, d.level);

        // 전사 2차 전직 "드랍 2배" — 처치 드랍량 2배
        if (plugin.getJobManager().isDoubleYieldActive(killer.getUniqueId(), JobType.WARRIOR)) {
            event.getDrops().replaceAll(item -> {
                item.setAmount(item.getAmount() * 2);
                return item;
            });
        }
    }

    // 어부 3차 전직 — 낚시찌 입질 대기시간 20% 감소
    @EventHandler
    public void onFishCast(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) return;
        var d = plugin.getJobManager().getData(event.getPlayer().getUniqueId());
        if (d.job != JobType.FISHER || d.tier(JobType.FISHER) < 3) return;
        var hook = event.getHook();
        hook.setMinWaitTime((int) (hook.getMinWaitTime() * 0.8));
        hook.setMaxWaitTime((int) (hook.getMaxWaitTime() * 0.8));
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        plugin.getJobManager().addExp(player, JobType.FISHER, 1L);
        var d = plugin.getJobManager().getData(player.getUniqueId());
        if (d.job == JobType.FISHER) JobPassives.onFisherCatch(player, d.level, player.getLocation());

        // 어부 2차 전직 "드랍 2배" — 낚은 아이템 개수 2배
        if (event.getCaught() instanceof Item item && plugin.getJobManager().isDoubleYieldActive(player.getUniqueId(), JobType.FISHER)) {
            item.getItemStack().setAmount(item.getItemStack().getAmount() * 2);
        }
    }
}
