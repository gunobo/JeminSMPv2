package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

public class SkillCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;

    private static final double FORCE_RADIUS = 4.0;

    public SkillCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
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

        switch (type) {
            case HEAL -> useHeal(player, skillLevel);
            case DEAL -> useDeal(player, skillLevel);
            case FORCE -> useForce(player, skillLevel);
        }

        jm.setCooldown(uuid, type, JobManager.cooldownSeconds(skillLevel));
    }

    private void useHeal(Player player, int level) {
        double amount = switch (level) { case 1 -> 6; case 2 -> 10; default -> 14; };
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        player.setHealth(Math.min(max, player.getHealth() + amount));
        player.sendMessage("§d✚ 힐 사용! §7체력을 " + (int) amount + " 회복했습니다.");
    }

    private void useDeal(Player player, int level) {
        int seconds = switch (level) { case 1 -> 5; case 2 -> 8; default -> 8; };
        int amplifier = level >= 3 ? 1 : 0;
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, seconds * 20, amplifier, false, true, true));
        player.sendMessage("§c⚔ 딜 사용! §7" + seconds + "초 동안 공격력이 증가합니다.");
    }

    private void useForce(Player player, int level) {
        int seconds = switch (level) { case 1 -> 3; case 2 -> 4; default -> 5; };
        Location loc = player.getLocation();
        int hit = 0;
        for (var entity : player.getWorld().getNearbyEntities(loc, FORCE_RADIUS, FORCE_RADIUS, FORCE_RADIUS)) {
            if (entity.equals(player)) continue;
            if (!(entity instanceof LivingEntity target)) continue;
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 1, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, seconds * 20, 0, false, true, true));
            Vector push = target.getLocation().toVector().subtract(loc.toVector());
            if (push.lengthSquared() > 0) {
                push.normalize().multiply(0.8).setY(0.3);
                target.setVelocity(target.getVelocity().add(push));
            }
            hit++;
        }
        player.sendMessage("§b☠ 포스 사용! §7주변 " + hit + "명을 밀치고 둔화시켰습니다.");
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
