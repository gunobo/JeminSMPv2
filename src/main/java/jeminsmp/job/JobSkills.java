package jeminsmp.job;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
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

    /** 직업별로 완전히 다른 스킬 고유 이름 (아이템 표시명 등에 사용) */
    public static String skillName(JobType job, SkillType type) {
        return switch (job) {
            case MINER -> switch (type) {
                case HEAL -> "대지의 축복"; case DEAL -> "낙석"; case FORCE -> "지진"; case MOVE -> "굴착 돌진";
            };
            case FARMER -> switch (type) {
                case HEAL -> "수확의 축복"; case DEAL -> "가시덩굴"; case FORCE -> "뿌리 속박"; case MOVE -> "새싹 도약";
            };
            case WARRIOR -> switch (type) {
                case HEAL -> "전투 의지"; case DEAL -> "돌격"; case FORCE -> "포효"; case MOVE -> "질풍 도약";
            };
            case FISHER -> switch (type) {
                case HEAL -> "만조"; case DEAL -> "작살"; case FORCE -> "파도"; case MOVE -> "갈고리 사출";
            };
        };
    }

    // ── 공용 유틸 ──

    private void heal(Player player, double amount) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? attr.getValue() : 20.0;
        player.setHealth(Math.min(max, player.getHealth() + amount));
    }

    /** 공격/디버프 스킬 대상 풀 — 같은 팀원은 제외 */
    private java.util.List<LivingEntity> nearbyEnemies(Player player, double radius) {
        Set<UUID> teammates = plugin.getTeamManager().getTeammates(player.getUniqueId());
        return player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius).stream()
                .filter(e -> e instanceof LivingEntity && !e.equals(player))
                .filter(e -> !(e instanceof Player p) || !teammates.contains(p.getUniqueId()))
                .map(e -> (LivingEntity) e)
                .toList();
    }

    /** 힐/버프 스킬 대상 풀 — 같은 팀원만 */
    private java.util.List<Player> nearbyTeammates(Player player, double radius) {
        Set<UUID> teammates = plugin.getTeamManager().getTeammates(player.getUniqueId());
        return player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius).stream()
                .filter(e -> e instanceof Player p && teammates.contains(p.getUniqueId()))
                .map(e -> (Player) e)
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

    // 굴착 돌진(광부 이동기)이 뚫으면 안 되는 블록
    private static final Set<Material> TUNNEL_BLOCKED = Set.of(
            Material.BEDROCK, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.RESPAWN_ANCHOR,
            Material.END_PORTAL_FRAME, Material.END_PORTAL, Material.NETHER_PORTAL,
            Material.REINFORCED_DEEPSLATE, Material.BARRIER, Material.WATER, Material.LAVA
    );

    private boolean isTunnelable(org.bukkit.block.Block block) {
        Material type = block.getType();
        if (type.isAir() || !type.isSolid() || TUNNEL_BLOCKED.contains(type)) return false;
        return !(block.getState() instanceof InventoryHolder);
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
                // 발로란트 진탕(콘커스) 느낌: 넉업 + 강한 슬로우 + 화면 뿌옇게(Darkness) + 현기증
                int seconds = switch (level) { case 1 -> 3; case 2 -> 4; default -> 5; };
                double launch = switch (level) { case 1 -> 0.5; case 2 -> 0.7; default -> 0.9; };
                int slowAmp = switch (level) { case 1 -> 1; case 2 -> 2; default -> 3; };
                int hit = 0;
                for (LivingEntity e : nearbyEnemies(player, 4.0)) {
                    Vector vel = e.getVelocity();
                    vel.setY(Math.max(vel.getY(), launch));
                    e.setVelocity(vel);
                    effect(e, PotionEffectType.NAUSEA, seconds, 0);
                    effect(e, PotionEffectType.SLOWNESS, seconds, slowAmp);
                    effect(e, PotionEffectType.DARKNESS, seconds, 0);
                    blockParticle(e.getLocation(), Material.STONE, 20);
                    hit++;
                }
                blockParticle(player.getLocation(), Material.STONE, 30);
                sound(player, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
                player.sendMessage("§8⛏ 지진! §7주변 적 " + hit + "개체를 진탕시켰습니다 (둔화+시야 뿌옇게).");
            }
            case MOVE -> {
                // 굴착 돌진: 바라보는 방향으로 3x3 단면 터널을 뚫으며 돌진
                int length = switch (level) { case 1 -> 3; case 2 -> 5; default -> 7; };
                double dashPower = switch (level) { case 1 -> 1.0; case 2 -> 1.2; default -> 1.4; };

                Vector dir = player.getLocation().getDirection().normalize();

                // 3x3 단면은 월드 축에 맞춰야 대각선 방향에서도 틈 없이 뚫림 (연속 벡터로 계산하면 회전각에 따라 구멍이 생김)
                double ax = Math.abs(dir.getX()), ay = Math.abs(dir.getY()), az = Math.abs(dir.getZ());
                Vector primary, spanA, spanB;
                if (ax >= ay && ax >= az) {
                    primary = new Vector(Math.signum(dir.getX()), 0, 0);
                    spanA = new Vector(0, 1, 0);
                    spanB = new Vector(0, 0, 1);
                } else if (ay >= ax && ay >= az) {
                    primary = new Vector(0, Math.signum(dir.getY()), 0);
                    spanA = new Vector(1, 0, 0);
                    spanB = new Vector(0, 0, 1);
                } else {
                    primary = new Vector(0, 0, Math.signum(dir.getZ()));
                    spanA = new Vector(1, 0, 0);
                    spanB = new Vector(0, 1, 0);
                }

                Location origin = player.getEyeLocation();
                int broken = 0;
                for (int i = 1; i <= length; i++) {
                    Location center = origin.clone().add(primary.clone().multiply(i));
                    for (int a = -1; a <= 1; a++) {
                        for (int b = -1; b <= 1; b++) {
                            Location loc = center.clone().add(spanA.clone().multiply(a)).add(spanB.clone().multiply(b));
                            var block = loc.getBlock();
                            if (isTunnelable(block)) {
                                blockParticle(block.getLocation().add(0.5, 0.5, 0.5), block.getType(), 4);
                                block.setType(Material.AIR);
                                broken++;
                            }
                        }
                    }
                }
                Vector dash = dir.clone().multiply(dashPower);
                dash.setY(Math.max(dash.getY(), 0.2));
                player.setVelocity(player.getVelocity().add(dash));
                sound(player, Sound.BLOCK_STONE_BREAK, 1f, 0.8f);
                sound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
                player.sendMessage("§6⛏ 굴착 돌진! §7블록 " + broken + "개를 뚫으며 돌진.");
            }
        }
    }

    // ── 🌾 농부 ──

    private void useFarmer(Player player, SkillType type, int level) {
        switch (type) {
            case HEAL -> {
                double amount = switch (level) { case 1 -> 4; case 2 -> 6; default -> 8; };
                double radius = switch (level) { case 1 -> 5; case 2 -> 6; default -> 7; };
                heal(player, amount);
                if (level >= 3) effect(player, PotionEffectType.SATURATION, 1, 0);
                int healed = 1;
                particle(player.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 12, 0.6, 0.6, 0.6);
                sound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
                for (Player p : nearbyTeammates(player, radius)) {
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
            case MOVE -> {
                // 새싹 도약: 위+앞으로 크게 도약하며 자신+주변 팀원에게 속도 증가
                double height = switch (level) { case 1 -> 0.7; case 2 -> 0.9; default -> 1.1; };
                double forward = switch (level) { case 1 -> 0.6; case 2 -> 0.8; default -> 1.0; };
                int seconds = switch (level) { case 1 -> 3; case 2 -> 4; default -> 5; };
                int amp = level >= 3 ? 1 : 0;

                Vector dir = player.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
                dir.normalize().multiply(forward);
                dir.setY(height);
                player.setVelocity(dir);

                int boosted = 1;
                effect(player, PotionEffectType.SPEED, seconds, amp);
                for (Player p : nearbyTeammates(player, 3.5)) {
                    effect(p, PotionEffectType.SPEED, seconds, amp);
                    boosted++;
                }
                particle(player.getLocation(), Particle.HAPPY_VILLAGER, 15, 0.4, 0.2, 0.4);
                sound(player, Sound.BLOCK_VINE_STEP, 1f, 1.3f);
                player.sendMessage("§a🌾 새싹 도약! §7팀원 " + boosted + "명에게 속도 증가 + 도약.");
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
                // 돌격: 정면으로 돌진하면서 진행 방향에 걸리는 적 전부 타격 + 자신도 돌진
                // (순간이동은 너무 갑작스러워서 거슬림 -> 부드러운 속도 이동으로, 다만 예전보다 짧게)
                double dmg = switch (level) { case 1 -> 6; case 2 -> 9; default -> 12; };
                double range = switch (level) { case 1 -> 4; case 2 -> 5; default -> 6; };
                double dashPower = switch (level) { case 1 -> 0.8; case 2 -> 0.95; default -> 1.1; };

                Vector dir = player.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
                dir.normalize();

                int hit = 0;
                for (LivingEntity e : nearbyEnemies(player, range)) {
                    Vector toTarget = e.getLocation().toVector().subtract(player.getLocation().toVector());
                    toTarget.setY(0);
                    if (toTarget.lengthSquared() < 0.01) continue;
                    double angle = dir.angle(toTarget.normalize());
                    if (angle > Math.toRadians(45)) continue;
                    e.damage(dmg, player);
                    Vector push = dir.clone().multiply(0.8);
                    push.setY(0.4);
                    e.setVelocity(e.getVelocity().add(push));
                    particle(e.getLocation().add(0, 1, 0), Particle.SWEEP_ATTACK, 3, 0.2, 0.2, 0.2);
                    hit++;
                }

                Vector dash = dir.clone().multiply(dashPower);
                dash.setY(0.25);
                player.setVelocity(player.getVelocity().add(dash));

                sound(player, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 1.2f);
                sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
                player.sendMessage("§c⚔ 돌격! §7전방으로 돌진하며 " + hit + "개체에게 " + (int) dmg + " 피해.");
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
            case MOVE -> {
                // 질풍 도약: 높이 뛰어올랐다가 착지하며 충격파(딜의 수평 돌격과 달리 수직 기동+진입)
                double dmg = switch (level) { case 1 -> 5; case 2 -> 7; default -> 9; };
                double radius = switch (level) { case 1 -> 3; case 2 -> 3.5; default -> 4; };
                double jumpPower = switch (level) { case 1 -> 1.1; case 2 -> 1.3; default -> 1.5; };

                Vector dir = player.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
                dir.normalize().multiply(0.4);
                dir.setY(jumpPower);
                player.setVelocity(dir);
                particle(player.getLocation(), Particle.CLOUD, 20, 0.3, 0.1, 0.3);
                sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.4f);
                player.sendMessage("§c⚔ 질풍 도약! §7높이 뛰어올라 착지하며 충격파를 일으킵니다.");

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    Location loc = player.getLocation();
                    player.setVelocity(new Vector(0, -0.6, 0));
                    blockParticle(loc, Material.COBBLESTONE, 25);
                    sound(player, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);
                    int hit = 0;
                    for (LivingEntity e : nearbyEnemies(player, radius)) {
                        e.damage(dmg, player);
                        Vector push = e.getLocation().toVector().subtract(loc.toVector());
                        if (push.lengthSquared() > 0) push.normalize().multiply(1.0);
                        push.setY(0.5);
                        e.setVelocity(e.getVelocity().add(push));
                        hit++;
                    }
                    if (hit > 0) player.sendMessage("§4⚔ 충격파! §7" + hit + "개체에게 " + (int) dmg + " 피해 + 넉백.");
                }, 14L);
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
                if (target == null) {
                    sound(player, Sound.ITEM_TRIDENT_HIT, 0.6f, 0.7f);
                    player.sendMessage("§c🎣 작살! §7근처에 적이 없어 허공에 던졌습니다.");
                    return;
                }
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
            case MOVE -> {
                // 갈고리 사출: 바라보는 방향의 벽/블록에 걸려 그쪽으로 끌려감 (허공이면 짧게 전진)
                double maxDist = switch (level) { case 1 -> 8; case 2 -> 10; default -> 12; };
                double speed = switch (level) { case 1 -> 1.3; case 2 -> 1.6; default -> 1.9; };

                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection();
                var hit = player.getWorld().rayTraceBlocks(eye, dir, maxDist, FluidCollisionMode.NEVER, true);
                boolean hitWall = hit != null && hit.getHitBlock() != null;

                Vector pull = dir.clone().multiply(speed * (hitWall ? 1.0 : 0.6));
                pull.setY(Math.max(pull.getY(), 0.25));
                player.setVelocity(pull);

                if (hitWall) {
                    particle(hit.getHitPosition().toLocation(player.getWorld()), Particle.SPLASH, 20, 0.2, 0.2, 0.2);
                    player.sendMessage("§3🎣 갈고리 사출! §7벽에 걸려 끌려갑니다.");
                } else {
                    player.sendMessage("§3🎣 갈고리 사출! §7허공에 사출... 앞으로 전진.");
                }
                sound(player, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 1f);
                sound(player, Sound.ITEM_TRIDENT_HIT, 0.7f, 1.3f);
            }
        }
    }
}
