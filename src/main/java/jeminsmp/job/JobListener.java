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

    // 1차 전직용 "희귀 활동" 판정
    private static final Set<Material> RARE_ORES = Set.of(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.ANCIENT_DEBRIS
    );
    private static final Set<Material> TREASURE = Set.of(
            Material.NAUTILUS_SHELL, Material.ENCHANTED_BOOK, Material.NAME_TAG,
            Material.SADDLE, Material.BOW, Material.FISHING_ROD, Material.TRIDENT
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
            if (RARE_ORES.contains(type)) plugin.getJobManager().addRareProgress(player, JobType.MINER, 1L);
            var d = plugin.getJobManager().getData(player.getUniqueId());
            if (d.job == JobType.MINER) JobPassives.onMinerOre(player, d.level, block.getLocation());
            return;
        }

        if (CROPS.contains(type) && block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() == ageable.getMaximumAge()) {
                plugin.getJobManager().addExp(player, JobType.FARMER, 1L);
                if (type == Material.NETHER_WART) plugin.getJobManager().addRareProgress(player, JobType.FARMER, 1L);
                var d = plugin.getJobManager().getData(player.getUniqueId());
                if (d.job == JobType.FARMER) JobPassives.onFarmerHarvest(player, d.level, block.getLocation());
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        plugin.getJobManager().addExp(killer, JobType.WARRIOR, 1L);
        if (event.getEntity() instanceof Player) plugin.getJobManager().addRareProgress(killer, JobType.WARRIOR, 1L);
        var d = plugin.getJobManager().getData(killer.getUniqueId());
        if (d.job == JobType.WARRIOR) JobPassives.onWarriorKill(killer, d.level);
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        plugin.getJobManager().addExp(player, JobType.FISHER, 1L);
        if (event.getCaught() instanceof Item item && TREASURE.contains(item.getItemStack().getType())) {
            plugin.getJobManager().addRareProgress(player, JobType.FISHER, 1L);
        }
        var d = plugin.getJobManager().getData(player.getUniqueId());
        if (d.job == JobType.FISHER) JobPassives.onFisherCatch(player, d.level, player.getLocation());
    }
}
