package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

/** 직업별로 완전히 다르게 구현된 힐/딜/포스 스킬 효과. 쿨다운은 JobManager.cooldownSeconds로 공통. */
public class JobSkills {

    private final JeminSMPPlugin plugin;

    public JobSkills(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void use(Player player, JobType job, SkillType type, int level) {
        switch (job) {
            case MINER -> useMiner(player, type, level);
            case FARMER -> useFarmer(player, type, level);
            case WARRIOR -> useWarrior(player, type, level);
            case FISHER -> useFisher(player, type, level);
        }
    }

    // ── 공용 유틸 ──

    private void heal(Player player, double amount) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? attr.getValue() : 20.0;
        player.setHealth(Math.min(max, player.getHealth() + amount));
    }

    private java.util.List<LivingEntity> nearbyEnemies(Player player, double radius) {
        return player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius).stream()
                .filter(e -> e instanceof LivingEntity && !e.equals(player))
                .map(e -> (LivingEntity) e)
                .toList();
    }

    private LivingEntity nearestEnemy(Player player, double radius) {
        return nearbyEnemies(player, radius).stream()
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    private void effect(LivingEntity target, PotionEffectType type, int seconds, int amplifier) {
        target.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, false, true, true));
    }

    private void sound(Player player, Sound sound, float volume, float pitch) {
        player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
    }

    private void particle(Location loc, Particle particle, int count, double dx, double dy, double dz) {
        loc.getWorld().spawnParticle(particle, loc, count, dx, dy, dz, 0.01);
    }

    private void blockParticle(Location loc, Material material, int count) {
        loc.getWorld().spawnParticle(Particle.BLOCK, loc, count, 0.4, 0.3, 0.4, material.createBlockData());
    }

    // ── ⛏ 광부 ──

    private void useMiner(Player player, SkillType type, int level) {
        switch (type) {
            case HEAL -> {
                double amount = switch (level) { case 1 -> 4; case 2 -> 6; default -> 8; };
                int seconds = switch (level) { case 1 -> 5; case 2 -> 6; default -> 8; };
                heal(player, amount);
                effect(player, PotionEffectType.HASTE, seconds, level >= 3 ? 1 : 0);
                blockParticle(player.getLocation(), Material.STONE, 20);
                particle(player.getLocation().add(0, 1, 0), Particle.HEART, 6, 0.4, 0.3, 0.4);
                sound(player, Sound.BLOCK_STONE_BREAK, 1f, 1.2f);
                player.sendMessage("§b⛏ 대지의 축복! §7체력 " + (int) amount + " 회복 + 채굴 속도 증가.");
            }
            case DEAL -> {
                double dmg = switch (level) { case 1 -> 4; case 2 -> 6; default -> 8; };
                for (LivingEntity e : nearbyEnemies(player, 3.0)) {
                    e.damage(dmg, player);
                    effect(e, PotionEffectType.SLOWNESS, 3, 0);
                    blockParticle(e.getLocation(), Material.COBBLESTONE, 15);
                }
                sound(player, Sound.BLOCK_STONE_BREAK, 1f, 0.7f);
                sound(player, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f);
                player.sendMessage("§6⛏ 낙석! §7주변 적에게 피해와 둔화를 입혔습니다.");
            }
            case FORCE -> {
                int seconds = switch (level) { case 1 -> 3; case 2 -> 4; default -> 5; };
                int amp = level >= 3 ? 1 : 0;
                for (LivingEntity e : nearbyEnemies(player, 4.0)) {
                    effect(e, PotionEffectType.SLOWNESS, seconds, amp + 1);
                    effect(e, PotionEffectType.MINING_FATIGUE, seconds, amp);
                    blockParticle(e.getLocation(), Material.DIRT, 25);
                }
                sound(player, Sound.BLOCK_GRAVEL_BREAK, 1f, 0.6f);
                player.sendMessage("§8⛏ 생매장! §7주변 적을 " + seconds + "초 동안 파묻었습니다.");
            }
        }
    }

    // ── 🌾 농부 ──

    private void useFarmer(Player player, SkillType type, int level) {
        switch (type) {
            case HEAL -> {
                double amount = switch (level) { case 1 -> 4; case 2 -> 6; default -> 8; };
                double radius = switch (level) { case 1 -> 5; case 2 -> 6; default -> 7; };
                Set<UUID> teammates = plugin.getTeamManager().getTeammates(player.getUniqueId());
                heal(player, amount);
                if (level >= 3) effect(player, PotionEffectType.SATURATION, 1, 0);
                int healed = 1;
                particle(player.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 12, 0.6, 0.6, 0.6);
                sound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
                for (LivingEntity e : nearbyEnemies(player, radius)) {
                    if (!(e instanceof Player p) || !teammates.contains(p.getUniqueId())) continue;
                    heal(p, amount);
                    particle(p.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 8, 0.5, 0.5, 0.5);
                    healed++;
                }
                player.sendMessage("§a🌾 수확의 축복! §7팀원 " + healed + "명이 체력 " + (int) amount + " 회복.");
            }
            case DEAL -> {
                int seconds = switch (level) { case 1 -> 3; case 2 -> 4; default -> 5; };
                int amp = level >= 3 ? 1 : 0;
                for (LivingEntity e : nearbyEnemies(player, 3.0)) {
                    effect(e, PotionEffectType.POISON, seconds, amp);
                    particle(e.getLocation().add(0, 1, 0), Particle.WITCH, 10, 0.3, 0.5, 0.3);
                }
                sound(player, Sound.BLOCK_VINE_BREAK, 1f, 0.8f);
                player.sendMessage("§2🌾 가시덩굴! §7주변 적을 중독시켰습니다.");
            }
            case FORCE -> {
                int seconds = switch (level) { case 1 -> 3; case 2 -> 4; default -> 5; };
                int amp = level >= 3 ? 3 : 2;
                for (LivingEntity e : nearbyEnemies(player, 4.0)) {
                    effect(e, PotionEffectType.SLOWNESS, seconds, amp);
                    effect(e, PotionEffectType.WEAKNESS, seconds, 0);
                    particle(e.getLocation(), Particle.WARPED_SPORE, 15, 0.3, 0.6, 0.3);
                }
                sound(player, Sound.BLOCK_VINE_STEP, 1f, 0.6f);
                player.sendMessage("§2🌾 뿌리 속박! §7주변 적을 뿌리로 묶었습니다.");
            }
        }
    }

    // ── ⚔ 전사 ──

    private void useWarrior(Player player, SkillType type, int level) {
        switch (type) {
            case HEAL -> {
                double amount = switch (level) { case 1 -> 4; case 2 -> 6; default -> 8; };
                int seconds = switch (level) { case 1 -> 3; case 2 -> 4; default -> 5; };
                heal(player, amount);
                effect(player, PotionEffectType.RESISTANCE, seconds, 0);
                particle(player.getLocation().add(0, 1, 0), Particle.CRIT, 20, 0.4, 0.5, 0.4);
                sound(player, Sound.ITEM_SHIELD_BLOCK, 1f, 1.1f);
                player.sendMessage("§c⚔ 전투 의지! §7체력 " + (int) amount + " 회복 + 피해 감소.");
            }
            case DEAL -> {
                double dmg = switch (level) { case 1 -> 6; case 2 -> 9; default -> 12; };
                LivingEntity target = nearestEnemy(player, 4.0);
                if (target == null) { player.sendMessage("§c근처에 적이 없습니다."); return; }
                target.damage(dmg, player);
                Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
                if (push.lengthSquared() > 0) push.normalize().multiply(1.2).setY(0.4);
                target.setVelocity(target.getVelocity().add(push));
                particle(target.getLocation().add(0, 1, 0), Particle.SWEEP_ATTACK, 3, 0.3, 0.3, 0.3);
                sound(player, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 1f);
                player.sendMessage("§c⚔ 강타! §7" + (int) dmg + " 피해 + 강한 넉백.");
            }
            case FORCE -> {
                int seconds = switch (level) { case 1 -> 3; case 2 -> 4; default -> 5; };
                int amp = level >= 3 ? 1 : 0;
                for (LivingEntity e : nearbyEnemies(player, 5.0)) {
                    effect(e, PotionEffectType.WEAKNESS, seconds, amp);
                    effect(e, PotionEffectType.SLOWNESS, seconds, 0);
                }
                particle(player.getLocation().add(0, 1, 0), Particle.SWEEP_ATTACK, 6, 1.5, 0.3, 1.5);
                sound(player, Sound.ENTITY_WOLF_GROWL, 1.2f, 0.6f);
                player.sendMessage("§4⚔ 포효! §7넓은 범위의 적을 위축시켰습니다.");
            }
        }
    }

    // ── 🎣 어부 ──

    private void useFisher(Player player, SkillType type, int level) {
        switch (type) {
            case HEAL -> {
                int seconds = switch (level) { case 1 -> 4; case 2 -> 6; default -> 8; };
                int amp = level >= 3 ? 1 : 0;
                effect(player, PotionEffectType.REGENERATION, seconds, amp);
                particle(player.getLocation().add(0, 1, 0), Particle.SPLASH, 15, 0.4, 0.4, 0.4);
                sound(player, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 1f);
                player.sendMessage("§b🎣 만조! §7" + seconds + "초 동안 체력이 서서히 회복됩니다.");
            }
            case DEAL -> {
                double dmg = switch (level) { case 1 -> 5; case 2 -> 7; default -> 9; };
                LivingEntity target = nearestEnemy(player, 6.0);
                if (target == null) { player.sendMessage("§c근처에 적이 없습니다."); return; }
                target.damage(dmg, player);
                Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector());
                if (pull.lengthSquared() > 0) pull.normalize().multiply(0.9).setY(0.2);
                target.setVelocity(target.getVelocity().add(pull));
                particle(target.getLocation().add(0, 1, 0), Particle.CRIT, 10, 0.3, 0.3, 0.3);
                sound(player, Sound.ITEM_TRIDENT_HIT, 1f, 1f);
                player.sendMessage("§3🎣 작살! §7" + (int) dmg + " 피해 + 끌어당김.");
            }
            case FORCE -> {
                int seconds = switch (level) { case 1 -> 2; case 2 -> 3; default -> 4; };
                double power = switch (level) { case 1 -> 1.0; case 2 -> 1.3; default -> 1.6; };
                Location loc = player.getLocation();
                for (LivingEntity e : nearbyEnemies(player, 4.0)) {
                    effect(e, PotionEffectType.SLOWNESS, seconds, 0);
                    Vector push = e.getLocation().toVector().subtract(loc.toVector());
                    if (push.lengthSquared() > 0) push.normalize().multiply(power).setY(0.5);
                    e.setVelocity(e.getVelocity().add(push));
                }
                player.setFireTicks(0);
                particle(loc.add(0, 0.5, 0), Particle.SPLASH, 40, 1.5, 0.5, 1.5);
                sound(player, Sound.ENTITY_GENERIC_SPLASH, 1.2f, 0.8f);
                player.sendMessage("§9🎣 파도! §7주변 적을 크게 밀쳐냈습니다.");
            }
        }
    }
}
