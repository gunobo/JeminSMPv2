package jeminsmp.season;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SeasonManager {

    private final JeminSMPPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    private int currentSeason;
    private String currentName;
    private long startedAt;

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

    public SeasonManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "season.yml");
        load();
    }

    private void load() {
        cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        currentSeason = cfg.getInt("current.number", 0);
        currentName   = cfg.getString("current.name", "");
        startedAt     = cfg.getLong("current.startedAt", 0);
    }

    public boolean isActive()        { return currentSeason > 0; }
    public int getCurrentSeason()    { return currentSeason; }
    public String getCurrentName()   { return currentName; }
    public long getStartedAt()       { return startedAt; }

    /** 새 시즌 시작. 진행 중이면 먼저 종료 후 시작. */
    public int startSeason(String name) {
        if (isActive()) archiveAndEnd();

        currentSeason++;
        currentName = (name == null || name.isBlank()) ? "시즌 " + currentSeason : name;
        startedAt   = System.currentTimeMillis();

        cfg.set("current.number",    currentSeason);
        cfg.set("current.name",      currentName);
        cfg.set("current.startedAt", startedAt);
        cfg.set("current.endedAt",   (Object) null);
        save();

        plugin.getLogger().info("[Season] " + currentName + " 시작!");
        return currentSeason;
    }

    /** 현재 시즌 종료. kills.yml 스냅샷 → seasons/season_N.yml 보관. */
    public void endSeason() {
        if (!isActive()) return;
        archiveAndEnd();
    }

    private void archiveAndEnd() {
        long endedAt = System.currentTimeMillis();

        // kills.yml → seasons/season_N.yml 복사
        File killsFile = new File(plugin.getDataFolder(), "kills.yml");
        if (killsFile.exists()) {
            File seasonsDir = new File(plugin.getDataFolder(), "seasons");
            seasonsDir.mkdirs();
            File archive = new File(seasonsDir, "season_" + currentSeason + ".yml");
            try {
                Files.copy(killsFile.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("[Season] 아카이브 저장: " + archive.getName());
            } catch (IOException e) {
                plugin.getLogger().warning("[Season] 아카이브 저장 실패: " + e.getMessage());
            }
        }

        // history 기록
        String base = "history." + currentSeason;
        cfg.set(base + ".name",      currentName);
        cfg.set(base + ".startedAt", startedAt);
        cfg.set(base + ".endedAt",   endedAt);

        plugin.getLogger().info("[Season] " + currentName + " 종료");

        // current 초기화
        currentSeason = 0;
        currentName   = "";
        startedAt     = 0;
        cfg.set("current.number",    0);
        cfg.set("current.name",      "");
        cfg.set("current.startedAt", 0L);
        cfg.set("current.endedAt",   (Object) null);
        save();
    }

    /** 특정 시즌 아카이브 데이터 반환 */
    public YamlConfiguration getArchivedStats(int season) {
        File archive = new File(plugin.getDataFolder(), "seasons/season_" + season + ".yml");
        return archive.exists() ? YamlConfiguration.loadConfiguration(archive) : null;
    }

    /** 시즌 이력 목록 문자열 */
    public String getHistoryString() {
        var section = cfg.getConfigurationSection("history");
        if (section == null) return "§7시즌 기록 없음";
        StringBuilder sb = new StringBuilder();
        for (String key : section.getKeys(false)) {
            String n  = cfg.getString("history." + key + ".name", "?");
            long   s  = cfg.getLong("history." + key + ".startedAt");
            long   e  = cfg.getLong("history." + key + ".endedAt");
            sb.append("§7시즌 §e").append(key).append(" §f").append(n)
              .append(" §8(").append(DATE_FMT.format(new Date(s)))
              .append(" ~ ").append(DATE_FMT.format(new Date(e))).append(")\n");
        }
        return sb.isEmpty() ? "§7시즌 기록 없음" : sb.toString().stripTrailing();
    }

    /** 가장 최근 완료 시즌 번호 */
    public int getLastSeason() {
        var section = cfg.getConfigurationSection("history");
        if (section == null) return 0;
        return section.getKeys(false).stream()
                .mapToInt(k -> { try { return Integer.parseInt(k); } catch (Exception e) { return 0; } })
                .max().orElse(0);
    }

    private void save() {
        try { cfg.save(file); } catch (IOException e) {
            plugin.getLogger().warning("[Season] season.yml 저장 실패: " + e.getMessage());
        }
    }
}
