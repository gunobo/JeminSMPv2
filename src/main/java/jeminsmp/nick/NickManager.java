package jeminsmp.nick;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NickManager {

    private static final int MAX_NICK_LENGTH = 32;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JeminSMPPlugin plugin;
    private final Map<UUID, String> nickMap = new HashMap<>();
    private FileConfiguration nickConfig;
    private File nickFile;

    public NickManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        nickFile = new File(plugin.getDataFolder(), "nicks.yml");
        if (!nickFile.exists()) {
            try { nickFile.createNewFile(); } catch (IOException e) {
                plugin.getLogger().severe("nicks.yml 생성 실패: " + e.getMessage());
            }
        }
        nickConfig = YamlConfiguration.loadConfiguration(nickFile);
        for (String key : nickConfig.getKeys(false)) {
            try { nickMap.put(UUID.fromString(key), nickConfig.getString(key)); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    private void save() {
        try { nickConfig.save(nickFile); } catch (IOException e) {
            plugin.getLogger().severe("nicks.yml 저장 실패: " + e.getMessage());
        }
    }

    public String setNick(Player player, String rawNick) {
        String stripped = rawNick.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
        if (stripped.isBlank()) return "§c닉네임이 비어있습니다.";
        if (stripped.length() > MAX_NICK_LENGTH) return "§c닉네임은 최대 " + MAX_NICK_LENGTH + "자까지 가능합니다.";
        nickMap.put(player.getUniqueId(), rawNick);
        nickConfig.set(player.getUniqueId().toString(), rawNick);
        save();
        applyNick(player);
        return null;
    }

    public void resetNick(Player player) {
        nickMap.remove(player.getUniqueId());
        nickConfig.set(player.getUniqueId().toString(), null);
        save();
        Component realName = Component.text(player.getName());
        player.displayName(realName);
        player.playerListName(realName);
        removeTeamNick(player);
    }

    public void applyNick(Player player) {
        String raw = nickMap.get(player.getUniqueId());
        if (raw == null) return;
        Component nick = LEGACY.deserialize(raw);
        player.displayName(nick);
        player.playerListName(nick);
        applyTeamNick(player, raw);
    }

    // ── 스코어보드 팀으로 머리 위 닉네임 적용 ──
    private void applyTeamNick(Player player, String rawNick) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "nc" + player.getUniqueId().toString().replace("-", "").substring(0, 14);
        Team team = sb.getTeam(teamName);
        if (team == null) team = sb.registerNewTeam(teamName);

        // 색상코드만 적용한 경우(실제 이름과 동일) vs 완전히 다른 닉
        String stripped = rawNick.replaceAll("(?i)[&§][0-9a-fk-or]", "");
        if (stripped.equalsIgnoreCase(player.getName())) {
            // 색상 prefix만 추출 → 머리 위에 색깔 이름 표시
            int nameIdx = rawNick.indexOf(stripped);
            String colorCodes = nameIdx > 0 ? rawNick.substring(0, nameIdx) : "";
            team.prefix(LEGACY.deserialize(colorCodes));
            team.suffix(Component.empty());
        } else {
            // 완전히 다른 닉 → 닉+공백 prefix, 뒤에 실제이름이 따라붙음
            team.prefix(LEGACY.deserialize(rawNick + " §8("));
            team.suffix(LEGACY.deserialize("§8)"));
        }
        team.addEntry(player.getName());
    }

    private void removeTeamNick(Player player) {
        cleanupTeam(player);
    }

    /** 퇴장 시 scoreboard 팀 항목 제거 (NickListener에서 호출) */
    public void cleanupTeam(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "nc" + player.getUniqueId().toString().replace("-", "").substring(0, 14);
        Team team = sb.getTeam(teamName);
        if (team != null) {
            team.removeEntry(player.getName());
            if (team.getEntries().isEmpty()) team.unregister();
        }
    }

    public boolean hasNick(UUID uuid) { return nickMap.containsKey(uuid); }
    public String getRawNick(UUID uuid) { return nickMap.get(uuid); }

    /** UUID 마이그레이션: 오프라인 UUID → 온라인 UUID로 인메모리 닉네임 이전 */
    public void migrateUuid(UUID from, UUID to) {
        String nick = nickMap.remove(from);
        if (nick != null) {
            nickMap.put(to, nick);
        }
    }
}
