package jeminsmp.schedule;

import com.google.gson.*;
import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Google Sheets API v4 — 그리드 형식
 *
 *      B       C           D           ...  J
 * 2   요일   오픈시간(스케줄러)
 * 3           스케줄러1   스케줄러2   ...  스케줄러8
 * 4   월      18:00-23:00
 * 5   화
 * 6   수
 * 7   목
 * 8   금
 * 9   토      14:00-22:00
 * 10  일
 *
 * 읽기  : API 키 (공개 시트)
 * 쓰기  : 서비스 계정 JSON (plugins/jeminSMPv1/service-account.json)
 *         + 시트를 서비스 계정 이메일과 공유(편집자)
 */
public class SheetsManager {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // B열 요일 → DayOfWeek (순서 고정)
    private static final LinkedHashMap<String, DayOfWeek> DAY_MAP = new LinkedHashMap<>();
    static {
        DAY_MAP.put("월", DayOfWeek.MONDAY);
        DAY_MAP.put("화", DayOfWeek.TUESDAY);
        DAY_MAP.put("수", DayOfWeek.WEDNESDAY);
        DAY_MAP.put("목", DayOfWeek.THURSDAY);
        DAY_MAP.put("금", DayOfWeek.FRIDAY);
        DAY_MAP.put("토", DayOfWeek.SATURDAY);
        DAY_MAP.put("일", DayOfWeek.SUNDAY);
    }

    private final JeminSMPPlugin plugin;
    private volatile long   lastSyncMillis = 0;
    private volatile String lastResult     = "동기화 없음";

