package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JobCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;
    private final Map<UUID, JobType> pendingReselect = new HashMap<>();

    public JobCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("플레이어만 사용할 수 있습니다."); return true; }
        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(player);
            case "info" -> handleInfo(player);
            case "select" -> handleSelect(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleList(Player player) {
        player.sendMessage("§6§l=== 직업 목록 ===");
        for (JobType t : JobType.values()) {
            player.sendMessage("§7- " + t.icon() + " §e" + t.display() + " §7(§f" + t.name().toLowerCase() + "§7)");
        }
        player.sendMessage("§7채굴=광부, 수확=농부, 전투=전사, 낚시=어부 로 경험치를 얻습니다.");
    }

    private void handleInfo(Player player) {
        var d = plugin.getJobManager().getData(player.getUniqueId());
        if (d.job == null) {
            player.sendMessage("§c아직 직업을 선택하지 않았습니다. §e/job select <직업>");
            return;
        }
        long need = JobManager.expForLevel(d.level);
        player.sendMessage("§6§l=== " + d.job.icon() + " " + d.job.display() + " ===");
        player.sendMessage("§7레벨: §fLv." + d.level + " §7(만렙 " + JobManager.MAX_LEVEL + ")");
        if (d.level < JobManager.MAX_LEVEL) {
            player.sendMessage("§7경험치: §f" + d.exp + " / " + need);
        }
        player.sendMessage("§7스킬포인트: §e" + d.skillPoints);
        for (SkillType t : SkillType.values()) {
            int lvl = d.skillLevel(t);
            player.sendMessage("  §7" + t.display() + ": §f" + lvl + "/" + JobManager.MAX_SKILL_LEVEL);
        }
    }

    private void handleSelect(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("§c사용법: /job select <광부|농부|전사|어부>"); return; }
        JobType job = JobType.fromString(args[1]);
        if (job == null) { player.sendMessage("§c존재하지 않는 직업입니다. /job list 로 확인하세요."); return; }

        UUID uuid = player.getUniqueId();
        JobType current = plugin.getJobManager().getJob(uuid);

        if (current == null) {
            plugin.getJobManager().selectJob(uuid, job);
            player.sendMessage("§a" + job.icon() + " " + job.display() + " §a직업을 선택했습니다!");
            return;
        }

        if (current == job) { player.sendMessage("§7이미 " + job.display() + " 직업입니다."); return; }

        if (pendingReselect.get(uuid) == job) {
            pendingReselect.remove(uuid);
            plugin.getJobManager().selectJob(uuid, job);
            player.sendMessage("§a직업을 " + current.display() + " §7→ §a" + job.icon() + " " + job.display()
                    + " §a(으)로 변경했습니다. §7(레벨/스킬 초기화됨)");
        } else {
            pendingReselect.put(uuid, job);
            player.sendMessage("§c전직하면 현재 " + current.display() + " 직업의 레벨·스킬포인트가 §l전부 초기화§r§c됩니다.");
            player.sendMessage("§e정말로 변경하려면 같은 명령어를 §f한 번 더§e 입력하세요: §7/job select " + args[1]);
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("""
                §6§l=== /job 명령어 ===
                §e/job list §7— 직업 목록
                §e/job info §7— 내 직업 정보
                §e/job select <직업> §7— 직업 선택/변경""");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("list", "info", "select");
        if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
            return Arrays.stream(JobType.values()).map(t -> t.name().toLowerCase()).toList();
        }
        return new ArrayList<>();
    }
}
