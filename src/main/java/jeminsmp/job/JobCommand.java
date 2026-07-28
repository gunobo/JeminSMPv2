package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
            case "buyscroll" -> handleBuyScroll(player);
            case "forceselect" -> handleForceSelect(player, args); // 전직의 서 버튼 전용 (확인 절차 없이 즉시 전직)
            case "admin" -> handleAdmin(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleList(Player player) {
        player.sendMessage("§6§l=== 직업 목록 ===");
        for (JobType t : JobType.values()) {
            player.sendMessage("§7- " + t.icon() + " §e" + t.display() + " §7(§f" + t.name().toLowerCase()
                    + "§7) — " + t.questName() + ": " + t.questAction());
        }
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
            player.sendMessage("§7퀘스트: §f" + d.job.questName() + " §7(" + d.job.questAction() + ") §e"
                    + d.exp + " / " + need);
        }
        for (SkillType t : SkillType.values()) {
            int lvl = d.skillLevel(t);
            player.sendMessage("  §7" + t.display() + ": §f" + lvl + "/" + JobManager.MAX_SKILL_LEVEL);
        }
    }

    private void handleForceSelect(Player player, String[] args) {
        if (args.length < 2) return;
        JobType job = JobType.fromString(args[1]);
        if (job == null) return;
        JobType current = plugin.getJobManager().getJob(player.getUniqueId());
        plugin.getJobManager().selectJob(player.getUniqueId(), job);
        plugin.getJobSkillItemListener().onJobChanged(player);
        if (current == null) {
            player.sendMessage("§a" + job.icon() + " " + job.display() + " §a직업을 선택했습니다!");
        } else {
            player.sendMessage("§a직업을 " + current.display() + " §7→ §a" + job.icon() + " " + job.display()
                    + " §a(으)로 변경했습니다. §7(레벨/스킬 초기화됨)");
        }
    }

    private void handleBuyScroll(Player player) {
        int have = countDiamonds(player);
        if (have < JobItems.JOB_SCROLL_PRICE) {
            player.sendMessage("§c다이아몬드가 부족합니다! §b💎 " + JobItems.JOB_SCROLL_PRICE + "개 필요 §7(보유: " + have + "개)");
            return;
        }
        removeDiamonds(player, JobItems.JOB_SCROLL_PRICE);
        player.getInventory().addItem(JobItems.createJobChangeScroll(plugin));
        player.sendMessage("§a전직의 서 §a구매 완료! §7(💎 " + JobItems.JOB_SCROLL_PRICE + "개 사용, 우클릭하여 사용)");
    }

    private int countDiamonds(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) count += item.getAmount();
        }
        return count;
    }

    private void removeDiamonds(Player player, int amount) {
        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, amount));
    }

    private void handleSelect(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("§c사용법: /job select <광부|농부|전사|어부>"); return; }
        JobType job = JobType.fromString(args[1]);
        if (job == null) { player.sendMessage("§c존재하지 않는 직업입니다. /job list 로 확인하세요."); return; }

        UUID uuid = player.getUniqueId();
        JobType current = plugin.getJobManager().getJob(uuid);

        if (current == null) {
            plugin.getJobManager().selectJob(uuid, job);
            plugin.getJobSkillItemListener().onJobChanged(player);
            player.sendMessage("§a" + job.icon() + " " + job.display() + " §a직업을 선택했습니다!");
            return;
        }

        if (current == job) { player.sendMessage("§7이미 " + job.display() + " 직업입니다."); return; }

        if (pendingReselect.get(uuid) == job) {
            pendingReselect.remove(uuid);
            plugin.getJobManager().selectJob(uuid, job);
            plugin.getJobSkillItemListener().onJobChanged(player);
            player.sendMessage("§a직업을 " + current.display() + " §7→ §a" + job.icon() + " " + job.display()
                    + " §a(으)로 변경했습니다. §7(레벨/스킬 초기화됨)");
        } else {
            pendingReselect.put(uuid, job);
            player.sendMessage("§c전직하면 현재 " + current.display() + " 직업의 레벨·스킬포인트가 §l전부 초기화§r§c됩니다.");
            player.sendMessage("§e정말로 변경하려면 같은 명령어를 §f한 번 더§e 입력하세요: §7/job select " + args[1]);
        }
    }

    // ── 관리자: 경험치 즉시 지급 / 레벨(미션) 즉시 설정 ──

    private void handleAdmin(Player sender, String[] args) {
        if (!sender.isOp()) { sender.sendMessage("§c관리자만 사용할 수 있습니다."); return; }
        if (args.length < 2) { sender.sendMessage("§c사용법: /job admin <give|setlevel> <닉네임> <값>"); return; }
        switch (args[1].toLowerCase()) {
            case "give" -> handleAdminGive(sender, args);
            case "setlevel" -> handleAdminSetLevel(sender, args);
            default -> sender.sendMessage("§c사용법: /job admin <give|setlevel> <닉네임> <값>");
        }
    }

    private void handleAdminGive(Player sender, String[] args) {
        if (args.length < 4) { sender.sendMessage("§c사용법: /job admin give <닉네임> <경험치>"); return; }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { sender.sendMessage("§c접속 중인 플레이어가 아닙니다."); return; }
        long amount;
        try { amount = Long.parseLong(args[3]); } catch (NumberFormatException e) { sender.sendMessage("§c경험치는 숫자로 입력하세요."); return; }

        var d = plugin.getJobManager().getData(target.getUniqueId());
        if (d.job == null) { sender.sendMessage("§c" + target.getName() + "님은 아직 직업이 없습니다."); return; }

        plugin.getJobManager().addExp(target, d.job, amount);
        sender.sendMessage("§a" + target.getName() + "§7님에게 " + d.job.display() + " 경험치 §e" + amount + "§7 지급 완료.");
    }

    private void handleAdminSetLevel(Player sender, String[] args) {
        if (args.length < 4) { sender.sendMessage("§c사용법: /job admin setlevel <닉네임> <레벨>"); return; }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { sender.sendMessage("§c접속 중인 플레이어가 아닙니다."); return; }
        int level;
        try { level = Integer.parseInt(args[3]); } catch (NumberFormatException e) { sender.sendMessage("§c레벨은 숫자로 입력하세요."); return; }

        var d = plugin.getJobManager().getData(target.getUniqueId());
        if (d.job == null) { sender.sendMessage("§c" + target.getName() + "님은 아직 직업이 없습니다."); return; }

        plugin.getJobManager().setLevel(target, level);
        sender.sendMessage("§a" + target.getName() + "§7님의 " + d.job.display() + " 레벨을 §eLv." + d.level + "§7(으)로 설정 완료.");
    }

    private void sendHelp(Player player) {
        player.sendMessage("""
                §6§l=== /job 명령어 ===
                §e/job list §7— 직업 목록
                §e/job info §7— 내 직업 정보
                §e/job select <직업> §7— 직업 선택/변경
                §e/job buyscroll §7— 전직의 서 구매 (💎 """ + JobItems.JOB_SCROLL_PRICE + "개)");
        if (player.isOp()) {
            player.sendMessage("""
                    §7--- 관리자 ---
                    §e/job admin give <닉네임> <경험치> §7— 경험치 즉시 지급
                    §e/job admin setlevel <닉네임> <레벨> §7— 레벨(미션) 즉시 설정""");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("list", "info", "select", "buyscroll"));
            if (sender.isOp()) subs.add("admin");
            return subs;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
            return Arrays.stream(JobType.values()).map(t -> t.name().toLowerCase()).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return sender.isOp() ? List.of("give", "setlevel") : List.of();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            return sender.isOp()
                    ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                    : List.of();
        }
        return new ArrayList<>();
    }
}
