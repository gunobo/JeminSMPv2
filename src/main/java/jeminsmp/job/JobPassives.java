package jeminsmp.job;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 직업별 상시 패시브. 스킬(힐/딜/포스/이동기)과 달리 해금 절차 없이 직업만 있으면 기본으로 켜져 있고,
 * 그 직업의 핵심 행동(채굴/수확/처치/낚시)을 할 때마다 발동해서 이펙트와 함께 효과를 줌.
 * 직업 레벨(1~30)에 따라 5단계로 점점 강해짐.
 */
public class JobPassives {

    private JobPassives() {}

    public static int tier(int jobLevel) {
        return Math.max(1, Math.min(5, ((jobLevel - 1) / 6) + 1));
    }

    public static String describe(JobType job, int jobLevel) {
        int tier = tier(jobLevel);
        return switch (job) {
            case MINER -> "광맥의 감각 Lv" + tier + " — 채굴할 때마다 잠깐 채굴 속도 폭증";
            case FARMER -> "풍요의 손길 Lv" + tier + " — 수확할 때마다 체력 회복";
            case WARRIOR -> "전투 본능 Lv" + tier + " — 처치할 때마다 체력 흡수";
            case FISHER -> "심해의 축복 Lv" + tier + " — 낚을 때마다 잠깐 이동 속도 증가";
        };
    }

    // ── 광부: 광맥의 감각 ──
    public static void onMinerOre(Player player, int jobLevel, Location loc) {
        int tier = tier(jobLevel);
        int seconds = 3 + tier;
        int amp = tier - 1;
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, seconds * 20, amp, true, true, true));
        player.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0.5, 0.5, 0.5), 8, 0.25, 0.25, 0.25);
        player.playSound(loc, Sound.BLOCK_STONE_BREAK, 0.6f, 1.6f);
    }

    // ── 농부: 풍요의 손길 ──
    public static void onFarmerHarvest(Player player, int jobLevel, Location loc) {
        int tier = tier(jobLevel);
        heal(player, tier);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 0.5, 0.5), 3 + tier, 0.3, 0.3, 0.3);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.8f);
    }

    // ── 전사: 전투 본능 ──
    public static void onWarriorKill(Player player, int jobLevel) {
        int tier = tier(jobLevel);
        heal(player, 1 + tier);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 2 + tier, 0.3, 0.3, 0.3);
        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.5f, 1.5f);
    }

    // ── 어부: 심해의 축복 ──
    public static void onFisherCatch(Player player, int jobLevel, Location loc) {
        int tier = tier(jobLevel);
        int seconds = 4 + tier;
        int amp = (tier - 1) / 2;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, amp, true, true, true));
        player.getWorld().spawnParticle(Particle.SPLASH, loc, 10 + tier * 2, 0.3, 0.2, 0.3);
        player.playSound(loc, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.6f, 1.3f);
    }

    private static void heal(Player player, double amount) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? attr.getValue() : 20.0;
        player.setHealth(Math.min(max, player.getHealth() + amount));
    }
}
