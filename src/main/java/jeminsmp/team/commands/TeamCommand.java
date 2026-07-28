package jeminsmp.team.commands;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.team.TeamLevelManager;
import jeminsmp.team.TeamManager;
import jeminsmp.team.mission.Mission;
import jeminsmp.team.mission.TeamMissionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;

    private static final Map<String, ChatColor> COLOR_MAP = new LinkedHashMap<>();
    static {
        COLOR_MAP.put("red", ChatColor.RED); COLOR_MAP.put("빨강", ChatColor.RED);
        COLOR_MAP.put("blue", ChatColor.BLUE); COLOR_MAP.put("파랑", ChatColor.BLUE);
        COLOR_MAP.put("green", ChatColor.GREEN); COLOR_MAP.put("초록", ChatColor.GREEN);
        COLOR_MAP.put("yellow", ChatColor.YELLOW); COLOR_MAP.put("노랑", ChatColor.YELLOW);
        COLOR_MAP.put("gold", ChatColor.GOLD); COLOR_MAP.put("금색", ChatColor.GOLD);
        COLOR_MAP.put("aqua", ChatColor.AQUA); COLOR_MAP.put("하늘", ChatColor.AQUA);
        COLOR_MAP.put("pink", ChatColor.LIGHT_PURPLE); COLOR_MAP.put("분홍", ChatColor.LIGHT_PURPLE);
        COLOR_MAP.put("white", ChatColor.WHITE); COLOR_MAP.put("흰색", ChatColor.WHITE);
        COLOR_MAP.put("gray", ChatColor.GRAY); COLOR_MAP.put("회색", ChatColor.GRAY);
        COLOR_MAP.put("dark_green", ChatColor.DARK_GREEN); COLOR_MAP.put("dark_red", ChatColor.DARK_RED);
        COLOR_MAP.put("dark_purple", ChatColor.DARK_PURPLE); COLOR_MAP.put("dark_blue", ChatColor.DARK_BLUE);
        COLOR_MAP.put("dark_aqua", ChatColor.DARK_AQUA);
    }

    public TeamCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("플레이어만 사용할 수 있습니다."); return true; }
        if (args.length == 0) { sendHelp(player); return true; }
        switch (args[0].toLowerCase()) {
            case "create"      -> handleCreate(player, args);
            case "disband"     -> handleDisband(player);
            case "invite"      -> handleInvite(player, args);
            case "accept"      -> handleAccept(player);
            case "deny"        -> handleDeny(player);
            case "leave"       -> handleLeave(player);
            case "kick"        -> handleKick(player, args);
            case "promote"     -> handlePromote(player, args);
            case "color"       -> handleColor(player, args);
            case "friendlyfire", "ff" -> handleFriendlyFire(player);
            case "info"        -> handleInfo(player, args);
            case "list"        -> handleList(player);
            case "level"       -> handleLevel(player);
            case "chest"       -> handleChest(player);
            case "sethome"     -> handleSetHome(player, args);
            case "home"        -> handleHome(player, args);
            case "delhome"     -> handleDelHome(player, args);
            case "homes"       -> handleHomes(player);
            case "tp"          -> handleTp(player, args);
            case "mission", "missions" -> handleMission(player);
            default            -> sendHelp(player);
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(err("사용법: /team create <이름> [색상]")); return; }
        ChatColor color = args.length >= 3 ? COLOR_MAP.getOrDefault(args[2].toLowerCase(), ChatColor.WHITE) : ChatColor.WHITE;
        String error = plugin.getTeamManager().createTeam(args[1], player.getUniqueId(), color);
        if (error != null) { player.sendMessage(err(error)); return; }
        broadcast(plugin.getTeamManager().getPlayerTeam(player.getUniqueId()),
                "🏳 팀 **" + args[1] + "** 이(가) 생성되었습니다! (" + color + "■" + ChatColor.RESET + " " + colorName(color) + ")");
    }

    private void handleDisband(Player player) {
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        String name = team.name;
        broadcastByUUIDs(team.members, "🔴 팀 **" + name + "** 이(가) 해산되었습니다.");
        String error = plugin.getTeamManager().disbandTeam(player.getUniqueId());
        if (error != null) player.sendMessage(err(error));
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(err("사용법: /team invite <플레이어>")); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { player.sendMessage(err("해당 플레이어가 온라인 상태가 아닙니다.")); return; }
        if (target.equals(player)) { player.sendMessage(err("자기 자신을 초대할 수 없습니다.")); return; }
        TeamManager.TeamData myTeam = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (myTeam == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        String error = plugin.getTeamManager().invitePlayer(player.getUniqueId(), target.getUniqueId());
        if (error != null) { player.sendMessage(err(error)); return; }
        player.sendMessage(ok("**" + target.getName() + "** 님에게 초대를 보냈습니다."));
        target.sendMessage(Component.text("📨 ", NamedTextColor.YELLOW)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(" 님이 팀 ", NamedTextColor.YELLOW))
                .append(Component.text("[" + myTeam.name + "]", NamedTextColor.GREEN))
                .append(Component.text(" 에 초대했습니다. /team accept 또는 /team deny", NamedTextColor.YELLOW)));
    }

    private void handleAccept(Player player) {
        String teamName = plugin.getTeamManager().getPendingInvite(player.getUniqueId());
        if (teamName == null) { player.sendMessage(err("받은 초대가 없습니다.")); return; }
        String error = plugin.getTeamManager().acceptInvite(player.getUniqueId());
        if (error != null) { player.sendMessage(err(error)); return; }
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        broadcast(team, "✅ **" + player.getName() + "** 님이 팀에 합류했습니다!");
    }

    private void handleDeny(Player player) {
        String error = plugin.getTeamManager().denyInvite(player.getUniqueId());
        if (error != null) { player.sendMessage(err(error)); return; }
        player.sendMessage(ok("초대를 거절했습니다."));
    }

    private void handleLeave(Player player) {
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        String error = plugin.getTeamManager().leaveTeam(player.getUniqueId());
        if (error != null) { player.sendMessage(err(error)); return; }
        player.sendMessage(ok("팀을 탈퇴했습니다."));
        broadcast(team, "🚪 **" + player.getName() + "** 님이 팀을 탈퇴했습니다.");
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(err("사용법: /team kick <플레이어>")); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        String error = plugin.getTeamManager().kickMember(player.getUniqueId(), target.getUniqueId());
        if (error != null) { player.sendMessage(err(error)); return; }
        broadcast(team, "⛔ **" + args[1] + "** 님이 팀에서 추방되었습니다.");
        Player online = Bukkit.getPlayer(target.getUniqueId());
        if (online != null) online.sendMessage(err("팀에서 추방되었습니다."));
    }

    private void handlePromote(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(err("사용법: /team promote <플레이어>")); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        String error = plugin.getTeamManager().promotePlayer(player.getUniqueId(), target.getUniqueId());
        if (error != null) { player.sendMessage(err(error)); return; }
        broadcast(team, "👑 **" + args[1] + "** 님이 새로운 팀장이 되었습니다.");
    }

    private void handleColor(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(err("사용법: /team color <색상>"));
            player.sendMessage(Component.text("사용 가능: " + String.join(", ", COLOR_MAP.keySet()), NamedTextColor.GRAY));
            return;
        }
        ChatColor color = COLOR_MAP.get(args[1].toLowerCase());
        if (color == null) { player.sendMessage(err("알 수 없는 색상입니다.")); return; }
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        String error = plugin.getTeamManager().setColor(player.getUniqueId(), color);
        if (error != null) { player.sendMessage(err(error)); return; }
        broadcast(team, "🎨 팀 색상이 " + color + "■" + ChatColor.RESET + " " + colorName(color) + " 으로 변경되었습니다.");
    }

    private void handleFriendlyFire(Player player) {
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        String error = plugin.getTeamManager().toggleFriendlyFire(player.getUniqueId());
        if (error != null) { player.sendMessage(err(error)); return; }
        String status = team.friendlyFire ? "§a허용" : "§c비허용";
        broadcast(team, "⚔ 팀킬이 " + status + "§r 으로 변경되었습니다.");
    }

    private void handleInfo(Player player, String[] args) {
        TeamManager.TeamData team;
        if (args.length >= 2) {
            team = plugin.getTeamManager().getTeam(args[1]);
            if (team == null) { player.sendMessage(err("팀을 찾을 수 없습니다: " + args[1])); return; }
        } else {
            team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
            if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        }
        String leaderName = Optional.ofNullable(Bukkit.getOfflinePlayer(team.leader).getName()).orElse("?");
        StringBuilder sb = new StringBuilder();
        for (UUID uuid : team.members) {
            String name = Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName()).orElse("?");
            boolean online = Bukkit.getPlayer(uuid) != null;
            boolean isLeader = uuid.equals(team.leader);
            sb.append("\n  ").append(isLeader ? "👑 " : "• ").append(name).append(online ? " §a(온라인)" : " §7(오프라인)");
        }
        player.sendMessage(Component.text(
                "§6=== 팀: " + team.color + team.name + " §6===\n§7팀장: §f" + leaderName +
                "\n§7색상: " + team.color + "■ " + colorName(team.color) +
                "\n§7팀킬: " + (team.friendlyFire ? "§a허용" : "§c비허용") +
                "\n§7인원 (" + team.members.size() + "명):" + sb));
    }

    private void handleList(Player player) {
        Collection<TeamManager.TeamData> all = plugin.getTeamManager().getAllTeams();
        if (all.isEmpty()) { player.sendMessage(Component.text("등록된 팀이 없습니다.", NamedTextColor.GRAY)); return; }
        StringBuilder sb = new StringBuilder("§6=== 팀 목록 ===");
        for (TeamManager.TeamData t : all) {
            long online = t.members.stream().filter(u -> Bukkit.getPlayer(u) != null).count();
            sb.append("\n  ").append(t.color).append(t.name).append(" §7(").append(online).append("/").append(t.members.size()).append("명 온라인)");
        }
        player.sendMessage(Component.text(sb.toString()));
    }

    private void broadcast(TeamManager.TeamData team, String message) {
        if (team == null) return;
        broadcastByUUIDs(team.members, message);
    }

    private void broadcastByUUIDs(Set<UUID> uuids, String message) {
        Component msg = Component.text(message);
        for (UUID uuid : uuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(msg);
        }
    }

    private boolean isCombatLocked(Player player) {
        if (plugin.getCombatManager().isInCombat(player)) {
            player.sendMessage(err("전투 중에는 팀 이동 명령어를 사용할 수 없습니다!"));
            return true;
        }
        if (plugin.getKillsManager().getCurrentStreak(player.getUniqueId()) > 0) {
            player.sendMessage(err("킬스트릭 중에는 팀 이동 명령어를 사용할 수 없습니다!"));
            return true;
        }
        return false;
    }

    private Component ok(String msg) { return Component.text("✅ " + msg, NamedTextColor.GREEN); }
    private Component err(String msg) { return Component.text("❌ " + msg, NamedTextColor.RED); }

    private String colorName(ChatColor c) {
        return switch (c) {
            case RED -> "빨강"; case BLUE -> "파랑"; case GREEN -> "초록";
            case YELLOW -> "노랑"; case GOLD -> "금색"; case AQUA -> "하늘";
            case LIGHT_PURPLE -> "분홍"; case WHITE -> "흰색"; case GRAY -> "회색";
            case DARK_GREEN -> "진초록"; case DARK_RED -> "진빨강";
            case DARK_PURPLE -> "진보라"; case DARK_BLUE -> "진파랑"; case DARK_AQUA -> "진하늘";
            default -> c.name();
        };
    }

    private void handleLevel(Player player) {
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        TeamLevelManager lm = plugin.getTeamLevelManager();
        int level = lm.getLevel(team.name);
        long totalTicks = plugin.getTeamManager().getTotalTicks(team.name);
        long totalSeconds = totalTicks / 20L;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        StringBuilder sb = new StringBuilder();
        sb.append("§6=== 팀 레벨: ").append(TeamLevelManager.getLevelColor(level)).append("Lv").append(level).append(" §6===\n");
        sb.append("§7총 플레이타임: §f").append(hours).append("시간 ").append(minutes).append("분\n");

        // Progress to next level
        if (level < TeamLevelManager.THRESHOLDS.length - 1) {
            long nextTicks = TeamLevelManager.THRESHOLDS[level + 1];
            long needed = nextTicks - totalTicks;
            long neededSec = needed / 20L;
            long neededH = neededSec / 3600;
            long neededM = (neededSec % 3600) / 60;
            // Progress bar (20 chars)
            long prevTicks = TeamLevelManager.THRESHOLDS[level];
            long range = nextTicks - prevTicks;
            long progress = totalTicks - prevTicks;
            int bars = (int) Math.min(20, (progress * 20) / range);
            String bar = "§a" + "█".repeat(bars) + "§7" + "░".repeat(20 - bars);
            sb.append("§7다음 레벨까지: §f").append(neededH).append("시간 ").append(neededM).append("분\n");
            sb.append("[").append(bar).append("§7] §f").append(progress / 720_000).append("h/").append(range / 720_000).append("h\n");
        } else {
            sb.append("§6§l최고 레벨 달성!\n");
        }

        sb.append("\n§7--- 레벨별 혜택 ---");
        String[] levelReqs = {"0h", "10h", "40h", "100h", "200h", "400h"};
        for (int i = 0; i < TeamLevelManager.THRESHOLDS.length; i++) {
            String check = i <= level ? "§a✅" : "§7🔒";
            String color = TeamLevelManager.getLevelColor(i);
            sb.append("\n").append(check).append(" ").append(color).append("Lv").append(i)
              .append(" §7(").append(levelReqs[i]).append("): §f").append(TeamLevelManager.PERK_NAMES[i]);
        }
        player.sendMessage(Component.text(sb.toString()));
    }

    private void handleChest(Player player) {
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        if (!plugin.getTeamLevelManager().hasLevel(team.name, 1)) {
            int lv = plugin.getTeamLevelManager().getLevel(team.name);
            player.sendMessage(err("팀 레벨 1(10시간) 이상에서 사용할 수 있습니다. 현재: Lv" + lv));
            return;
        }
        plugin.getTeamLevelManager().openChest(player, team.name);
    }

    private void handleSetHome(Player player, String[] args) {
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        int maxHomes = plugin.getTeamLevelManager().getMaxHomes(team.name);
        if (maxHomes == 0) {
            int lv = plugin.getTeamLevelManager().getLevel(team.name);
            player.sendMessage(err("팀 홈 기능은 레벨 2(40시간) 이상에서 사용할 수 있습니다. 현재: Lv" + lv));
            return;
        }
        String homeName = args.length >= 2 ? args[1] : "home";
        Map<String, Location> homes = plugin.getTeamManager().getTeamHomes(team.name);
        if (!homes.containsKey(homeName) && homes.size() >= maxHomes) {
            player.sendMessage(err("팀 홈 슬롯이 가득 찼습니다. (최대 " + maxHomes + "개)"));
            return;
        }
        plugin.getTeamManager().setTeamHome(team.name, homeName, player.getLocation());
        player.sendMessage(ok("팀 홈 §f" + homeName + " §a을(를) 저장했습니다."));
    }

    private void handleHome(Player player, String[] args) {
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        if (isCombatLocked(player)) return;
        if (plugin.getTeamLevelManager().getMaxHomes(team.name) == 0) {
            player.sendMessage(err("팀 홈 기능은 레벨 2(40시간) 이상에서 사용할 수 있습니다."));
            return;
        }
        String homeName = args.length >= 2 ? args[1] : "home";
        Map<String, Location> homes = plugin.getTeamManager().getTeamHomes(team.name);
        Location loc = homes.get(homeName);
        if (loc == null) { player.sendMessage(err("팀 홈 §f" + homeName + " §c을(를) 찾을 수 없습니다.")); return; }
        player.teleport(loc);
        player.sendMessage(ok("팀 홈 §f" + homeName + " §a으로 텔레포트했습니다."));
    }

    private void handleDelHome(Player player, String[] args) {
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        String homeName = args.length >= 2 ? args[1] : "home";
        Map<String, Location> homes = plugin.getTeamManager().getTeamHomes(team.name);
        if (!homes.containsKey(homeName)) { player.sendMessage(err("팀 홈 §f" + homeName + " §c을(를) 찾을 수 없습니다.")); return; }
        plugin.getTeamManager().removeTeamHome(team.name, homeName);
        player.sendMessage(ok("팀 홈 §f" + homeName + " §a을(를) 삭제했습니다."));
    }

    private void handleHomes(Player player) {
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        Map<String, Location> homes = plugin.getTeamManager().getTeamHomes(team.name);
        if (homes.isEmpty()) { player.sendMessage(Component.text("§7저장된 팀 홈이 없습니다.")); return; }
        StringBuilder sb = new StringBuilder("§6=== 팀 홈 목록 ===");
        int maxHomes = plugin.getTeamLevelManager().getMaxHomes(team.name);
        sb.append(" §7(").append(homes.size()).append("/").append(maxHomes).append(")");
        for (Map.Entry<String, Location> entry : homes.entrySet()) {
            Location loc = entry.getValue();
            sb.append("\n  §e").append(entry.getKey()).append(" §7- ")
              .append(loc.getWorld().getName()).append(" (")
              .append((int) loc.getX()).append(", ").append((int) loc.getY()).append(", ").append((int) loc.getZ()).append(")");
        }
        player.sendMessage(Component.text(sb.toString()));
    }

    private void handleTp(Player player, String[] args) {
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }
        if (isCombatLocked(player)) return;
        if (!plugin.getTeamLevelManager().hasLevel(team.name, 5)) {
            int lv = plugin.getTeamLevelManager().getLevel(team.name);
            player.sendMessage(err("팀 텔레포트는 레벨 5(400시간) 이상에서 사용할 수 있습니다. 현재: Lv" + lv));
            return;
        }
        if (args.length < 2) { player.sendMessage(err("사용법: /team tp <닉네임>")); return; }
        String targetName = args[1];
        // Search by display name or player name
        Player target = null;
        for (UUID uuid : team.members) {
            Player member = Bukkit.getPlayer(uuid);
            if (member == null) continue;
            if (member.equals(player)) continue;
            String nick = plugin.getNickManager().getRawNick(uuid);
            String displayName = nick != null ? nick : member.getName();
            if (displayName.equalsIgnoreCase(targetName) || member.getName().equalsIgnoreCase(targetName)) {
                target = member;
                break;
            }
        }
        if (target == null) { player.sendMessage(err("팀원 §f" + targetName + " §c을(를) 찾을 수 없거나 오프라인입니다.")); return; }
        plugin.getTeamLevelManager().startTpWarmup(player, target);
    }

    private void handleMission(Player player) {
        TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage(err("팀에 소속되어 있지 않습니다.")); return; }

        TeamMissionManager mm = plugin.getTeamMissionManager();
        if (mm == null) { player.sendMessage(err("미션 시스템이 비활성화되어 있습니다.")); return; }

        mm.checkAndReset(team.name);

        StringBuilder sb = new StringBuilder("§6=== 팀 미션 ===\n");
        for (Mission mission : mm.getMissions()) {
            long progress = mm.getProgress(team.name, mission.id());
            long required = mission.required();
            boolean completed = mm.isCompleted(team.name, mission.id());
            long rewardHours = mission.rewardTicks() / 72_000L;

            String tag = mission.daily() ? "§e[일일]" : "§b[주간]";
            int bars = (int) Math.min(10, (progress * 10) / Math.max(1, required));
            String bar = "§a" + "█".repeat(bars) + "§8" + "░".repeat(10 - bars);

            if (completed) {
                sb.append(tag).append(" §f").append(mission.name())
                  .append(" §a✅ 완료! §8(+").append(rewardHours).append("h)\n");
            } else {
                sb.append(tag).append(" §f").append(mission.name())
                  .append(" §7").append(progress).append("/").append(required)
                  .append(" §8").append(bar)
                  .append(" §a(+").append(rewardHours).append("h)\n");
            }
        }

        long totalBonusTicks = mm.getBonusTicks(team.name);
        long totalBonusHours = totalBonusTicks / 72_000L;
        sb.append("§7미션 보너스: §a+").append(totalBonusHours).append("시간");

        player.sendMessage(Component.text(sb.toString()));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("""
                §6=== Team 명령어 ===
                §e/team create <이름> [색상] §7- 팀 생성
                §e/team invite <플레이어> §7- 팀원 초대 (팀장)
                §e/team accept §7/ §e/team deny §7- 초대 수락/거절
                §e/team leave §7- 팀 탈퇴
                §e/team kick <플레이어> §7- 팀원 추방 (팀장)
                §e/team promote <플레이어> §7- 팀장 위임
                §e/team color <색상> §7- 네임태그 색상 변경 (팀장)
                §e/team friendlyfire §7- 팀킬 허용 토글 (팀장)
                §e/team disband §7- 팀 해산 (팀장)
                §e/team info [팀이름] §7- 팀 정보
                §e/team list §7- 전체 팀 목록
                §e/tc <메시지> §7또는 §e@<메시지> §7- 팀 채팅
                §b--- 팀 레벨 기능 ---
                §e/team level §7- 팀 레벨 및 혜택 확인
                §e/team chest §7- 공용 상자 (Lv1 이상)
                §e/team sethome [이름] §7- 팀 홈 저장 (Lv2 이상)
                §e/team home [이름] §7- 팀 홈으로 이동 (Lv2 이상)
                §e/team delhome [이름] §7- 팀 홈 삭제 (Lv2 이상)
                §e/team homes §7- 팀 홈 목록 (Lv2 이상)
                §e/team tp <닉네임> §7- 팀원 텔레포트 (Lv5 이상)
                §e/ec §7- 엔더상자 (Lv3 이상)"""));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("create", "invite", "accept", "deny", "leave", "kick", "promote",
                "color", "friendlyfire", "disband", "info", "list",
                "level", "chest", "sethome", "home", "delhome", "homes", "tp", "mission");
        if (args.length == 2 && args[0].equalsIgnoreCase("color")) return new ArrayList<>(COLOR_MAP.keySet());
        if (args.length == 2 && List.of("invite", "kick", "promote").contains(args[0].toLowerCase()))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            if (!(sender instanceof Player player)) return List.of();
            TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
            if (team == null) return List.of();
            List<String> names = new ArrayList<>();
            for (UUID uuid : team.members) {
                Player member = Bukkit.getPlayer(uuid);
                if (member == null || member.equals(player)) continue;
                String nick = plugin.getNickManager().getRawNick(uuid);
                names.add(nick != null ? nick : member.getName());
            }
            return names;
        }
        if (args.length == 2 && List.of("home", "delhome").contains(args[0].toLowerCase())) {
            if (!(sender instanceof Player player)) return List.of();
            TeamManager.TeamData team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
            if (team == null) return List.of();
            return new ArrayList<>(plugin.getTeamManager().getTeamHomes(team.name).keySet());
        }
        return List.of();
    }
}
