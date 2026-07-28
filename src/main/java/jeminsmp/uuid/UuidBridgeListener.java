package jeminsmp.uuid;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import jeminsmp.JeminSMPPlugin;
import jeminsmp.kills.KillsManager;
import jeminsmp.team.TeamManager;
import jeminsmp.title.TitleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UuidBridgeListener implements Listener {

    private final JeminSMPPlugin plugin;
    private final OnlineUuidResolver resolver;

    private final ConcurrentHashMap<String, FetchResult> pendingResults = new ConcurrentHashMap<>();

    private record FetchResult(boolean uuidOk, boolean skinOk) {}

    public UuidBridgeListener(JeminSMPPlugin plugin, OnlineUuidResolver resolver) {
        this.plugin = plugin;
        this.resolver = resolver;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (plugin.getServer().getOnlineMode()) return;

        String name = event.getName();
        UUID fakeUUID = event.getUniqueId();

        OnlineUuidResolver.ResolvedProfile resolved = resolver.resolve(name);
        if (resolved == null) {
            plugin.getLogger().warning("[UuidBridge] " + name + " Mojang API 조회 실패 — 오프라인 UUID 사용");
            pendingResults.put(name, new FetchResult(false, false));
            return;
        }

        UUID realUUID = resolved.uuid();
        PlayerProfile profile = event.getPlayerProfile();
        profile.setId(realUUID);

        if (resolved.textureValue() != null && resolved.textureSig() != null) {
            profile.removeProperty("textures");
            profile.setProperty(new ProfileProperty("textures", resolved.textureValue(), resolved.textureSig()));
        }

        event.setPlayerProfile(profile);
        pendingResults.put(name, new FetchResult(true, resolved.textureValue() != null));

        if (!realUUID.equals(fakeUUID)) {
            plugin.getLogger().info("[UuidBridge] " + name + " UUID 교체: " + realUUID
                    + (resolved.textureValue() != null ? " (스킨 O)" : " (스킨 X)"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.getServer().getOnlineMode()) return;

        Player player = event.getPlayer();
        FetchResult result = pendingResults.remove(player.getName());
        if (result == null) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            showBridgeStatus(player, result);
        }, 20L);
    }

    private void showBridgeStatus(Player player, FetchResult result) {
        UUID uuid = player.getUniqueId();

        // 스킨
        String skinLine = result.skinOk()
                ? "§a✔ 스킨§f 적용됨"
                : "§c✘ 스킨§f " + (result.uuidOk() ? "조회 실패" : "UUID 실패로 스킵");

        // 칭호
        TitleManager tm = plugin.getTitleManager();
        String titleLine;
        if (tm != null) {
            Set<String> owned = tm.getOwnedTitles(uuid);
            String equipped = tm.getEquippedDisplay(uuid);
            int count = owned != null ? owned.size() : 0;
            titleLine = "§a✔ 칭호§f " + (equipped != null ? equipped + " §7(" + count + "개 보유)" : "§7없음");
        } else {
            titleLine = "§c✘ 칭호§f 로드 실패";
        }

        // 킬/데스
        KillsManager km = plugin.getKillsManager();
        String killLine;
        if (km != null) {
            KillsManager.PlayerStats stats = km.getStats(uuid);
            if (stats != null) {
                killLine = "§a✔ 전적§f " + stats.kills + "킬 / " + stats.deaths + "데스";
            } else {
                killLine = "§7- 전적§f 기록 없음";
            }
        } else {
            killLine = "§c✘ 전적§f 로드 실패";
        }

        // 플레이타임
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long totalSec = ticks / 20L;
        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        String timeLine = "§a✔ 플레이타임§f " + hours + "시간 " + minutes + "분";

        // 팀
        TeamManager team = plugin.getTeamManager();
        String teamLine;
        if (team != null) {
            var teamData = team.getPlayerTeam(uuid);
            teamLine = teamData != null
                    ? "§a✔ 팀§f " + teamData.name
                    : "§7- 팀§f 없음";
        } else {
            teamLine = "§c✘ 팀§f 로드 실패";
        }

        player.sendMessage(Component.text("§8§l──────────────────────────"));
        player.sendMessage(Component.text("§b[서버 정보] §f불러온 데이터"));
        player.sendMessage(Component.text("  " + skinLine));
        player.sendMessage(Component.text("  " + titleLine));
        player.sendMessage(Component.text("  " + killLine));
        player.sendMessage(Component.text("  " + timeLine));
        player.sendMessage(Component.text("  " + teamLine));
        player.sendMessage(Component.text("§8§l──────────────────────────"));
    }
}
