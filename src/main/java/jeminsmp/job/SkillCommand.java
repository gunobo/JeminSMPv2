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
        if (args.length < 2) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "use" -> handleUse(player, args[1]);
            case "invest" -> handleInvest(player, args[1]);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleUse(Player player, String skillArg) {
        SkillType type = SkillType.fromString(skillArg);
        if (type == null) { player.sendMessage("§c존재하지 않는 스킬입니다. (heal|deal|force)"); return; }

        JobManager jm = plugin.getJobManager();
        UUID uuid = player.getUniqueId();
        var d = jm.getData(uuid);

        if (d.job == null) { player.sendMessage("§c직업을 먼저 선택하세요. /job select <직업>"); return; }
        int skillLevel = d.skillLevel(type);
        if (skillLevel <= 0) { player.sendMessage("§c" + type.display() + " 스킬을 아직 배우지 않았습니다. /skill invest " + type.name().toLowerCase()); return; }

        long remaining = jm.getCooldownRemainingSeconds(uuid, type);
        if (remaining > 0) { player.sendMessage("§c쿨다운 중입니다. §e" + remaining + "초 §c남음."); return; }

        skills.use(player, d.job, type, skillLevel);
        jm.setCooldown(uuid, type, JobManager.cooldownSeconds(skillLevel));
    }

    private void handleInvest(Player player, String skillArg) {
        SkillType type = SkillType.fromString(skillArg);
        if (type == null) { player.sendMessage("§c존재하지 않는 스킬입니다. (heal|deal|force)"); return; }
        String error = plugin.getJobManager().invest(player.getUniqueId(), type);
        if (error != null) { player.sendMessage("§c" + error); return; }
        int newLevel = plugin.getJobManager().getData(player.getUniqueId()).skillLevel(type);
        player.sendMessage("§a" + type.display() + " 스킬 §e레벨 " + newLevel + " §a로 강화했습니다!");
    }

    private void sendHelp(Player player) {
        player.sendMessage("""
                §6§l=== /skill 명령어 ===
                §e/skill use <heal|deal|force> §7— 액티브 스킬 사용
                §e/skill invest <heal|deal|force> §7— 스킬포인트 투자 (레벨업)""");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("use", "invest");
        if (args.length == 2) return List.of("heal", "deal", "force");
        return List.of();
    }
}
