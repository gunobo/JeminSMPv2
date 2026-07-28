package jeminsmp.uuid;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class UuidMigrationManager {

    private final JeminSMPPlugin plugin;
    private final File migratedFile;
    private YamlConfiguration migratedCfg;
    private final Set<String> migratedNames = new HashSet<>();

    public UuidMigrationManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        this.migratedFile = new File(plugin.getDataFolder(), "migrated_uuids.yml");
        load();
    }

    private void load() {
        migratedCfg = migratedFile.exists()
                ? YamlConfiguration.loadConfiguration(migratedFile)
                : new YamlConfiguration();
        migratedNames.clear();
        migratedNames.addAll(migratedCfg.getStringList("migrated"));
    }

    public static UUID computeOfflineUUID(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public boolean needsMigration(Player player) {
        UUID offlineUUID = computeOfflineUUID(player.getName());

        // Already in offline mode — UUIDs match, no migration needed
        if (offlineUUID.equals(player.getUniqueId())) return false;

        // Already migrated this player
        if (migratedNames.contains(player.getName())) return false;

        // Check world playerdata file
        for (World world : Bukkit.getWorlds()) {
            File playerdata = new File(world.getWorldFolder(), "playerdata/" + offlineUUID + ".dat");
            if (playerdata.exists()) return true;
        }

        // Check plugin YAML files (e.g. players.yml has offline UUID key)
        File playersYml = new File(plugin.getDataFolder(), "players.yml");
        if (playersYml.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(playersYml);
            if (cfg.contains(offlineUUID.toString())) return true;
        }

        return false;
    }

    public void migrate(Player player) {
        UUID onlineUUID = player.getUniqueId();
        UUID offlineUUID = computeOfflineUUID(player.getName());

        plugin.getLogger().info("[UuidMigration] " + player.getName()
                + " 마이그레이션 시작: " + offlineUUID + " → " + onlineUUID);

        migrateWorldFiles(offlineUUID, onlineUUID);
        migratePluginFiles(offlineUUID, onlineUUID);
        migrateInMemory(offlineUUID, onlineUUID);

        // Mark as done
        migratedNames.add(player.getName());
        migratedCfg.set("migrated", new ArrayList<>(migratedNames));
        try {
            migratedCfg.save(migratedFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[UuidMigration] migrated_uuids.yml 저장 실패: " + e.getMessage());
        }

        plugin.getLogger().info("[UuidMigration] " + player.getName() + " 마이그레이션 완료!");
    }

    /** Public entry point called from PlayerLoginEvent to copy world files early. */
    public void migrateWorldFilesPublic(UUID from, UUID to) {
        migrateWorldFiles(from, to);
    }

    private void migrateWorldFiles(UUID from, UUID to) {
        World world = Bukkit.getWorlds().get(0);
        File worldFolder = world.getWorldFolder();
        for (String subdir : List.of("playerdata", "advancements", "stats")) {
            File dir = new File(worldFolder, subdir);
            String[] exts = subdir.equals("playerdata")
                    ? new String[]{".dat", ".dat_old"}
                    : new String[]{".json"};
            for (String ext : exts) {
                File src = new File(dir, from + ext);
                File dst = new File(dir, to + ext);
                if (!src.exists()) continue;
                if (dst.exists() && dst.length() >= src.length()) continue;
                try {
                    Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("[UuidMigration]   OK " + subdir + "/" + from + ext);
                } catch (IOException e) {
                    plugin.getLogger().warning("[UuidMigration]   FAIL " + subdir + " 복사 실패: " + e.getMessage());
                }
            }
        }
    }

    private void migratePluginFiles(UUID from, UUID to) {
        File dataFolder = plugin.getDataFolder();
        for (String name : List.of(
                "kills.yml", "players.yml", "balance.yml", "nicks.yml",
                "homes.yml", "links.yml", "teams.yml", "compensations.yml",
                "shop.yml", "mailbox.yml", "team_missions.yml",
                "last_will.yml", "letters.yml", "migrated_uuids.yml")) {
            migrateYamlFile(new File(dataFolder, name), from, to);
        }
    }

    private void migrateYamlFile(File file, UUID from, UUID to) {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String fromStr = from.toString();
        String toStr = to.toString();
        boolean changed = false;

        for (String key : new ArrayList<>(cfg.getKeys(false))) {
            // Top-level key is the UUID itself
            if (key.equals(fromStr)) {
                cfg.set(toStr, cfg.get(fromStr));
                cfg.set(fromStr, null);
                changed = true;
            }
            // Top-level key is a section (e.g. "players", "mc2dc", "dc2mc", "names")
            else if (cfg.isConfigurationSection(key)) {
                var section = cfg.getConfigurationSection(key);
                if (section == null) continue;
                for (String subKey : new ArrayList<>(section.getKeys(false))) {
                    if (subKey.equals(fromStr)) {
                        section.set(toStr, section.get(fromStr));
                        section.set(fromStr, null);
                        changed = true;
                    }
                    // String values that contain the UUID (e.g. dc2mc.<discordId> = "<uuid>")
                    else if (section.isString(subKey) && fromStr.equals(section.getString(subKey))) {
                        section.set(subKey, toStr);
                        changed = true;
                    }
                }
            }
            // String list values (e.g. claimed.<eventId> = [uuid1, uuid2])
            else if (cfg.isList(key)) {
                List<String> list = cfg.getStringList(key);
                if (list.contains(fromStr)) {
                    list.replaceAll(s -> s.equals(fromStr) ? toStr : s);
                    cfg.set(key, list);
                    changed = true;
                }
            }
            // Direct string value
            else if (cfg.isString(key) && fromStr.equals(cfg.getString(key))) {
                cfg.set(key, toStr);
                changed = true;
            }
        }

        if (changed) {
            try {
                cfg.save(file);
                plugin.getLogger().info("[UuidMigration]   OK " + file.getName());
            } catch (IOException e) {
                plugin.getLogger().warning("[UuidMigration]   FAIL " + file.getName() + " 저장 실패: " + e.getMessage());
            }
        }
    }

    private void migrateInMemory(UUID from, UUID to) {
        // KillsManager
        if (plugin.getKillsManager() != null) {
            plugin.getKillsManager().migrateUuid(from, to);
        }
        // NickManager
        if (plugin.getNickManager() != null) {
            plugin.getNickManager().migrateUuid(from, to);
        }
        // TitleManager
        if (plugin.getTitleManager() != null) {
            plugin.getTitleManager().migrateUuid(from, to);
        }
        // LinkManager — reload from updated file
        if (plugin.getLinkManager() != null) {
            plugin.getLinkManager().reload();
        }
    }
}
