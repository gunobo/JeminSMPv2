package jeminsmp.link;

import jeminsmp.JeminSMPPlugin;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 마인크래프트 ↔ 디스코드 계정 연동
 *
 * 연동 흐름:
 * 1. 디스코드에서 !link <마크닉네임> 입력 → 6자리 코드 발급
 * 2. 마인크래프트에서 /link <코드> 입력 → 연동 완료
 * 3. config의 discord.linked-role-id 역할 자동 부여
 */
public class LinkManager {

    private final JeminSMPPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    // 임시 코드 저장: code → discordUserId (5분 만료)
    private final Map<String, PendingLink> pendingCodes = new ConcurrentHashMap<>();

    private record PendingLink(String discordUserId, String discordUsername, long expiresAt) {}

    public LinkManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "links.yml");
        load();
        // 만료 코드 정리 (1분마다)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                () -> pendingCodes.entrySet().removeIf(e -> System.currentTimeMillis() > e.getValue().expiresAt()),
                1200L, 1200L);
    }

    private void load() {
        cfg = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
    }

    /** UUID 마이그레이션 후 파일에서 링크 데이터를 다시 로드 */
    public void reload() {
        load();
    }

    private void save() {
        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().warning("links.yml 저장 실패: " + e.getMessage()); }
    }

    // ── 코드 발급 (디스코드 → 마크 방향) ──
    public String generateCode(String discordUserId, String discordUsername) {
        // 이미 연동되어 있으면 null 반환
        if (getMinecraftUUID(discordUserId) != null) return null;

        // 기존 코드 제거
        pendingCodes.values().removeIf(p -> p.discordUserId().equals(discordUserId));

        String code = String.format("%06d", new Random().nextInt(1_000_000));
        pendingCodes.put(code, new PendingLink(discordUserId, discordUsername,
                System.currentTimeMillis() + 5 * 60 * 1000));
        return code;
    }

    // ── 코드 확인 및 연동 (마인크래프트에서 /link 입력) ──
    public LinkResult confirmLink(Player player, String code) {
        PendingLink pending = pendingCodes.remove(code);
        if (pending == null) return LinkResult.INVALID_CODE;
        if (System.currentTimeMillis() > pending.expiresAt()) return LinkResult.EXPIRED;

        // 이미 다른 계정과 연동된 경우
        String existingDiscord = getDiscordUserId(player.getUniqueId());
        if (existingDiscord != null) return LinkResult.ALREADY_LINKED;

        // 저장
        String uuid = player.getUniqueId().toString();
        cfg.set("mc2dc." + uuid,              pending.discordUserId());
        cfg.set("dc2mc." + pending.discordUserId(), uuid);
        cfg.set("names." + uuid,              player.getName());
        save();

        // 디스코드 역할 부여
        grantRole(pending.discordUserId());

        return LinkResult.SUCCESS;
    }

    // ── 연동 해제 ──
    public boolean unlink(UUID minecraftUUID) {
        String discordId = getDiscordUserId(minecraftUUID);
        if (discordId == null) return false;
        // 역할 해제는 cfg 데이터 삭제 전에 호출 (UUID 조회 필요)
        revokeRole(discordId);
        cfg.set("mc2dc." + minecraftUUID, null);
        cfg.set("dc2mc." + discordId,     null);
        cfg.set("names." + minecraftUUID, null);
        cfg.set("roleId." + minecraftUUID, null);
        save();
        return true;
    }

    public boolean unlinkByDiscord(String discordId) {
        String uuidStr = cfg.getString("dc2mc." + discordId);
        if (uuidStr == null) return false;
        return unlink(UUID.fromString(uuidStr));
    }

    // ── 조회 ──
    public String getDiscordUserId(UUID minecraftUUID) {
        return cfg.getString("mc2dc." + minecraftUUID);
    }

    public UUID getMinecraftUUID(String discordUserId) {
        String uuidStr = cfg.getString("dc2mc." + discordUserId);
        if (uuidStr == null) return null;
        try { return UUID.fromString(uuidStr); } catch (Exception e) { return null; }
    }

    public String getMinecraftName(String discordUserId) {
        UUID uuid = getMinecraftUUID(discordUserId);
        if (uuid == null) return null;
        return cfg.getString("names." + uuid, "알 수 없음");
    }

    public boolean isLinked(UUID minecraftUUID) {
        return cfg.contains("mc2dc." + minecraftUUID);
    }

    // ── 디스코드 역할 부여 (닉네임으로 역할 생성 후 부여) ──
    public void grantRole(String discordUserId) {
        if (plugin.getDiscordBot() == null) return;
        var jda = plugin.getDiscordBot().getJda();
        if (jda == null) return;

        // 마크 닉네임 조회
        UUID uuid = getMinecraftUUID(discordUserId);
        if (uuid == null) return;
        String mcName = cfg.getString("names." + uuid, null);
        if (mcName == null) return;

        for (Guild g : jda.getGuilds()) {
            g.retrieveMemberById(discordUserId).queue(member -> {
                if (member == null) return;

                // 이미 같은 이름의 역할이 있으면 재사용
                Role existing = g.getRolesByName(mcName, false).stream().findFirst().orElse(null);
                if (existing != null) {
                    if (!member.getRoles().contains(existing)) {
                        g.addRoleToMember(member, existing).queue();
                    }
                    // 역할 ID 저장
                    cfg.set("roleId." + uuid, existing.getId());
                    save();
                    return;
                }

                // 역할 새로 생성
                g.createRole().setName(mcName).queue(role -> {
                    g.addRoleToMember(member, role).queue();
                    // 역할 ID 저장 (해제 시 삭제용)
                    cfg.set("roleId." + uuid, role.getId());
                    save();
                }, err -> plugin.getLogger().warning("[Link] 역할 생성 실패: " + err.getMessage()));

            }, err -> {});
        }
    }

    // ── 디스코드 역할 해제 및 삭제 ──
    public void revokeRole(String discordUserId) {
        if (plugin.getDiscordBot() == null) return;
        var jda = plugin.getDiscordBot().getJda();
        if (jda == null) return;

        UUID uuid = getMinecraftUUID(discordUserId);
        if (uuid == null) return;
        String savedRoleId = cfg.getString("roleId." + uuid, null);

        for (Guild g : jda.getGuilds()) {
            g.retrieveMemberById(discordUserId).queue(member -> {
                if (member == null) return;

                // 저장된 역할 ID로 찾아서 삭제
                if (savedRoleId != null) {
                    Role role = g.getRoleById(savedRoleId);
                    if (role != null) {
                        g.removeRoleFromMember(member, role).queue(v ->
                                role.delete().queue(
                                        null,
                                        err -> plugin.getLogger().warning("[Link] 역할 삭제 실패: " + err.getMessage())
                                ), err -> {});
                    }
                }
            }, err -> {});
        }

        // roleId 제거
        if (uuid != null) {
            cfg.set("roleId." + uuid, null);
            save();
        }
    }

    public enum LinkResult { SUCCESS, INVALID_CODE, EXPIRED, ALREADY_LINKED }
}
