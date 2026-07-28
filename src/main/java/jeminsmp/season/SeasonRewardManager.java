package jeminsmp.season;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SeasonRewardManager {

    private final JeminSMPPlugin plugin;
    private final File rewardFile;
    private final File pendingFile;
    private YamlConfiguration rewardCfg;
    private YamlConfiguration pendingCfg;

    public SeasonRewardManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        this.rewardFile  = new File(plugin.getDataFolder(), "season_rewards.yml");
        this.pendingFile = new File(plugin.getDataFolder(), "season_pending.yml");
        load();
    }

    private void load() {
        rewardCfg  = rewardFile.exists()  ? YamlConfiguration.loadConfiguration(rewardFile)  : new YamlConfiguration();
        pendingCfg = pendingFile.exists() ? YamlConfiguration.loadConfiguration(pendingFile) : new YamlConfiguration();
    }

    // ── 보상 아이템 관리 ──

    public List<ItemStack> getRewards() {
        List<?> raw = rewardCfg.getList("rewards");
        if (raw == null) return new ArrayList<>();
        List<ItemStack> result = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof ItemStack item) result.add(item);
        }
        return result;
    }

    public void addReward(ItemStack item) {
        List<ItemStack> list = getRewards();
        list.add(item.clone());
        rewardCfg.set("rewards", list);
        saveReward();
    }

    public void clearRewards() {
        rewardCfg.set("rewards", null);
        saveReward();
    }

    private void saveReward() {
        try { rewardCfg.save(rewardFile); } catch (IOException e) {
            plugin.getLogger().warning("[SeasonReward] 저장 실패: " + e.getMessage());
        }
    }

    // ── 시즌 시작 시 전체 지급 ──

    /** 온라인 플레이어에게 즉시 지급, 오프라인은 pending에 저장 */
    public void distributeToAll() {
        List<ItemStack> rewards = getRewards();
        if (rewards.isEmpty()) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            giveItems(p, rewards);
            p.sendMessage("§6§l[시즌 보상] §f새 시즌 시작 보상이 지급되었습니다!");
        }

        // 오프라인 플레이어 처리: pending에 저장해 두면 접속 시 지급
        for (UUID uuid : getPendingUuids()) {
            // 이미 pending 있는 경우 덮어쓰지 않고 병합
            storePending(uuid, rewards);
        }
    }

    /** 특정 플레이어에게 보상 지급 (온라인 전용) */
    public void giveRewardsTo(Player player) {
        List<ItemStack> rewards = getRewards();
        if (rewards.isEmpty()) return;
        giveItems(player, rewards);
        player.sendMessage("§6§l[시즌 보상] §f새 시즌 시작 보상이 지급되었습니다!");
    }

    // ── pending (오프라인 플레이어) 관리 ──

    public void storePending(UUID uuid, List<ItemStack> items) {
        List<ItemStack> existing = getPending(uuid);
        existing.addAll(items);
        pendingCfg.set(uuid.toString(), existing);
        savePending();
    }

    public List<ItemStack> getPending(UUID uuid) {
        List<?> raw = pendingCfg.getList(uuid.toString());
        if (raw == null) return new ArrayList<>();
        List<ItemStack> result = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof ItemStack item) result.add(item);
        }
        return result;
    }

    public boolean hasPending(UUID uuid) {
        return pendingCfg.contains(uuid.toString());
    }

    public void clearPending(UUID uuid) {
        pendingCfg.set(uuid.toString(), null);
        savePending();
    }

    /** 접속 시 pending 보상 지급 */
    public boolean givePendingIfAny(Player player) {
        if (!hasPending(player.getUniqueId())) return false;
        List<ItemStack> items = getPending(player.getUniqueId());
        giveItems(player, items);
        clearPending(player.getUniqueId());
        player.sendMessage("§6§l[시즌 보상] §f오프라인 중 지급된 시즌 보상을 받았습니다!");
        return true;
    }

    private Set<UUID> getPendingUuids() {
        Set<UUID> result = new HashSet<>();
        for (String key : pendingCfg.getKeys(false)) {
            try { result.add(UUID.fromString(key)); } catch (Exception ignored) {}
        }
        return result;
    }

    private void savePending() {
        try { pendingCfg.save(pendingFile); } catch (IOException e) {
            plugin.getLogger().warning("[SeasonReward] pending 저장 실패: " + e.getMessage());
        }
    }

    private void giveItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            // 인벤토리가 가득 찬 경우 발밑에 드롭
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }
}
