package jeminsmp.team.mission;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamMissionManager {

    private final JeminSMPPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    private final List<Mission> missions = new ArrayList<>();

    public TeamMissionManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "team_missions.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        config = YamlConfiguration.loadConfiguration(file);

        // Hardcoded default missions
        missions.add(new Mission("daily_mob",   "몹 사냥꾼",  "몹 2000마리 처치",    MissionType.MOB_KILL,      2000,      360_000L, true));
        missions.add(new Mission("daily_ore",   "광부",       "광석 500개 채굴",     MissionType.ORE_MINE,       500,      144_000L, true));
        missions.add(new Mission("daily_walk",  "탐험가",     "10km 이동",           MissionType.WALK_DISTANCE, 1_000_000, 72_000L,  true));
        missions.add(new Mission("weekly_pvp",  "전사",       "플레이어 10명 처치",  MissionType.PLAYER_KILL,       10,     720_000L, false));
        missions.add(new Mission("weekly_craft","장인",       "아이템 500개 제작",   MissionType.CRAFT_ITEM,       500,     360_000L, false));
    }

    public List<Mission> getMissions() {
        return missions;
    }

    public long getProgress(String teamName, String missionId) {
        return config.getLong("progress." + teamName + "." + missionId + ".progress", 0L);
    }

    public boolean isCompleted(String teamName, String missionId) {
        return config.getBoolean("progress." + teamName + "." + missionId + ".completed", false);
    }

    public long getBonusTicks(String teamName) {
        return config.getLong("bonusTicks." + teamName, 0L);
    }

    public void addProgress(String teamName, MissionType type, long amount) {
        checkAndReset(teamName);

        for (Mission mission : missions) {
            if (mission.type() != type) continue;
            if (isCompleted(teamName, mission.id())) continue;

            String path = "progress." + teamName + "." + mission.id();
            long current = config.getLong(path + ".progress", 0L);
            long newProgress = current + amount;
            config.set(path + ".progress", newProgress);

            if (newProgress >= mission.required()) {
                config.set(path + ".progress", mission.required());
                config.set(path + ".completed", true);

                // Add reward ticks
                long bonus = getBonusTicks(teamName);
                config.set("bonusTicks." + teamName, bonus + mission.rewardTicks());

                // Broadcast to team
                broadcastMissionComplete(teamName, mission);
            }
        }
        save();
    }

    public void checkAndReset(String teamName) {
        String today = LocalDate.now().toString();
        int currentWeek = LocalDate.now().get(WeekFields.ISO.weekOfWeekBasedYear());
        String currentWeekStr = String.valueOf(currentWeek);

        for (Mission mission : missions) {
            String path = "progress." + teamName + "." + mission.id();
            String lastReset = config.getString(path + ".lastReset", "");

            if (mission.daily()) {
                if (!lastReset.equals(today)) {
                    config.set(path + ".progress", 0L);
                    config.set(path + ".completed", false);
                    config.set(path + ".lastReset", today);
                }
            } else {
                if (!lastReset.equals(currentWeekStr)) {
                    config.set(path + ".progress", 0L);
                    config.set(path + ".completed", false);
                    config.set(path + ".lastReset", currentWeekStr);
                }
            }
        }
        save();
    }

    private void broadcastMissionComplete(String teamName, Mission mission) {
        long rewardHours = mission.rewardTicks() / 72_000L;
        String msg = "§6🎯 §e팀 미션 완료! §f" + mission.name()
                + "\n§7보상: §a+" + rewardHours + "시간 §7팀 레벨 경험치";

        TeamManager teamManager = plugin.getTeamManager();
        TeamManager.TeamData teamData = teamManager.getTeam(teamName);
        if (teamData == null) return;

        for (UUID uuid : teamData.members) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(msg);
        }
    }

    private void save() {
        try { config.save(file); } catch (IOException e) {
            plugin.getLogger().warning("team_missions.yml 저장 실패: " + e.getMessage());
        }
    }
}
