package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.Map;
import java.util.Set;

public class JobListener implements Listener {

    private final JeminSMPPlugin plugin;

    // 광물 종류별 경험치 (심층일수록/희귀할수록 더 높음)
    private static final Map<Material, Long> ORE_EXP = Map.ofEntries(
            Map.entry(Material.COAL_ORE, 3L), Map.entry(Material.DEEPSLATE_COAL_ORE, 4L),
            Map.entry(Material.COPPER_ORE, 3L), Map.entry(Material.DEEPSLATE_COPPER_ORE, 4L),
            Map.entry(Material.IRON_ORE, 5L), Map.entry(Material.DEEPSLATE_IRON_ORE, 6L),
            Map.entry(Material.LAPIS_ORE, 6L), Map.entry(Material.DEEPSLATE_LAPIS_ORE, 7L),
            Map.entry(Material.REDSTONE_ORE, 6L), Map.entry(Material.DEEPSLATE_REDSTONE_ORE, 7L),
            Map.entry(Material.GOLD_ORE, 8L), Map.entry(Material.DEEPSLATE_GOLD_ORE, 9L),
            Map.entry(Material.NETHER_GOLD_ORE, 6L), Map.entry(Material.NETHER_QUARTZ_ORE, 5L),
            Map.entry(Material.EMERALD_ORE, 15L), Map.entry(Material.DEEPSLATE_EMERALD_ORE, 18L),
            Map.entry(Material.DIAMOND_ORE, 15L), Map.entry(Material.DEEPSLATE_DIAMOND_ORE, 18L),
            Map.entry(Material.ANCIENT_DEBRIS, 25L)
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

        Long oreExp = ORE_EXP.get(type);
        if (oreExp != null) {
            plugin.getJobManager().addExp(player, JobType.MINER, oreExp);
            return;
        }

        if (CROPS.contains(type) && block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() == ageable.getMaximumAge()) {
                plugin.getJobManager().addExp(player, JobType.FARMER, 4L);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        long exp = event.getEntity() instanceof Player ? 20L : 6L;
        plugin.getJobManager().addExp(killer, JobType.WARRIOR, exp);
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        plugin.getJobManager().addExp(event.getPlayer(), JobType.FISHER, 8L);
    }
}
