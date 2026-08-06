package jeminsmp.team;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {

    private final JeminSMPPlugin plugin;
    private final File file;
    private FileConfiguration config;

    private final Map<String, TeamData> teams = new LinkedHashMap<>();
    private final Map<UUID, String> playerTeam = new HashMap<>();
    private final Map<UUID, String> pendingInvites = new HashMap<>();
    private final Map<UUID, Long> loginBaseline = new HashMap<>();

    private static final String SB_PREFIX = "jsmp_";
    public static final int MAX_TEAM_SIZE = 6;

    public TeamManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "teams.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        load();
    }

    private void load() {
        config = YamlConfiguration.loadConfiguration(file);
        teams.clear();
        playerTeam.clear();
        ConfigurationSection section = config.getConfigurationSection("teams");
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            String path = "teams." + name;
            try {
                UUID leader = UUID.fromString(Objects.requireNonNull(config.getString(path + ".leader")));
                ChatColor color = ChatColor.valueOf(config.getString(path + ".color", "WHITE"));
                boolean ff = config.getBoolean(path + ".friendlyFire", false);
                Set<UUID> members = new LinkedHashSet<>();
                for (String s : config.getStringList(path + ".members")) {
                    try { members.add(UUID.fromString(s)); } catch (Exception ignored) {}
                }
                TeamData data = new TeamData(name, leader, members, color, ff);
                // Load memberTicks
                ConfigurationSection ticksSec = config.getConfigurationSection(path + ".memberTicks");
                if (ticksSec != null) {
                    for (String uuidStr : ticksSec.getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            data.memberTicks.put(uuid, ticksSec.getLong(uuidStr, 0L));
                        } catch (Exception ignored) {}
                    }
                }
                // Load homes
                ConfigurationSection homesSec = config.getConfigurationSection(path + ".homes");
                if (homesSec != null) {
                    for (String homeName : homesSec.getKeys(false)) {
                        String hp = path + ".homes." + homeName;
                        String worldName = config.getString(hp + ".world");
                        if (worldName == null) continue;
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) continue;
                        double x = config.getDouble(hp + ".x");
                        double y = config.getDouble(hp + ".y");
                        double z = config.getDouble(hp + ".z");
                        float yaw = (float) config.getDouble(hp + ".yaw");
                        float pitch = (float) config.getDouble(hp + ".pitch");
                        data.homes.put(homeName, new Location(world, x, y, z, yaw, pitch));
                    }
                }
                // Load chest
                int chestSize = config.getInt(path + ".chest.size", 0);
                for (int i = 0; i < chestSize; i++) {
                    ItemStack item = config.getItemStack(path + ".chest.item_" + i);
                    data.chest[i] = item;
                }
                teams.put(name.toLowerCase(), data);
                for (UUID uuid : members) playerTeam.put(uuid, name.toLowerCase());
                syncScoreboard(data);
            } catch (Exception ignored) {}
        }
    }

    private void save() {
        config.set("teams", null);
        for (TeamData data : teams.values()) {
            String path = "teams." + data.name;
            config.set(path + ".leader", data.leader.toString());
            config.set(path + ".color", data.color.name());
            config.set(path + ".friendlyFire", data.friendlyFire);
            config.set(path + ".members", data.members.stream().map(UUID::toString).toList());
            // Save memberTicks
            for (Map.Entry<UUID, Long> entry : data.memberTicks.entrySet()) {
                config.set(path + ".memberTicks." + entry.getKey().toString(), entry.getValue());
            }
            // Save homes
            for (Map.Entry<String, Location> entry : data.homes.entrySet()) {
                String hp = path + ".homes." + entry.getKey();
                Location loc = entry.getValue();
                config.set(hp + ".world", loc.getWorld().getName());
                config.set(hp + ".x", loc.getX());
                config.set(hp + ".y", loc.getY());
                config.set(hp + ".z", loc.getZ());
                config.set(hp + ".yaw", (double) loc.getYaw());
                config.set(hp + ".pitch", (double) loc.getPitch());
            }
            // Save chest
            config.set(path + ".chest.size", data.chest.length);
            for (int i = 0; i < data.chest.length; i++) {
                config.set(path + ".chest.item_" + i, data.chest[i]);
            }
        }
        try { config.save(file); } catch (IOException e) {
            plugin.getLogger().severe("teams.yml 저장 실패: " + e.getMessage());
        }
    }

    private void syncScoreboard(TeamData data) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String sbName = SB_PREFIX + data.name.toLowerCase();
        Team sbTeam = board.getTeam(sbName);
        if (sbTeam == null) sbTeam = board.registerNewTeam(sbName);
        sbTeam.setColor(data.color);
        sbTeam.setAllowFriendlyFire(data.friendlyFire);
        sbTeam.setCanSeeFriendlyInvisibles(true);
        for (UUID uuid : data.members) {
            String name = getPlayerName(uuid);
            if (name != null && !sbTeam.hasEntry(name)) sbTeam.addEntry(name);
        }
    }

    private void removeScoreboardEntry(String teamName, UUID uuid) {
        String name = getPlayerName(uuid);
        if (name == null) return;
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team sbTeam = board.getTeam(SB_PREFIX + teamName.toLowerCase());
        if (sbTeam != null) sbTeam.removeEntry(name);
    }

    private void disbandScoreboard(String teamName) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team sbTeam = board.getTeam(SB_PREFIX + teamName.toLowerCase());
        if (sbTeam != null) try { sbTeam.unregister(); } catch (Exception ignored) {}
    }

    private String getPlayerName(UUID uuid) {
        var online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName();
    }

    public String createTeam(String name, UUID leaderUUID, ChatColor color) {
        if (name.length() > 16) return "팀 이름은 16자 이하여야 합니다.";
        if (!name.matches("[a-zA-Z0-9가-힣_]+")) return "팀 이름에 사용할 수 없는 문자가 있습니다.";
        if (teams.containsKey(name.toLowerCase())) return "이미 존재하는 팀 이름입니다.";
        if (playerTeam.containsKey(leaderUUID)) return "이미 팀에 소속되어 있습니다. 먼저 탈퇴하세요.";
        Set<UUID> members = new LinkedHashSet<>();
        members.add(leaderUUID);
        TeamData data = new TeamData(name, leaderUUID, members, color, false);
        teams.put(name.toLowerCase(), data);
        playerTeam.put(leaderUUID, name.toLowerCase());
        syncScoreboard(data);
        save();
        return null;
    }

    public String disbandTeam(UUID requesterUUID) {
        String teamName = playerTeam.get(requesterUUID);
        if (teamName == null) return "팀에 소속되어 있지 않습니다.";
        TeamData data = teams.get(teamName);
        if (!data.leader.equals(requesterUUID)) return "팀장만 팀을 해산할 수 있습니다.";
        for (UUID uuid : data.members) playerTeam.remove(uuid);
        teams.remove(teamName);
        disbandScoreboard(teamName);
        save();
        return null;
    }

    public String invitePlayer(UUID inviterUUID, UUID targetUUID) {
        String teamName = playerTeam.get(inviterUUID);
        if (teamName == null) return "팀에 소속되어 있지 않습니다.";
        TeamData data = teams.get(teamName);
        if (!data.leader.equals(inviterUUID)) return "팀장만 초대할 수 있습니다.";
        if (playerTeam.containsKey(targetUUID)) return "이미 팀에 소속된 플레이어입니다.";
        if (data.members.size() >= MAX_TEAM_SIZE) return "팀 인원이 가득 찼습니다. (최대 " + MAX_TEAM_SIZE + "명)";
        pendingInvites.put(targetUUID, teamName);
        return null;
    }

    public String acceptInvite(UUID playerUUID) {
        String teamName = pendingInvites.remove(playerUUID);
        if (teamName == null) return "받은 팀 초대가 없습니다.";
        if (playerTeam.containsKey(playerUUID)) return "이미 팀에 소속되어 있습니다.";
        TeamData data = teams.get(teamName);
        if (data == null) return "초대받은 팀이 더 이상 존재하지 않습니다.";
        if (data.members.size() >= MAX_TEAM_SIZE) return "팀 인원이 가득 찼습니다. (최대 " + MAX_TEAM_SIZE + "명)";
        data.members.add(playerUUID);
        playerTeam.put(playerUUID, teamName);
        syncScoreboard(data);
        save();
        return null;
    }

    public String denyInvite(UUID playerUUID) {
        if (pendingInvites.remove(playerUUID) == null) return "받은 팀 초대가 없습니다.";
        return null;
    }

    public String leaveTeam(UUID playerUUID) {
        String teamName = playerTeam.get(playerUUID);
        if (teamName == null) return "팀에 소속되어 있지 않습니다.";
        TeamData data = teams.get(teamName);
        if (data.leader.equals(playerUUID)) return "팀장은 /team disband 로 팀을 해산하거나 /team promote 로 팀장을 위임하세요.";
        data.members.remove(playerUUID);
        playerTeam.remove(playerUUID);
        removeScoreboardEntry(teamName, playerUUID);
        save();
        return null;
    }

    public String kickMember(UUID leaderUUID, UUID targetUUID) {
        String teamName = playerTeam.get(leaderUUID);
        if (teamName == null) return "팀에 소속되어 있지 않습니다.";
        TeamData data = teams.get(teamName);
        if (!data.leader.equals(leaderUUID)) return "팀장만 추방할 수 있습니다.";
        if (targetUUID.equals(leaderUUID)) return "자기 자신을 추방할 수 없습니다.";
        if (!data.members.contains(targetUUID)) return "해당 플레이어는 팀원이 아닙니다.";
        data.members.remove(targetUUID);
        playerTeam.remove(targetUUID);
        removeScoreboardEntry(teamName, targetUUID);
        save();
        return null;
    }

    public String promotePlayer(UUID leaderUUID, UUID targetUUID) {
        String teamName = playerTeam.get(leaderUUID);
        if (teamName == null) return "팀에 소속되어 있지 않습니다.";
        TeamData data = teams.get(teamName);
        if (!data.leader.equals(leaderUUID)) return "팀장만 위임할 수 있습니다.";
        if (!data.members.contains(targetUUID)) return "해당 플레이어는 팀원이 아닙니다.";
        if (targetUUID.equals(leaderUUID)) return "이미 팀장입니다.";
        data.leader = targetUUID;
        save();
        return null;
    }

    public String setColor(UUID leaderUUID, ChatColor color) {
        String teamName = playerTeam.get(leaderUUID);
        if (teamName == null) return "팀에 소속되어 있지 않습니다.";
        TeamData data = teams.get(teamName);
        if (!data.leader.equals(leaderUUID)) return "팀장만 색상을 변경할 수 있습니다.";
        data.color = color;
        syncScoreboard(data);
        save();
        return null;
    }

    public String toggleFriendlyFire(UUID leaderUUID) {
        String teamName = playerTeam.get(leaderUUID);
        if (teamName == null) return "팀에 소속되어 있지 않습니다.";
        TeamData data = teams.get(teamName);
        if (!data.leader.equals(leaderUUID)) return "팀장만 변경할 수 있습니다.";
        data.friendlyFire = !data.friendlyFire;
        syncScoreboard(data);
        save();
        return null;
    }

    public void onPlayerJoin(UUID uuid, String playerName) {
        String teamName = playerTeam.get(uuid);
        if (teamName == null) return;
        TeamData data = teams.get(teamName);
        if (data == null) return;
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team sbTeam = board.getTeam(SB_PREFIX + teamName);
        if (sbTeam == null) syncScoreboard(data);
        else if (!sbTeam.hasEntry(playerName)) sbTeam.addEntry(playerName);
    }

    public void recordLogin(Player p) {
        loginBaseline.put(p.getUniqueId(), (long) p.getStatistic(Statistic.PLAY_ONE_MINUTE));
    }

    public void recordQuit(Player p) {
        UUID uuid = p.getUniqueId();
        Long base = loginBaseline.remove(uuid);
        if (base == null) return;
        long current = (long) p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long delta = Math.max(0, current - base);
        String teamName = playerTeam.get(uuid);
        if (teamName == null) return;
        TeamData data = teams.get(teamName);
        if (data == null) return;
        data.memberTicks.merge(uuid, delta, Long::sum);
        save();
    }

    public long getTotalTicks(String teamName) {
        TeamData data = teams.get(teamName.toLowerCase());
        if (data == null) return 0;
        long total = 0;
        for (UUID uuid : data.members) {
            total += data.memberTicks.getOrDefault(uuid, 0L);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                Long base = loginBaseline.get(uuid);
                if (base != null) {
                    long delta = (long) online.getStatistic(Statistic.PLAY_ONE_MINUTE) - base;
                    if (delta > 0) total += delta;
                }
            }
        }
        // Add bonus ticks from team missions
        if (plugin.getTeamMissionManager() != null) {
            total += plugin.getTeamMissionManager().getBonusTicks(teamName);
        }
        return total;
    }

    public void setTeamHome(String teamName, String homeName, Location loc) {
        TeamData data = teams.get(teamName.toLowerCase());
        if (data == null) return;
        data.homes.put(homeName, loc);
        save();
    }

    public void removeTeamHome(String teamName, String homeName) {
        TeamData data = teams.get(teamName.toLowerCase());
        if (data == null) return;
        data.homes.remove(homeName);
        save();
    }

    public Map<String, Location> getTeamHomes(String teamName) {
        TeamData data = teams.get(teamName.toLowerCase());
        if (data == null) return Map.of();
        return Collections.unmodifiableMap(data.homes);
    }

    public void saveChest(String teamName, org.bukkit.inventory.Inventory inventory) {
        TeamData data = teams.get(teamName.toLowerCase());
        if (data == null) return;
        for (int i = 0; i < data.chest.length; i++) {
            data.chest[i] = inventory.getItem(i);
        }
        save();
    }

    public TeamData getTeam(String name) { return teams.get(name.toLowerCase()); }
    public TeamData getPlayerTeam(UUID uuid) {
        String name = playerTeam.get(uuid);
        return name == null ? null : teams.get(name);
    }
    public String getPendingInvite(UUID uuid) { return pendingInvites.get(uuid); }
    public Set<UUID> getTeammates(UUID uuid) {
        TeamData data = getPlayerTeam(uuid);
        return data == null ? Set.of() : data.members;
    }
    public Collection<TeamData> getAllTeams() { return Collections.unmodifiableCollection(teams.values()); }

    public static class TeamData {
        public final String name;
        public UUID leader;
        public final Set<UUID> members;
        public ChatColor color;
        public boolean friendlyFire;
        public final Map<UUID, Long> memberTicks = new HashMap<>();
        public final Map<String, Location> homes = new LinkedHashMap<>();
        public final ItemStack[] chest = new ItemStack[27];

        TeamData(String name, UUID leader, Set<UUID> members, ChatColor color, boolean friendlyFire) {
            this.name = name;
            this.leader = leader;
            this.members = members;
            this.color = color;
            this.friendlyFire = friendlyFire;
        }
    }
}
