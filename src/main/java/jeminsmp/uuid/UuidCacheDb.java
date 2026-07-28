package jeminsmp.uuid;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.logging.Logger;

public class UuidCacheDb {

    private final Logger log;
    private Connection conn;

    public record CacheRow(
        String realUuid,
        String textureValue,
        String textureSig,
        long cachedAt
    ) {}

    public UuidCacheDb(JeminSMPPlugin plugin) {
        this.log = plugin.getLogger();
        FileConfiguration cfg = plugin.getConfig();

        String host = cfg.getString("database.host", "localhost");
        int    port = cfg.getInt("database.port", 3310);
        String name = cfg.getString("database.name", "jemin_hybrid");
        String user = cfg.getString("database.user", "jemin");
        String pass = cfg.getString("database.password", "jeminpass");

        try {
            String url = "jdbc:mysql://" + host + ":" + port + "/" + name
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            conn = DriverManager.getConnection(url, user, pass);

            try (Statement st = conn.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS uuid_skin_cache (
                        username      VARCHAR(16)  PRIMARY KEY,
                        real_uuid     VARCHAR(36)  NOT NULL,
                        texture_value MEDIUMTEXT,
                        texture_sig   MEDIUMTEXT,
                        uuid_ok       TINYINT(1)   NOT NULL DEFAULT 0,
                        skin_ok       TINYINT(1)   NOT NULL DEFAULT 0,
                        cached_at     BIGINT       NOT NULL
                    )
                """);
            }
            log.info("[UuidDB] 연결 성공 (" + host + ":" + port + "/" + name + ")");
        } catch (SQLException e) {
            log.severe("[UuidDB] 연결 실패: " + e.getMessage());
            conn = null;
        }
    }

    public boolean isConnected() { return conn != null; }

    public CacheRow get(String username) {
        if (conn == null) return null;
        long ttlMs = 24 * 3_600_000L;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT real_uuid, texture_value, texture_sig, cached_at FROM uuid_skin_cache WHERE username = ?")) {
            ps.setString(1, username.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            long cachedAt = rs.getLong("cached_at");
            if (System.currentTimeMillis() - cachedAt > ttlMs) return null;
            return new CacheRow(
                rs.getString("real_uuid"),
                rs.getString("texture_value"),
                rs.getString("texture_sig"),
                cachedAt
            );
        } catch (SQLException e) {
            log.warning("[UuidDB] 조회 실패: " + e.getMessage());
            return null;
        }
    }

    public void save(String username, String realUuid, String textureValue, String textureSig,
                     boolean uuidOk, boolean skinOk) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO uuid_skin_cache
                    (username, real_uuid, texture_value, texture_sig, uuid_ok, skin_ok, cached_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    real_uuid = VALUES(real_uuid),
                    texture_value = VALUES(texture_value),
                    texture_sig = VALUES(texture_sig),
                    uuid_ok = VALUES(uuid_ok),
                    skin_ok = VALUES(skin_ok),
                    cached_at = VALUES(cached_at)
                """)) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, realUuid);
            ps.setString(3, textureValue);
            ps.setString(4, textureSig);
            ps.setInt(5, uuidOk ? 1 : 0);
            ps.setInt(6, skinOk ? 1 : 0);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[UuidDB] 저장 실패: " + e.getMessage());
        }
    }

    public void close() {
        try { if (conn != null && !conn.isClosed()) conn.close(); }
        catch (SQLException ignored) {}
    }
}
