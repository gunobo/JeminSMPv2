package jeminsmp.team;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeamLevelManager {

    // Level thresholds in ticks: Lv0=0, Lv1=10h, Lv2=40h, Lv3=100h, Lv4=200h, Lv5=400h
    public static final long[] THRESHOLDS = {0L, 720_000L, 2_880_000L, 7_200_000L, 14_400_000L, 28_800_000L};
    public static final String[] PERK_NAMES = {"없음", "공용 상자", "팀 홈 (1개)", "엔더상자 명령어", "팀 홈 2개", "팀원 텔레포트"};
    private static final String[] LEVEL_COLORS = {"§7", "§e", "§a", "§b", "§d", "§6§l"};

    private final JeminSMPPlugin plugin;
    private final Map<String, Inventory> activeChests = new HashMap<>();
    private final Map<UUID, String> chestViewers = new HashMap<>();
    private final Map<UUID, BukkitTask> tpWarmups = new HashMap<>();

    public TeamLevelManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public int getLevel(String teamName) {
        long ticks = plugin.getTeamManager().getTotalTicks(teamName);
        int level = 0;
        for (int i = THRESHOLDS.length - 1; i >= 0; i--) {
            if (ticks >= THRESHOLDS[i]) {
                level = i;
                break;
            }
        }
        return level;
    }

    public boolean hasLevel(String teamName, int required) {
        return getLevel(teamName) >= required;
    }

    public int getMaxHomes(String teamName) {
        int lv = getLevel(teamName);
        if (lv >= 4) return 2;
        if (lv >= 2) return 1;
        return 0;
    }

    public void openChest(Player player, String teamName) {
        Inventory inv = activeChests.get(teamName);
        if (inv == null) {
            TeamManager.TeamData data = plugin.getTeamManager().getTeam(teamName);
            Component title = Component.text("[팀] " + teamName + " 공용 상자", NamedTextColor.GOLD);
            inv = Bukkit.createInventory(null, 27, title);
            if (data != null) {
                for (int i = 0; i < data.chest.length; i++) {
                    if (data.chest[i] != null) inv.setItem(i, data.chest[i]);
                }
            }
            activeChests.put(teamName, inv);
        }
        chestViewers.put(player.getUniqueId(), teamName);
        player.openInventory(inv);
    }

    public void onChestClose(Player player, Inventory inv) {
        UUID uuid = player.getUniqueId();
        String teamName = chestViewers.remove(uuid);
        if (teamName == null) return;
        // Check if any other viewers remain
        boolean othersViewing = chestViewers.containsValue(teamName);
        if (!othersViewing) {
            plugin.getTeamManager().saveChest(teamName, inv);
            activeChests.remove(teamName);
        }
    }

    public void startTpWarmup(Player player, Player target) {
        cancelWarmup(player.getUniqueId());
        final double[] startPos = {player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ()};
        player.sendMessage("§e3초 후 §f" + target.getName() + " §e님에게 텔레포트합니다. 움직이지 마세요!");

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tpWarmups.remove(player.getUniqueId());
            if (!player.isOnline() || !target.isOnline()) {
                player.sendMessage("§c텔레포트 대상이 오프라인 상태입니다.");
                return;
            }
            player.teleport(target.getLocation());
            player.sendMessage("§a" + target.getName() + " §a님에게 텔레포트했습니다.");
        }, 60L);

        tpWarmups.put(player.getUniqueId(), task);

        // Store start position for move check via metadata
        player.setMetadata("team_tp_start_x", new org.bukkit.metadata.FixedMetadataValue(plugin, startPos[0]));
        player.setMetadata("team_tp_start_y", new org.bukkit.metadata.FixedMetadataValue(plugin, startPos[1]));
        player.setMetadata("team_tp_start_z", new org.bukkit.metadata.FixedMetadataValue(plugin, startPos[2]));
    }

    public void cancelWarmup(UUID uuid) {
        BukkitTask task = tpWarmups.remove(uuid);
        if (task != null) task.cancel();
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.removeMetadata("team_tp_start_x", plugin);
            p.removeMetadata("team_tp_start_y", plugin);
            p.removeMetadata("team_tp_start_z", plugin);
        }
    }

    public boolean hasPendingWarmup(UUID uuid) {
        return tpWarmups.containsKey(uuid);
    }

    public static String getLevelColor(int level) {
        if (level < 0 || level >= LEVEL_COLORS.length) return "§7";
        return LEVEL_COLORS[level];
    }
}
