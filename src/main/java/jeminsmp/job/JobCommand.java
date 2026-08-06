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
import java.util.List;
import java.util.UUID;

public class JobCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;

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
            case "advance" -> handleAdvance(player);
            case "dropboost" -> handleDropBoost(player);
            case "admin" -> handleAdmin(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleList(Player player) {
        player.sendMessage("§6§l=== 직업 목록 ===");
        for (JobType t : JobType.values()) {
            player.sendMessage("§7- §e" + t.display() + " §7(§f" + t.name().toLowerCase()
                    + "§7) — " + t.questName() + ": " + t.questAction());
        }
    }

    private void handleInfo(Player player) {
        var d = plugin.getJobManager().getData(player.getUniqueId());
        if (d.job == null) {
            player.sendMessage("§c아직 직업을 선택하지 않았습니다. §e/job select <직업>");
            return;
        }
        int tier = plugin.getJobManager().getTier(player.getUniqueId(), d.job);
        int cap = JobManager.levelCapForTier(tier);
        long need = JobManager.expForLevel(d.level);
        player.sendMessage("§6§l=== " + d.job.display() + (tier > 0 ? " §7(" + tier + "차 전직)" : "") + " ===");
        player.sendMessage("§7레벨: §fLv." + d.level + " §7(상한 " + cap + ")");
        if (d.level < cap) {
            player.sendMessage("§7퀘스트: §f" + d.job.questName() + " §7(" + d.job.questAction() + ") §e"
                    + d.exp + " / " + need);
        }
        int skillCap = JobManager.skillCapForTier(tier);
        for (SkillType t : SkillType.values()) {
            int lvl = d.skillLevel(t);
            player.sendMessage("  §7" + t.display() + ": §f" + lvl + "/" + skillCap);
        }
        player.sendMessage("§7패시브: §f" + JobPassives.describe(d.job, d.level));
        if (plugin.getJobManager().hasNextTier(player.getUniqueId())) {
            player.sendMessage("§7" + (tier + 1) + "차 전직: §f" + plugin.getJobManager().advanceProgressText(player.getUniqueId())
                    + " §7(§e/job advance§7)");
        }
        if (tier >= 2) {
            boolean on = plugin.getJobManager().isDoubleYieldActive(player.getUniqueId(), d.job);
            player.sendMessage("§7핵심 드랍 2배: " + (on ? "§a켜짐" : "§c꺼짐") + " §7(§e/job dropboost§7로 전환)");
        }
    }

    // 만렙 + 누적 조건 채우면 전직 — 레벨 상한이 늘어나고 전용 칭호 지급
    private void handleAdvance(Player player) {
        var jm = plugin.getJobManager();
        var d = jm.getData(player.getUniqueId());
        if (d.job == null) { player.sendMessage("§c먼저 직업을 선택하세요."); return; }

        JobType job = d.job;
        int beforeTier = jm.getTier(player.getUniqueId(), job);
        if (!jm.canAdvance(player.getUniqueId())) {
            player.sendMessage("§c아직 전직 조건을 채우지 못했거나, " + job.display() + "은 다음 전직 단계가 없습니다.");
            player.sendMessage("§7" + jm.advanceProgressText(player.getUniqueId()));
            return;
        }

        int newTier = jm.advance(player);
        int oldCap = JobManager.levelCapForTier(beforeTier);
        int newCap = JobManager.levelCapForTier(newTier);
        announceAdvance(player, job, newTier, oldCap, newCap);
        grantAdvanceTitle(player, job, newTier);
    }

    // 전직은 서버 전체가 알 정도로 크게 알림 (채팅 + 타이틀 + 사운드, 전원 대상)
    private void announceAdvance(Player player, JobType job, int tier, int oldCap, int newCap) {
        String bar = "§6§l━━━━━━━━━━━━━━━━━━━━";
        String line1 = "§6§l🎉 " + player.getName() + "§r§e님이 §f" + job.display() + " " + tier + "차 전직§e에 성공했습니다!";
        String line2 = "§7레벨 상한 " + oldCap + " → §f" + newCap;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(bar);
            p.sendMessage(line1);
            p.sendMessage(line2);
            p.sendMessage(bar);
            p.sendTitle("§6§l⚔ 전직!", "§e" + player.getName() + " §7- §f" + job.display() + " " + tier + "차", 10, 70, 20);
            p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_TOTEM_USE, 1f, 1f);
        }

        if (plugin.getDiscordBot() != null && plugin.getConfig().getBoolean("discord.log.job-advance", true)) {
            plugin.getDiscordBot().sendEvent("🎉 **" + player.getName() + "** 님이 **" + job.display() + " " + tier
                    + "차 전직**에 성공했습니다! (레벨 상한 " + oldCap + " → " + newCap + ")");
        }
    }

    // 2차 전직 전용 — 직업별 핵심 드랍 2배 토글
    private void handleDropBoost(Player player) {
        Boolean state = plugin.getJobManager().toggleDoubleYield(player);
        if (state == null) {
            player.sendMessage("§c2차 전직을 완료해야 사용할 수 있습니다.");
            return;
        }
        player.sendMessage(state
                ? "§a⚡ 핵심 드랍 2배를 켰습니다."
                : "§7⚡ 핵심 드랍 2배를 껐습니다.");
    }

    private void grantAdvanceTitle(Player player, JobType job, int tier) {
        var tm = plugin.getTitleManager();
        String id = "job_advance_" + job.name().toLowerCase() + "_t" + tier;
        String display = titleFor(job, tier);
        if (tm.getTitle(id) == null) tm.createTitle(id, 0, display);
        tm.giveTitle(player.getUniqueId(), id);
        player.sendMessage("§6🏅 새 칭호 획득! §f" + display.replace('&', '§')
                + " §7(§e/title equip " + id + "§7로 장착)");
    }

    private String titleFor(JobType job, int tier) {
        if (tier >= 3) {
            return switch (job) {
                case WARRIOR -> "&4&l[⚔ 전쟁의 화신]";
                case MINER -> "&e&l[⛏ 심연의 전설]";
                case FARMER -> "&6&l[🌾 계절의 신]";
                case FISHER -> "&b&l[🎣 심해의 군주]";
            };
        }
        if (tier >= 2) {
            return switch (job) {
                case WARRIOR -> "&4&l[⚔ 백전노장]";
                case MINER -> "&b&l[⛏ 심층의 지배자]";
                case FARMER -> "&a&l[🌾 대지의 지배자]";
                case FISHER -> "&3&l[🎣 파도의 지배자]";
            };
        }
        return switch (job) {
            case MINER -> "&b[⛏ 심층의 거장]";
            case FARMER -> "&a[🌾 대지의 현자]";
            case WARRIOR -> "&c[⚔ 전장의 지배자]";
            case FISHER -> "&3[🎣 심해의 지배자]";
        };
    }

    private void handleForceSelect(Player player, String[] args) {
        if (args.length < 2) return;
        JobType job = JobType.fromString(args[1]);
        if (job == null) return;
        JobType current = plugin.getJobManager().getJob(player.getUniqueId());
        plugin.getJobManager().selectJob(player.getUniqueId(), job);
        plugin.getJobSkillItemListener().onJobChanged(player);
        if (current == null) {
            player.sendMessage("§a" + job.display() + " §a직업을 선택했습니다!");
        } else {
            player.sendMessage("§a직업을 " + current.display() + " §7→ §a" + job.display()
                    + " §a(으)로 변경했습니다. §7(§f" + current.display() + "§7 레벨은 저장되어 나중에 돌아오면 유지됩니다)");
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

    // 최초 선택만 무료. 이미 직업이 있으면 전직의 서(§e/job buyscroll§7)로만 전직 가능
    private void handleSelect(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("§c사용법: /job select <광부|농부|전사|어부>"); return; }
        JobType job = JobType.fromString(args[1]);
        if (job == null) { player.sendMessage("§c존재하지 않는 직업입니다. /job list 로 확인하세요."); return; }

        UUID uuid = player.getUniqueId();
        JobType current = plugin.getJobManager().getJob(uuid);

        if (current == null) {
            plugin.getJobManager().selectJob(uuid, job);
            plugin.getJobSkillItemListener().onJobChanged(player);
            player.sendMessage("§a" + job.display() + " §a직업을 선택했습니다!");
            return;
        }

        if (current == job) { player.sendMessage("§7이미 " + job.display() + " 직업입니다."); return; }

        player.sendMessage("§c이미 " + current.display() + " 직업이 있습니다. 전직하려면 §e전직의 서§c가 필요해요.");
        player.sendMessage("§7/job buyscroll §7(💎 " + JobItems.JOB_SCROLL_PRICE + "개)로 구매 후 우클릭하세요.");
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
                §e/job buyscroll §7— 전직의 서 구매 (💎 """ + JobItems.JOB_SCROLL_PRICE + """
                개)
                §e/job advance §7— 전직 (만렙 + 누적 조건 필요, /job info 에서 진행도 확인)
                §e/job dropboost §7— (2차 전직 전용) 직업별 핵심 드랍 2배 토글""");
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
            List<String> subs = new ArrayList<>(List.of("list", "info", "select", "buyscroll", "advance", "dropboost"));
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
