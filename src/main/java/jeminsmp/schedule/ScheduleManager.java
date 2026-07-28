package jeminsmp.schedule;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ScheduleManager {

    private final JeminSMPPlugin plugin;
    private final WaitingManager waitingManager;

    private boolean enabled;
    private boolean forceOpen;
    private boolean forceClose;
    private ZoneId timezone;
    private boolean wasOpen;
    private final Set<Integer> sentWarnings = new HashSet<>();

    private final List<TimeSlot> slots = new ArrayList<>();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public ScheduleManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        this.waitingManager = new WaitingManager(plugin);
        load();
        wasOpen = isOpen();

        // 20초마다 체크 (전환 감지 지연 최소화)
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20 * 20L);
    }

    // ── 설정 로드 ──
    public void load() {
        slots.clear();
        var cfg = plugin.getConfig();
        enabled  = cfg.getBoolean("schedule.enabled", false);
        timezone = ZoneId.of(cfg.getString("schedule.timezone", "Asia/Seoul"));

        var list = cfg.getMapList("schedule.slots");
        for (var map : list) {
            try {
                String days  = map.get("days").toString();
                String start = map.get("start").toString();
                String end   = map.get("end").toString();
                if (end.equals("24:00"))   end   = "00:00";
                if (start.equals("24:00")) start = "00:00";
                slots.add(new TimeSlot(parseDays(days),
                        LocalTime.parse(start, TIME_FMT),
                        LocalTime.parse(end, TIME_FMT)));
            } catch (Exception e) {
                plugin.getLogger().warning("schedule slot 파싱 오류: " + e.getMessage());
            }
        }
    }

    // ── 현재 서버가 열려있는가 ──
    public boolean isOpen() {
        if (forceOpen)  return true;
        if (forceClose) return false;
        if (!enabled)   return true;  // 스케줄 비활성화 → 항상 열림
        return isOpenAt(ZonedDateTime.now(timezone));
    }

    private boolean isOpenAt(ZonedDateTime now) {
        DayOfWeek dow  = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();
        for (TimeSlot slot : slots) {
            if (!slot.days().contains(dow)) continue;
            if (slot.end().isBefore(slot.start()) || slot.end().equals(LocalTime.MIDNIGHT)) {
                // 자정을 넘기는 슬롯 (예: 22:00-02:00)
                if (time.isAfter(slot.start()) || time.isBefore(slot.end())) return true;
            } else {
                if (!time.isBefore(slot.start()) && time.isBefore(slot.end())) return true;
            }
        }
        return false;
    }

    // ── 매 분 체크 ──
    private void tick() {
        ZonedDateTime now = ZonedDateTime.now(timezone);
        boolean openNow = isOpen();

        // 상태 전환 감지
        if (wasOpen && !openNow) {
            plugin.getLogger().info("[Schedule] 서버 닫힘 → 대기열 전환");
            sentWarnings.clear();
            Bukkit.getScheduler().runTask(plugin, waitingManager::enqueueAll);
        } else if (!wasOpen && openNow) {
            plugin.getLogger().info("[Schedule] 서버 열림 → 대기열 해제");
            sentWarnings.clear();
            Bukkit.getScheduler().runTask(plugin, waitingManager::releaseAll);
        }
        wasOpen = openNow;

        // 닫힘 카운트다운 경고 (5분, 3분, 1분 — 각 1회만)
        if (enabled && !forceOpen && !forceClose && openNow) {
            for (int mins : List.of(5, 3, 1)) {
                if (!sentWarnings.contains(mins) && !isOpenAt(now.plusMinutes(mins))) {
                    sentWarnings.add(mins);
                    Bukkit.broadcast(Component.text(
                            "⚠ " + mins + "분 후 서버 운영 시간이 종료됩니다. 대기 상태로 전환됩니다.",
                            NamedTextColor.RED));
                }
            }
        }

        // 대기 중인 플레이어에게 1분마다 순서 + 다음 오픈 시간 알림
        if (!openNow) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                List<UUID> order = waitingManager.getQueueOrder();
                if (order.isEmpty()) return;

                ZonedDateTime next = getNextOpenTime();
                String openStr;
                if (next != null) {
                    Duration dur = Duration.between(ZonedDateTime.now(timezone), next);
                    long h = dur.toHours(), m = dur.toMinutesPart();
                    String timeStr = next.format(DateTimeFormatter.ofPattern("HH:mm"));
                    openStr = "다음 오픈: " + timeStr + " ("
                            + (h > 0 ? h + "시간 " + m + "분" : m + "분") + " 후)";
                } else {
                    openStr = "예정된 오픈 시간 없음";
                }

                for (int i = 0; i < order.size(); i++) {
                    Player p = Bukkit.getPlayer(order.get(i));
                    if (p == null || !p.isOnline()) continue;
                    p.sendMessage(Component.text(
                            "⏳ 대기열 " + (i + 1) + "번  |  " + openStr,
                            NamedTextColor.AQUA));
                }
            });
        }
    }

    // ── 다음 오픈 시간 계산 ──
    public ZonedDateTime getNextOpenTime() {
        if (slots.isEmpty()) return null;
        ZonedDateTime now = ZonedDateTime.now(timezone);
        for (int i = 1; i <= 7 * 24 * 60; i++) {
            ZonedDateTime candidate = now.plusMinutes(i);
            if (isOpenAt(candidate)) return candidate;
        }
        return null;
    }

    // ── 슬롯 추가/삭제 ──
    public boolean addSlot(String days, String start, String end) {
        try {
            // 24:00 → 00:00 (자정) 으로 정규화
            if (end.equals("24:00")) end = "00:00";
            if (start.equals("24:00")) start = "00:00";
            slots.add(new TimeSlot(
                    parseDays(days),
                    LocalTime.parse(start, TIME_FMT),
                    LocalTime.parse(end, TIME_FMT)));
            save();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Schedule] 슬롯 추가 실패: " + e.getMessage());
            return false;
        }
    }

    public boolean removeSlot(int index) {
        if (index < 1 || index > slots.size()) return false;
        slots.remove(index - 1);
        save();
        return true;
    }

    public void clearSlots() {
        slots.clear();
        save();
    }

    private void save() {
        var cfg = plugin.getConfig();
        cfg.set("schedule.enabled", enabled);
        List<Map<String, Object>> list = new ArrayList<>();
        for (TimeSlot s : slots) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("days",  formatDays(s.days()));
            map.put("start", s.start().format(TIME_FMT));
            map.put("end",   s.end().format(TIME_FMT));
            list.add(map);
        }
        cfg.set("schedule.slots", list);
        Bukkit.getScheduler().runTask(plugin, plugin::saveConfig);
    }

    // ── 강제 열기/닫기 ──
    public void setForceOpen(boolean v) {
        forceOpen = v;
        if (v) {
            forceClose = false;
            wasOpen = true;
            Bukkit.getScheduler().runTask(plugin, waitingManager::releaseAll);
        }
    }

    public void setForceClose(boolean v) {
        forceClose = v;
        if (v) {
            forceOpen = false;
            wasOpen = false;
            Bukkit.getScheduler().runTask(plugin, waitingManager::enqueueAll);
        }
    }

    public void clearForceFlags() {
        forceOpen  = false;
        forceClose = false;
        boolean openNow = isOpen();
        if (openNow && !wasOpen) {
            Bukkit.getScheduler().runTask(plugin, waitingManager::releaseAll);
        } else if (!openNow && wasOpen) {
            Bukkit.getScheduler().runTask(plugin, waitingManager::enqueueAll);
        }
        wasOpen = openNow;
    }

    public void setEnabled(boolean v) {
        enabled = v;
        save();
        boolean openNow = isOpen();
        if (openNow && !wasOpen) {
            Bukkit.getScheduler().runTask(plugin, waitingManager::releaseAll);
        } else if (!openNow && wasOpen) {
            Bukkit.getScheduler().runTask(plugin, waitingManager::enqueueAll);
        }
        wasOpen = openNow;
    }

    public boolean isEnabled()    { return enabled; }
    public boolean isForceOpen()  { return forceOpen; }
    public boolean isForceClose() { return forceClose; }

    // ── Google Sheets 동기화 적용 ──
    public void applyFromSync(Boolean newEnabled, String newTimezone, List<String[]> newSlots) {
        boolean changed = false;

        if (newEnabled != null && newEnabled != this.enabled) {
            this.enabled = newEnabled;
            changed = true;
        }
        if (newTimezone != null && !newTimezone.isEmpty()) {
            try {
                ZoneId tz = ZoneId.of(newTimezone);
                if (!tz.equals(this.timezone)) {
                    this.timezone = tz;
                    changed = true;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Sheets] 잘못된 타임존: " + newTimezone);
            }
        }
        if (!newSlots.isEmpty()) {
            List<TimeSlot> parsed = new ArrayList<>();
            for (String[] row : newSlots) {
                try {
                    parsed.add(new TimeSlot(
                            parseDays(row[0]),
                            LocalTime.parse(row[1], TIME_FMT),
                            LocalTime.parse(row[2], TIME_FMT)));
                } catch (Exception e) {
                    plugin.getLogger().warning("[Sheets] 슬롯 파싱 오류: " + e.getMessage());
                }
            }
            if (!parsed.isEmpty()) {
                slots.clear();
                slots.addAll(parsed);
                changed = true;
            }
        }

        if (changed) {
            save();
            boolean openNow = isOpen();
            if (openNow && !wasOpen) {
                Bukkit.getScheduler().runTask(plugin, waitingManager::releaseAll);
            } else if (!openNow && wasOpen) {
                Bukkit.getScheduler().runTask(plugin, waitingManager::enqueueAll);
            }
            wasOpen = openNow;
            plugin.getLogger().info("[Sheets] 스케줄 적용 완료 (슬롯 " + slots.size() + "개)");
        }
    }

    public List<TimeSlot> getSlots()   { return Collections.unmodifiableList(slots); }
    public ZoneId getTimezone()        { return timezone; }
    public WaitingManager getWaitingManager() { return waitingManager; }

    // ── 현재 상태 요약 ──
    public String statusString() {
        ZonedDateTime now = ZonedDateTime.now(timezone);
        String time  = now.format(DateTimeFormatter.ofPattern("MM/dd(E) HH:mm", Locale.KOREAN));
        String state = isOpen() ? "🟢 열림" : "🔴 닫힘";
        StringBuilder sb = new StringBuilder();
        sb.append("**서버 스케줄** (").append(timezone).append(")\n");
        sb.append("현재 시각: ").append(time).append("  |  상태: ").append(state);
        if (forceOpen)  sb.append(" (강제 열림)");
        if (forceClose) sb.append(" (강제 닫힘)");
        if (!enabled)   sb.append(" (스케줄 비활성화)");
        sb.append("\n\n");
        if (slots.isEmpty()) {
            sb.append("등록된 슬롯 없음");
        } else {
            for (int i = 0; i < slots.size(); i++) {
                TimeSlot s = slots.get(i);
                sb.append("`").append(i + 1).append(".` ")
                  .append(formatDays(s.days())).append("  ")
                  .append(s.start().format(TIME_FMT)).append(" ~ ")
                  .append(s.end().format(TIME_FMT)).append("\n");
            }
        }
        return sb.toString();
    }

    // ── 유틸 ──
    public static Set<DayOfWeek> parseDays(String days) {
        days = days.trim();
        String upper = days.toUpperCase();

        // 영문 키워드
        if (upper.equals("ALL")     || upper.equals("매일")) return EnumSet.allOf(DayOfWeek.class);
        if (upper.equals("WEEKDAY") || upper.equals("평일"))
            return EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        if (upper.equals("WEEKEND") || upper.equals("주말"))
            return EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

        // 한글 요일 붙여쓰기: "월화수목금", "월수금토" 등 — 한 글자씩 파싱
        if (days.matches("[월화수목금토일]+")) {
            Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
            for (char c : days.toCharArray()) {
                switch (c) {
                    case '월' -> result.add(DayOfWeek.MONDAY);
                    case '화' -> result.add(DayOfWeek.TUESDAY);
                    case '수' -> result.add(DayOfWeek.WEDNESDAY);
                    case '목' -> result.add(DayOfWeek.THURSDAY);
                    case '금' -> result.add(DayOfWeek.FRIDAY);
                    case '토' -> result.add(DayOfWeek.SATURDAY);
                    case '일' -> result.add(DayOfWeek.SUNDAY);
                }
            }
            if (!result.isEmpty()) return result;
        }

        // 콤마·공백 구분 파싱: "월,수,금" / "MON WED FRI" 등
        Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (String d : days.split("[,\\s]+")) {
            switch (d.trim().toUpperCase()) {
                case "MON", "월" -> result.add(DayOfWeek.MONDAY);
                case "TUE", "화" -> result.add(DayOfWeek.TUESDAY);
                case "WED", "수" -> result.add(DayOfWeek.WEDNESDAY);
                case "THU", "목" -> result.add(DayOfWeek.THURSDAY);
                case "FRI", "금" -> result.add(DayOfWeek.FRIDAY);
                case "SAT", "토" -> result.add(DayOfWeek.SATURDAY);
                case "SUN", "일" -> result.add(DayOfWeek.SUNDAY);
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("요일 파싱 실패: " + days);
        return result;
    }

    public static String formatDays(Set<DayOfWeek> days) {
        if (days.size() == 7) return "매일";
        if (days.containsAll(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
                && days.size() == 5) return "평일";
        if (days.contains(DayOfWeek.SATURDAY) && days.contains(DayOfWeek.SUNDAY)
                && days.size() == 2) return "주말";
        String[] KO = {"월", "화", "수", "목", "금", "토", "일"};
        StringBuilder sb = new StringBuilder();
        for (DayOfWeek d : DayOfWeek.values()) {
            if (days.contains(d)) sb.append(KO[d.getValue() - 1]);
        }
        return sb.toString();
    }

    public record TimeSlot(Set<DayOfWeek> days, LocalTime start, LocalTime end) {}
}
