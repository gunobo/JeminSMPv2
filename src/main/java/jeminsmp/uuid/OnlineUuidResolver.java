package jeminsmp.uuid;

import jeminsmp.JeminSMPPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Logger;

public class OnlineUuidResolver {

    public record ResolvedProfile(UUID uuid, String textureValue, String textureSig) {}

    private static final int MAX_RETRIES    = 2;
    private static final int TIMEOUT_SECONDS = 10;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();

    private final Logger log;
    private final UuidCacheDb db;

    public OnlineUuidResolver(JeminSMPPlugin plugin, UuidCacheDb db) {
        this.log = plugin.getLogger();
        this.db  = db;
        log.info("[UuidResolver] 초기화 완료 (timeout=" + TIMEOUT_SECONDS + "s, retries=" + MAX_RETRIES + ", db=" + db.isConnected() + ")");
    }

    /** Resolves UUID + skin for the given player name. Blocking. */
    public ResolvedProfile resolve(String name) {
        // 1. DB 캐시 확인
        UuidCacheDb.CacheRow row = db.get(name);
        if (row != null) {
            try {
                UUID uuid = UUID.fromString(row.realUuid());
                log.info("[UuidBridge] " + name + " 결과 (캐시)\n"
                        + "  ✅ UUID : " + row.realUuid() + "\n"
                        + (row.textureValue() != null ? "  ✅ 스킨 : 적용됨" : "  ❌ 스킨 : 없음 (캐시)"));
                return new ResolvedProfile(uuid, row.textureValue(), row.textureSig());
            } catch (IllegalArgumentException ignored) {}
        }

        // 2. Mojang API 조회
        String rawId       = null;
        UUID   uuid        = null;
        String textureValue = null;
        String textureSig   = null;
        boolean uuidOk     = false;
        boolean skinOk     = false;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // UUID 조회
                HttpRequest req1 = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                        .header("User-Agent", "JeminSMP/1.0")
                        .GET().build();

                HttpResponse<String> res1 = http.send(req1, HttpResponse.BodyHandlers.ofString());

                if (res1.statusCode() != 200) {
                    log.warning("[UuidResolver] " + name + " UUID API 응답 " + res1.statusCode());
                    break;
                }

                rawId = parseJsonValue(res1.body(), "id");
                if (rawId == null || rawId.length() != 32) {
                    log.warning("[UuidResolver] " + name + " UUID 파싱 실패: " + res1.body());
                    break;
                }

                uuid = UUID.fromString(
                    rawId.substring(0,8) + "-" + rawId.substring(8,12) + "-" +
                    rawId.substring(12,16) + "-" + rawId.substring(16,20) + "-" + rawId.substring(20));
                uuidOk = true;

                // 스킨 조회
                HttpRequest req2 = HttpRequest.newBuilder()
                        .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + rawId + "?unsigned=false"))
                        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                        .header("User-Agent", "JeminSMP/1.0")
                        .GET().build();

                HttpResponse<String> res2 = http.send(req2, HttpResponse.BodyHandlers.ofString());

                if (res2.statusCode() == 200) {
                    textureValue = parseJsonValue(res2.body(), "value");
                    textureSig   = parseJsonValue(res2.body(), "signature");
                    skinOk = (textureValue != null);
                }
                break;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warning("[UuidResolver] " + name + " 스레드 인터럽트 (시도 " + attempt + ")");
                break;
            } catch (IOException e) {
                if (attempt < MAX_RETRIES) {
                    log.warning("[UuidResolver] " + name + " 오류 (시도 " + attempt + "/" + MAX_RETRIES + "): " + e.getMessage() + " — 재시도");
                } else {
                    log.warning("[UuidResolver] " + name + " 최종 실패: " + e.getMessage());
                }
            }
        }

        // 3. 결과 로깅
        log.info("[UuidBridge] " + name + " 결과 (API)\n"
                + (uuidOk ? "  ✅ UUID : " + uuid : "  ❌ UUID : 조회 실패") + "\n"
                + (skinOk ? "  ✅ 스킨 : 적용됨" : "  ❌ 스킨 : " + (uuidOk ? "조회 실패" : "UUID 실패로 스킵")));

        if (!uuidOk) return null;

        // 4. DB 저장
        db.save(name, uuid.toString(), textureValue, textureSig, true, skinOk);

        return new ResolvedProfile(uuid, textureValue, textureSig);
    }

    public void invalidate(String name) {
        // DB에서는 개별 삭제 대신 만료 처리 (캐시 TTL로 자동 처리됨)
        log.info("[UuidResolver] " + name + " 캐시 무효화 요청 (다음 접속 시 재조회)");
    }

    /** Flexibly parses "key" : "value" or "key":"value" from JSON string. */
    private String parseJsonValue(String json, String key) {
        int ki = json.indexOf("\"" + key + "\"");
        if (ki < 0) return null;
        int colon = json.indexOf(":", ki);
        if (colon < 0) return null;
        int q1 = json.indexOf("\"", colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf("\"", q1 + 1);
        return q2 < 0 ? null : json.substring(q1 + 1, q2);
    }
}
