package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class SkillCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;
    private final JobSkills skills;

    public SkillCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        this.skills = new JobSkills(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("플레이어만 사용할 수 있습니다."); return true; }
        if (args.length < 2 || !args[0].equalsIgnoreCase("use")) { sendHelp(player); return true; }
        handleUse(player, args[1]);
        return true;
    }

    public void handleUse(Player player, String skillArg) {
        tryUse(player, skillArg, true);
    }

    /** 키 입력(전투/채굴 중 자동 트리거)으로 시도할 때 — 실패해도 채팅 스팸 안 나게 조용히 무시 */
    public void tryUseQuiet(Player player, String skillArg) {
        tryUse(player, skillArg, false);
    }

    private void tryUse(Player player, String skillArg, boolean verbose) {
        SkillType type = SkillType.fromString(skillArg);
        if (type == null) { if (verbose) player.sendMessage("§c존재하지 않는 스킬입니다. (heal|deal|force|move)"); return; }

        JobManager jm = plugin.getJobManager();
        UUID uuid = player.getUniqueId();
        var d = jm.getData(uuid);

        if (d.job == null) { if (verbose) player.sendMessage("§c직업을 먼저 선택하세요. /job select <직업>"); return; }
        int skillLevel = d.skillLevel(type);
        if (skillLevel <= 0) {
            if (verbose) player.sendMessage("§c" + type.display() + " 스킬이 아직 잠겨있습니다. 레벨업하면 자동으로 해금됩니다.");
            return;
        }

        long remaining = jm.getCooldownRemainingSeconds(uuid, type);
        if (remaining > 0) {
            if (verbose) player.sendMessage("§c쿨다운 중입니다. §e" + remaining + "초 §c남음.");
            return;
        }

        skills.use(player, d.job, type, skillLevel);
        int cooldownSeconds = JobManager.cooldownSeconds(skillLevel);
        jm.setCooldown(uuid, type, cooldownSeconds);
        // 아이템에 엔더펄/황금사과 같은 쿨다운 표시(사선 회색) 띄우기
        player.setCooldown(JobItems.cooldownGroupKey(plugin, type), cooldownSeconds * 20);
    }

    private void sendHelp(Player player) {
        player.sendMessage("""
                §6§l=== /skill 명령어 ===
                §e/skill use <heal|deal|force|move> §7— 액티브 스킬 사용
                §7스킬은 직업 레벨업 시 딜→힐→포스 순서로 자동 해금/성장됩니다.""");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("use");
        if (args.length == 2) return List.of("heal", "deal", "force", "move");
        return List.of();
    }
}
