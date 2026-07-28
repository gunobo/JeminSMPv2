package jeminsmp.warn;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class WarnManager {

    private final JeminSMPPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public record Warning(String reason, String issuer, String date) {}

    public WarnManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warnings.yml");
        load();
    }

    private void load() {
        cfg = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
    }

    private void save() {
        try { cfg.save(file); }
        catch (IOException e) {
            plugin.getLogger().warning("warnings.yml 저장 실패: " + e.getMessage());
        }
    }

    private String wkey(UUID uuid) { return "players." + uuid; }

    // ── 경고 목록 ──
    public List<Warning> getWarnings(UUID uuid) {
        List<Warning> list = new ArrayList<>();
        for (var rawMap : cfg.getMapList(wkey(uuid) + ".warnings")) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) rawMap;
            list.add(new Warning(
                    String.valueOf(map.getOrDefault("reason", "사유 없음")),
                    String.valueOf(map.getOrDefault("issuer", "알 수 없음")),
                    String.valueOf(map.getOrDefault("date",   ""))
            ));
        }
        return list;
    }

    // ── 경고 추가 → 임계치 도달 시 자동 징계 ──
    public int addWarning(UUID target, String reason, String issuerName) {
        List<Map<?, ?>> warnings = new ArrayList<>(cfg.getMapList(wkey(target) + ".warnings"));
        Map<String, String> w = new LinkedHashMap<>();
        w.put("reason", reason);
        w.put("issuer", issuerName);
        w.put("date",   LocalDateTime.now().format(DATE_FMT));
        warnings.add(w);
        cfg.set(wkey(target) + ".warnings", warnings);
        save();

        int count      = warnings.size();
        int threshold  = plugin.getConfig().getInt("warn.auto-punish-count", 3);

        if (count >= threshold && !isPunished(target)) {
            setPunished(target, true, false); // 자동 징계 (해제 없이 enqueue)
            Player p = Bukkit.getPlayer(target);
            if (p != null && p.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> enqueuePunished(p));
            }
        }
        return count;
    }

    // ── 경고 제거 ──
    public boolean removeWarning(UUID target, int index) {
        List<Map<?, ?>> warnings = new ArrayList<>(cfg.getMapList(wkey(target) + ".warnings"));
        if (index < 1 || index > warnings.size()) return false;
        warnings.remove(index - 1);
        cfg.set(wkey(target) + ".warnings", warnings);
        save();
        return true;
    }

    public void clearWarnings(UUID uuid) {
        cfg.set(wkey(uuid) + ".warnings", new ArrayList<>());
        save();
    }

    // ── 징계 상태 ──
    public boolean isPunished(UUID uuid) {
        return cfg.getBoolean(wkey(uuid) + ".punished", false);
    }

    /**
     * @param release true 이면 징계 해제 시 대기열에서도 풀어줌
     */
    public void setPunished(UUID uuid, boolean punished, boolean release) {
        cfg.set(wkey(uuid) + ".punished", punished);
        save();

        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return;

        if (punished) {
            Bukkit.getScheduler().runTask(plugin, () -> enqueuePunished(p));
        } else if (release) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                var wm = plugin.getScheduleManager().getWaitingManager();
                if (wm.isQueued(uuid)) {
                    wm.release(p);
                    p.sendMessage(Component.text(
                            "✅ 징계가 해제되었습니다. 서버에 입장합니다!", NamedTextColor.GREEN));
                }
            });
        }
    }

    // ── 징계 대기열 입장 ──
    public void enqueuePunished(Player player) {
        var wm = plugin.getScheduleManager().getWaitingManager();
        wm.enqueue(player); // 기존 enqueue 활용
        player.sendMessage(Component.text(
                "⛔ 징계로 인해 대기 상태로 유지됩니다. 관리자에게 문의하세요.",
                NamedTextColor.RED));
    }

    // ── 이름으로 UUID 조회 ──
    public UUID findUUID(String name) {
        // 온라인 우선
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p.getUniqueId();
        // 오프라인 검색
        return Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(op -> name.equalsIgnoreCase(op.getName()))
                .map(op -> op.getUniqueId())
                .findFirst().orElse(null);
    }

    public String formatWarnings(UUID uuid, String playerName) {
        List<Warning> list = getWarnings(uuid);
        boolean punished = isPunished(uuid);
        int threshold = plugin.getConfig().getInt("warn.auto-punish-count", 3);

        StringBuilder sb = new StringBuilder();
        sb.append("**⚠ ").append(playerName).append(" 경고 목록**");
        sb.append(" (").append(list.size()).append("/").append(threshold).append(")");
        if (punished) sb.append(" 🔴징계중");
        sb.append("\n");

        if (list.isEmpty()) {
            sb.append("경고 없음");
        } else {
            for (int i = 0; i < list.size(); i++) {
                Warning w = list.get(i);
                sb.append("`").append(i + 1).append(".` ")
                  .append(w.reason())
                  .append("  — ").append(w.issuer())
                  .append(" (").append(w.date()).append(")\n");
            }
        }
        return sb.toString().trim();
    }
}