    public SheetsManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        scheduleAutoSync();
    }

    // ── 자동 동기화 ──
    private void scheduleAutoSync() {
        int minutes = plugin.getConfig().getInt("google.sheets.sync-interval-minutes", 5);
        long ticks  = (long) minutes * 60 * 20;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::runSync, ticks, ticks);
    }

    public void syncNow(Runnable onComplete) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            runSync();
            if (onComplete != null)
                Bukkit.getScheduler().runTask(plugin, onComplete);
        });
    }

    // ── 동기화 메인 ──
    private void runSync() {
        if (!plugin.getConfig().getBoolean("google.sheets.enabled", false)) return;

        String apiKey  = plugin.getConfig().getString("google.sheets.api-key",  "").trim();
        String sheetId = plugin.getConfig().getString("google.sheets.sheet-id", "").trim();
        String range   = plugin.getConfig().getString("google.sheets.range",    "B2:J10").trim();

        if (sheetId.isEmpty()) { lastResult = "❌ sheet-id 미설정"; return; }
        if (apiKey.isEmpty())  { lastResult = "❌ api-key 미설정";  return; }

        try {
            String url = "https://sheets.googleapis.com/v4/spreadsheets/"
                    + sheetId + "/values/"
                    + URLEncoder.encode(range, StandardCharsets.UTF_8)
                    + "?key=" + apiKey;

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 403) {
                lastResult = "❌ 403 — 시트를 '링크 있는 모두 뷰어'로 공개하세요";
                plugin.getLogger().warning("[Sheets] 403 Forbidden");
                return;
            }
            if (res.statusCode() != 200) {
                lastResult = "❌ API 오류 (" + res.statusCode() + ")";
                plugin.getLogger().warning("[Sheets] HTTP " + res.statusCode() + ": " + res.body());
                return;
            }

            lastSyncMillis = System.currentTimeMillis();
            final String body = res.body();

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    boolean empty = parseAndApplyGrid(body);
                    if (empty) {
                        // 비어있으면 현재 스케줄을 시트에 기록
                        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                                () -> writeScheduleToSheet(sheetId));
                    }
                } catch (Exception ex) {
                    lastResult = "❌ 적용 오류: " + ex.getMessage();
                    plugin.getLogger().warning("[Sheets] 오류: " + ex.getMessage());
                    ex.printStackTrace();
                }
            });

        } catch (Exception e) {
            lastResult = "❌ 요청 실패: " + e.getMessage();
            plugin.getLogger().warning("[Sheets] 동기화 실패: " + e.getMessage());
        }
    }

    // ── 그리드 파싱 ──
    // range = B2:J10 → 각 row의 index 0 = B열 (요일), index 1~8 = C~J열 (시간대)
    private boolean parseAndApplyGrid(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        if (!obj.has("values")) {
            lastResult = "⚠ 시트가 비어있습니다. 현재 스케줄을 자동으로 채우는 중...";
            return true;
        }

        JsonArray rows   = obj.getAsJsonArray("values");
        List<String[]> slots = new ArrayList<>();

        for (JsonElement el : rows) {
            if (!el.isJsonArray()) continue;
            JsonArray row    = el.getAsJsonArray();
            String   dayCell = cell(row, 0);

            DayOfWeek dow = DAY_MAP.get(dayCell);
            if (dow == null) continue; // 헤더/빈 행 무시

            // index 1~8 = 스케줄러1~8
            for (int i = 1; i <= 8; i++) {
                String t = cell(row, i);
                if (t.isEmpty()) continue;
                String[] tp = t.split("-", 2);
                if (tp.length == 2 && tp[0].contains(":") && tp[1].contains(":"))
                    slots.add(new String[]{dayCell, tp[0].trim(), tp[1].trim()});
            }
        }

        if (slots.isEmpty()) {
            lastResult = "⚠ 시트가 비어있습니다. 현재 스케줄을 자동으로 채우는 중...";
            return true;
        }

        plugin.getScheduleManager().applyFromSync(null, null, slots);

        String t = Instant.ofEpochMilli(lastSyncMillis)
                .atZone(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        lastResult = "✅ " + t + " 동기화 완료 (" + slots.size() + "개 슬롯)";
        plugin.getLogger().info("[Sheets] " + lastResult);
        return false;
    }

    // ── 시트에 현재 스케줄 기록 (서비스 계정 필요) ──
    private void writeScheduleToSheet(String sheetId) {
        try {
            String token = getAccessToken();
            if (token == null) {
                lastResult = "⚠ 시트가 비어있습니다.\n"
                        + "자동 채우기를 사용하려면 서비스 계정 JSON을\n"
                        + "`plugins/jeminSMPv1/service-account.json` 에 넣고\n"
                        + "시트를 해당 서비스 계정 이메일과 공유(편집자)하세요.";
                plugin.getLogger().info("[Sheets] service-account.json 없음 — 자동 채우기 스킵");
                return;
            }

            List<ScheduleManager.TimeSlot> current = plugin.getScheduleManager().getSlots();
            String[] dayNames = {"월", "화", "수", "목", "금", "토", "일"};
            DayOfWeek[] dows  = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
            };

            // C4:J10 값 구성
            JsonArray values = new JsonArray();
            for (DayOfWeek dow : dows) {
                JsonArray row = new JsonArray();
                int col = 0;
                for (ScheduleManager.TimeSlot s : current) {
                    if (s.days().contains(dow)) {
                        row.add(s.start().format(TIME_FMT) + "-" + s.end().format(TIME_FMT));
                        if (++col >= 8) break;
                    }
                }
                while (col++ < 8) row.add("");
                values.add(row);
            }

            JsonObject body = new JsonObject();
            body.addProperty("range",          "C4:J10");
            body.addProperty("majorDimension", "ROWS");
            body.add("values", values);

            String url = "https://sheets.googleapis.com/v4/spreadsheets/" + sheetId
                    + "/values/C4:J10?valueInputOption=USER_ENTERED";

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200) {
                lastResult = "✅ 시트가 비어있어 현재 스케줄을 자동으로 채웠습니다.";
                plugin.getLogger().info("[Sheets] " + lastResult);
            } else {
                lastResult = "❌ 시트 쓰기 실패 (" + res.statusCode() + ")";
                plugin.getLogger().warning("[Sheets] 쓰기 실패: " + res.body());
            }

        } catch (Exception e) {
            lastResult = "❌ 시트 쓰기 오류: " + e.getMessage();
            plugin.getLogger().warning("[Sheets] 쓰기 오류: " + e.getMessage());
        }
    }

    // ── 서비스 계정 → OAuth2 액세스 토큰 ──
    private String getAccessToken() throws Exception {
        File saFile = new File(plugin.getDataFolder(),
                plugin.getConfig().getString("google.sheets.service-account-file", "service-account.json"));
        if (!saFile.exists()) return null;

        JsonObject sa = JsonParser.parseString(Files.readString(saFile.toPath())).getAsJsonObject();
        String rawKey = sa.get("private_key").getAsString()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("[\\r\\n\\s]", "");
        String clientEmail = sa.get("client_email").getAsString();

        // JWT 헤더·클레임
        long now = System.currentTimeMillis() / 1000;
        String header = b64(("{\"alg\":\"RS256\",\"typ\":\"JWT\"}").getBytes(StandardCharsets.UTF_8));
        String payload = b64((
                "{\"iss\":\"" + clientEmail + "\","
                + "\"scope\":\"https://www.googleapis.com/auth/spreadsheets\","
                + "\"aud\":\"https://oauth2.googleapis.com/token\","
                + "\"exp\":" + (now + 3600) + ","
                + "\"iat\":" + now + "}"
        ).getBytes(StandardCharsets.UTF_8));

        String toSign = header + "." + payload;
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey pk = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(rawKey)));
        Signature  sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(pk);
        sig.update(toSign.getBytes(StandardCharsets.UTF_8));
        String jwt = toSign + "." + b64(sig.sign());

        // 토큰 교환
        String reqBody = "grant_type="
                + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(jwt, StandardCharsets.UTF_8);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/token"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject tr = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!tr.has("access_token")) {
            plugin.getLogger().warning("[Sheets] 토큰 발급 실패: " + res.body());
            return null;
        }
        return tr.get("access_token").getAsString();
    }

    private static String b64(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String cell(JsonArray row, int idx) {
        if (idx >= row.size()) return "";
        JsonElement el = row.get(idx);
        return el.isJsonNull() ? "" : el.getAsString().trim();
    }

    public String getLastResult()    { return lastResult; }
    public long   getLastSyncMillis() { return lastSyncMillis; }
}
