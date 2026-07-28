package jeminsmp.schedule;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class WaitingManager {

    private final JeminSMPPlugin plugin;

    // LinkedHashMap: 삽입 순서(대기열 순서) 유지
    private final LinkedHashMap<UUID, StoredState> liveQueue = new LinkedHashMap<>();

    private final File dataFile;
    private YamlConfiguration data;
    private World waitingWorld;

    // ── 완전 공허 청크 제너레이터 ──
    private static class VoidGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise()       { return false; }
        @Override public boolean shouldGenerateSurface()     { return false; }
        @Override public boolean shouldGenerateBedrock()     { return false; }
        @Override public boolean shouldGenerateCaves()       { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs()        { return false; }
        @Override public boolean shouldGenerateStructures()  { return false; }
        @Override
        public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
            return Collections.emptyList();
        }
    }

    public WaitingManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "waiting_queue.yml");
        reload();
        initWaitingWorld();
    }

    // ── 대기 월드 초기화 (완전 공허) ──
    private void initWaitingWorld() {
        waitingWorld = Bukkit.getWorld("jeminsmp_waiting");
        if (waitingWorld != null) {
            applyWorldRules(waitingWorld);
            return;
        }

        waitingWorld = new WorldCreator("jeminsmp_waiting")
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                .generator(new VoidGenerator())
                .createWorld();

        if (waitingWorld == null) {
            plugin.getLogger().warning("[WaitingManager] 대기 월드 생성 실패!");
            return;
        }

        applyWorldRules(waitingWorld);
        waitingWorld.setSpawnLocation(0, 64, 0);
        plugin.getLogger().info("[WaitingManager] 공허 대기 월드 생성 완료.");
    }

    @SuppressWarnings({"deprecation", "removal"})
    private void applyWorldRules(World w) {
        w.setTime(6000);
        w.setStorm(false);
        w.setThundering(false);
        w.setDifficulty(Difficulty.PEACEFUL);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,   false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE,    false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING,     false);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setGameRule(GameRule.DO_FIRE_TICK,        false);
        w.setGameRule(GameRule.NATURAL_REGENERATION, false);
        w.setGameRule(GameRule.FALL_DAMAGE,          false);
    }

    // ── 인벤토리 저장 레코드 ──
    public record StoredState(
            ItemStack[] contents,
            ItemStack[] armor,
            ItemStack offhand,
            GameMode   gameMode,
            Location   returnLocation,
            int        level,
            float      exp
    ) {}

    // ── 플레이어 → 대기열 (silent=true 면 "대기 중" 메시지 생략) ──
    public boolean enqueue(Player player) { return enqueue(player, false); }

    public boolean enqueue(Player player, boolean silent) {
        if (player.isOp()) return false;
        if (liveQueue.containsKey(player.getUniqueId())) return false;

        // 이미 대기 월드에 있는 경우 (서버 재시작 후 재접속 등)
        // → 파일에 저장된 상태가 있으면 그걸 그대로 사용 (인벤 덮어쓰기 방지!)
        boolean alreadyWaiting = waitingWorld != null && player.getWorld().equals(waitingWorld);
        if (alreadyWaiting) {
            StoredState existing = loadStateFromFile(player.getUniqueId());
            if (existing != null) {
                liveQueue.put(player.getUniqueId(), existing);
                player.setGameMode(GameMode.SPECTATOR);
                if (!silent) {
                    int pos = liveQueue.size();
                    player.sendMessage(Component.text(
                            "⏳ 지금은 접속할 수 없는 시간입니다. (대기열 " + pos + "번)",
                            NamedTextColor.YELLOW));
                    player.sendMessage(Component.text(
                            "   서버 오픈 시 순서대로 입장됩니다.", NamedTextColor.GRAY));
                }
                return true;
            }
        }

        Location returnLoc = alreadyWaiting
                ? getReturnLocationFromFile(player.getUniqueId())
                : player.getLocation().clone();
        if (returnLoc == null) returnLoc = player.getLocation().clone();

        // 게임모드: SPECTATOR면 SURVIVAL 기본값
        GameMode originalMode = player.getGameMode();
        if (originalMode == GameMode.SPECTATOR) {
            originalMode = GameMode.SURVIVAL;
        }

        StoredState state = new StoredState(
                deepClone(player.getInventory().getContents()),
                deepClone(player.getInventory().getArmorContents()),
                player.getInventory().getItemInOffHand().clone(),
                originalMode,
                returnLoc,
                player.getLevel(),
                player.getExp()
        );
        liveQueue.put(player.getUniqueId(), state);
        saveStateToFile(player.getUniqueId(), state);

        // 인벤토리·레벨 비우기
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        player.setLevel(0);
        player.setExp(0f);

        // 관전 모드 + 대기 월드로 이동
        player.setGameMode(GameMode.SPECTATOR);
        if (waitingWorld != null) {
            player.teleport(waitingWorld.getSpawnLocation());
        }

        if (!silent) {
            int pos = liveQueue.size();
            player.sendMessage(Component.text(
                    "⏳ 지금은 접속할 수 없는 시간입니다. (대기열 " + pos + "번)",
                    NamedTextColor.YELLOW));
            player.sendMessage(Component.text(
                    "   서버 오픈 시 순서대로 입장됩니다.", NamedTextColor.GRAY));
        }
        return true;
    }

    // ── 대기열 → 게임 복귀 ──
    public boolean release(Player player) { return release(player, false); }

    public boolean release(Player player, boolean silent) {
        StoredState state = liveQueue.remove(player.getUniqueId());
        if (state == null) state = loadStateFromFile(player.getUniqueId());
        if (state == null) return false;

        removeStateFromFile(player.getUniqueId());

        player.getInventory().setContents(state.contents());
        player.getInventory().setArmorContents(state.armor());
        player.getInventory().setItemInOffHand(state.offhand());
        player.setGameMode(state.gameMode());
        player.setLevel(state.level());
        player.setExp(state.exp());

        Location ret = state.returnLocation();
        if (ret != null && ret.getWorld() != null) {
            player.teleport(ret);
        } else {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }

        if (!silent) {
            player.sendMessage(Component.text(
                    "🟢 서버에 입장합니다! 즐거운 게임 되세요!", NamedTextColor.GREEN));
        }
        return true;
    }

    // ── 서버 닫힐 때 전원 대기열 ──
    public void enqueueAll() {
        boolean any = false;
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (!p.isOp() && !liveQueue.containsKey(p.getUniqueId())) {
                enqueue(p);
                any = true;
            }
        }
        if (any) Bukkit.broadcast(Component.text(
                "⏳ 서버 운영 시간이 종료되었습니다. 다음 오픈 시간까지 대기 상태로 유지됩니다.",
                NamedTextColor.YELLOW));
    }

    // ── 서버 열릴 때: 대기열 순서대로 입장 (징계 플레이어 제외) ──
    public void releaseAll() {
        var warnManager = plugin.getWarnManager();
        List<Player> ordered = new ArrayList<>();
        for (UUID uuid : liveQueue.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            // 징계 중인 플레이어는 서버 열려도 대기열 유지
            if (warnManager != null && warnManager.isPunished(uuid)) {
                p.sendMessage(Component.text(
                        "⛔ 징계 중이어서 입장할 수 없습니다. 관리자에게 문의하세요.",
                        NamedTextColor.RED));
                continue;
            }
            ordered.add(p);
        }

        if (ordered.isEmpty()) return;

        int total = ordered.size();
        Bukkit.broadcast(Component.text(
                "🟢 서버가 오픈되었습니다! 대기열 " + total + "명이 순서대로 입장합니다.",
                NamedTextColor.GREEN));

        for (int i = 0; i < ordered.size(); i++) {
            Player p = ordered.get(i);
            long delayTicks = (long) i * 40L; // 2초 간격
            int  waitSecs   = i * 2;

            if (waitSecs == 0) {
                p.sendMessage(Component.text(
                        "🟢 1번! 바로 입장합니다.", NamedTextColor.GREEN));
            } else {
                p.sendMessage(Component.text(
                        "🟢 대기열 " + (i + 1) + "번 · " + waitSecs + "초 후 입장합니다.",
                        NamedTextColor.GREEN));
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) release(p);
            }, Math.max(1L, delayTicks));
        }
    }

    public void onQuit(UUID uuid) { liveQueue.remove(uuid); }

    public void onJoin(Player player, boolean serverOpen) {
        if (player.isOp()) return;

        // 징계 중이면 항상 대기열
        var warnManager = plugin.getWarnManager();
        if (warnManager != null && warnManager.isPunished(player.getUniqueId())) {
            warnManager.enqueuePunished(player);
            return;
        }

        if (serverOpen) {
            // 서버가 열려있어도 대기열을 거쳐서 입장
            // → 아이템·게임모드·레벨 유지, 대기 월드 경유 후 즉시 복귀
            boolean queued = enqueue(player, true); // 서버 열려있을 땐 대기 메시지 생략
            if (queued) {
                // 1틱 후 즉시 release (스펙테이터 잠깐 거쳤다가 바로 복귀)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) release(player, true);
                }, 1L);
            } else if (hasPendingRestore(player.getUniqueId())) {
                // 이미 대기 월드에 있던 경우 (서버 재시작 후) → 바로 복귀
                release(player, true);
            }
        } else {
            enqueue(player);
        }
    }

    public boolean isQueued(UUID uuid)          { return liveQueue.containsKey(uuid); }
    public boolean hasPendingRestore(UUID uuid) { return data.contains(uuid.toString()); }
    public World   getWaitingWorld()            { return waitingWorld; }

    /** 대기열 순서 목록 (tick 알림용) */
    public List<UUID> getQueueOrder() {
        return new ArrayList<>(liveQueue.keySet());
    }

    // ── 파일 저장/로드 ──
    private void reload() {
        data = dataFile.exists()
                ? YamlConfiguration.loadConfiguration(dataFile)
                : new YamlConfiguration();
    }

    private void saveFile() {
        try { data.save(dataFile); }
        catch (IOException e) {
            plugin.getLogger().warning("waiting_queue.yml 저장 실패: " + e.getMessage());
        }
    }

    private void saveStateToFile(UUID uuid, StoredState state) {
        String b = uuid.toString();
        data.set(b + ".gamemode", state.gameMode().name());

        data.set(b + ".inv", null);
        for (int i = 0; i < state.contents().length; i++) {
            ItemStack item = state.contents()[i];
            if (item != null && item.getType() != Material.AIR)
                data.set(b + ".inv." + i, item);
        }
        data.set(b + ".armor", null);
        for (int i = 0; i < state.armor().length; i++) {
            ItemStack item = state.armor()[i];
            if (item != null && item.getType() != Material.AIR)
                data.set(b + ".armor." + i, item);
        }
        ItemStack off = state.offhand();
        data.set(b + ".offhand", (off != null && off.getType() != Material.AIR) ? off : null);

        data.set(b + ".level", state.level());
        data.set(b + ".exp",   (double) state.exp());

        Location loc = state.returnLocation();
        if (loc != null && loc.getWorld() != null) {
            data.set(b + ".loc.world", loc.getWorld().getName());
            data.set(b + ".loc.x",     loc.getX());
            data.set(b + ".loc.y",     loc.getY());
            data.set(b + ".loc.z",     loc.getZ());
            data.set(b + ".loc.yaw",   (double) loc.getYaw());
            data.set(b + ".loc.pitch", (double) loc.getPitch());
        }
        saveFile();
    }

    private StoredState loadStateFromFile(UUID uuid) {
        String b = uuid.toString();
        if (!data.contains(b)) return null;

        GameMode gm;
        try { gm = GameMode.valueOf(data.getString(b + ".gamemode", "SURVIVAL")); }
        catch (Exception e) { gm = GameMode.SURVIVAL; }

        ItemStack[] contents = loadItemArray(b + ".inv",   36);
        ItemStack[] armor    = loadItemArray(b + ".armor",  4);
        ItemStack offhand = data.getItemStack(b + ".offhand");
        if (offhand == null) offhand = new ItemStack(Material.AIR);

        Location loc = null;
        String locWorldName = data.getString(b + ".loc.world");
        if (locWorldName != null) {
            World w = Bukkit.getWorld(locWorldName);
            if (w == null) w = Bukkit.getWorlds().get(0);
            loc = new Location(w,
                    data.getDouble(b + ".loc.x"),
                    data.getDouble(b + ".loc.y"),
                    data.getDouble(b + ".loc.z"),
                    (float) data.getDouble(b + ".loc.yaw"),
                    (float) data.getDouble(b + ".loc.pitch"));
        }
        int   level = data.getInt(b + ".level", 0);
        float exp   = (float) data.getDouble(b + ".exp", 0.0);
        return new StoredState(contents, armor, offhand, gm, loc, level, exp);
    }

    private Location getReturnLocationFromFile(UUID uuid) {
        String b = uuid + ".loc";
        String wName = data.getString(b + ".world");
        if (wName == null) return null;
        World w = Bukkit.getWorld(wName);
        if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w,
                data.getDouble(b + ".x"), data.getDouble(b + ".y"), data.getDouble(b + ".z"),
                (float) data.getDouble(b + ".yaw"), (float) data.getDouble(b + ".pitch"));
    }

    private void removeStateFromFile(UUID uuid) {
        data.set(uuid.toString(), null);
        saveFile();
    }

    private ItemStack[] loadItemArray(String path, int size) {
        ItemStack[] arr = new ItemStack[size];
        ConfigurationSection sec = data.getConfigurationSection(path);
        if (sec == null) return arr;
        for (String key : sec.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                if (slot >= 0 && slot < size)
                    arr[slot] = data.getItemStack(path + "." + key);
            } catch (NumberFormatException ignored) {}
        }
        return arr;
    }

    private static ItemStack[] deepClone(ItemStack[] src) {
        ItemStack[] copy = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++)
            copy[i] = src[i] != null ? src[i].clone() : null;
        return copy;
    }
}
