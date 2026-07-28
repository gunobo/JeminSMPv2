package jeminsmp.team.mission;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.team.TeamManager;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;

import java.util.Set;

public class TeamMissionListener implements Listener {

    private static final Set<Material> ORES = Set.of(
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
        Material.ANCIENT_DEBRIS
    );

    private final JeminSMPPlugin plugin;

    public TeamMissionListener(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    private String getTeamName(Player player) {
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        return team == null ? null : team.name;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        String teamName = getTeamName(killer);
        if (teamName == null) return;

        TeamMissionManager missionManager = plugin.getTeamMissionManager();
        missionManager.checkAndReset(teamName);

        if (entity instanceof Player) {
            // Player kill
            missionManager.addProgress(teamName, MissionType.PLAYER_KILL, 1);
        } else {
            // Mob kill
            missionManager.addProgress(teamName, MissionType.MOB_KILL, 1);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        if (!ORES.contains(event.getBlock().getType())) return;

        Player player = event.getPlayer();
        String teamName = getTeamName(player);
        if (teamName == null) return;

        plugin.getTeamMissionManager().addProgress(teamName, MissionType.ORE_MINE, 1);
    }

    @EventHandler
    public void onStatisticIncrement(PlayerStatisticIncrementEvent event) {
        Player player = event.getPlayer();
        String teamName = getTeamName(player);
        if (teamName == null) return;

        Statistic stat = event.getStatistic();
        TeamMissionManager missionManager = plugin.getTeamMissionManager();

        if (stat == Statistic.WALK_ONE_CM || stat == Statistic.SPRINT_ONE_CM || stat == Statistic.CROUCH_ONE_CM) {
            long delta = event.getNewValue() - event.getPreviousValue();
            if (delta > 0) missionManager.addProgress(teamName, MissionType.WALK_DISTANCE, delta);
        } else if (stat == Statistic.CRAFT_ITEM) {
            missionManager.addProgress(teamName, MissionType.CRAFT_ITEM, 1);
        }
    }
}
