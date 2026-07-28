package jeminsmp.discord;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.compensation.CompensationManager;
import jeminsmp.schedule.ScheduleManager;
import jeminsmp.title.TitleManager.TitleInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DiscordListener extends ListenerAdapter {

    private final JeminSMPPlugin plugin;
    private final DiscordBot discordBot;

    // 요일 선택 UI 세션
    private final Map<String, PendingScheduleAdd> pendingScheduleAdds = new ConcurrentHashMap<>();

    private record PendingScheduleAdd(Set<DayOfWeek> selected, String start, String end) {}

    public DiscordListener(JeminSMPPlugin plugin, DiscordBot discordBot) {
        this.plugin = plugin;
        this.discordBot = discordBot;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        discordBot.onReady(event.getJDA());
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw().trim();
        String prefix = plugin.getConfig().getString("discord.command-prefix", "!");

        // 명령어는 어느 채널에서든 처리
        if (content.startsWith(prefix)) {
            String withoutPrefix = content.substring(prefix.length()).trim();
            if (withoutPrefix.equalsIgnoreCase("help") || withoutPrefix.equals("사용법")) {
                handleHelp(event, prefix);
                return;
            }
            if (withoutPrefix.equalsIgnoreCase("online")) {
                handleOnline(event);
                return;
            }
            if (withoutPrefix.startsWith("run ")) {
                handleRun(event, withoutPrefix.substring(4).trim());
                return;
            }
            if (withoutPrefix.startsWith("announce")) {
                handleAnnounce(event, withoutPrefix.substring("announce".length()).trim());
                return;
            }
            if (withoutPrefix.startsWith("title")) {
                handleTitle(event, withoutPrefix.substring("title".length()).trim());
                return;
            }
            if (withoutPrefix.equalsIgnoreCase("kicka")) {
                handleKickAll(event);
                return;
            }
            if (withoutPrefix.startsWith("update")) {
                handleUpdate(event, withoutPrefix.substring("update".length()).trim());
                return;
            }
            if (withoutPrefix.equalsIgnoreCase("patch") || withoutPrefix.equalsIgnoreCase("changelog")) {
                handlePatchNote(event);
                return;
            }
            if (withoutPrefix.startsWith("schedule") || withoutPrefix.startsWith("스케줄")) {
                String rest = withoutPrefix.startsWith("schedule")
                        ? withoutPrefix.substring("schedule".length()).trim()
                        : withoutPrefix.substring("스케줄".length()).trim();
                handleSchedule(event, rest);
                return;
            }
            if (withoutPrefix.startsWith("warn") || withoutPrefix.startsWith("unwarn")
                    || withoutPrefix.startsWith("warnings") || withoutPrefix.startsWith("punish")
                    || withoutPrefix.startsWith("unpunish")) {
                handleWarn(event, withoutPrefix);
                return;
            }
            if (withoutPrefix.startsWith("link") || withoutPrefix.equalsIgnoreCase("unlink")) {
                handleLink(event, withoutPrefix);
                return;
            }
            if (withoutPrefix.startsWith("comp")) {
                handleComp(event, withoutPrefix.substring("comp".length()).trim());
                return;
            }
            if (withoutPrefix.equalsIgnoreCase("opennow")) {
                handleOpenNow(event, true);
                return;
            }
            if (withoutPrefix.equalsIgnoreCase("closenow")) {
                handleOpenNow(event, false);
                return;
            }
            if (withoutPrefix.startsWith("season") || withoutPrefix.startsWith("시즌")) {
                String rest = withoutPrefix.startsWith("season")
                        ? withoutPrefix.substring("season".length()).trim()
                        : withoutPrefix.substring("시즌".length()).trim();
                handleSeason(event, rest);
                return;
            }
            if (withoutPrefix.equalsIgnoreCase("startsmp") || withoutPrefix.startsWith("startsmp ")) {
                handleStartSmp(event, withoutPrefix.substring("startsmp".length()).trim());
                return;
            }
            event.getChannel().sendMessage("❓ 알 수 없는 명령어입니다.").queue();
            return;
        }

        // 채팅 → 이벤트 채널에서만 게임으로 전송
        if (discordBot.getEventsChannel() == null) return;
        if (!event.getChannel().getId().equals(discordBot.getEventsChannel().getId())) return;

        String finalUsername = event.getMember() != null ? event.getMember().getEffectiveName() : event.getAuthor().getName();
        // Raw content의 멘션(<@id>) → 표시 이름으로 변환
        String finalContent = event.getMessage().getContentDisplay();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Component msg = Component.text("[Discord] ", NamedTextColor.BLUE)
                    .append(Component.text(finalUsername, NamedTextColor.WHITE))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(Component.text(finalContent, NamedTextColor.WHITE));
            Bukkit.broadcast(msg);
        });
    }

    private void handleHelp(MessageReceivedEvent event, String prefix) {
        String help = "**📖 jeminSMPv1 Discord 명령어**\n"
                + "`" + prefix + "help` — 이 도움말\n"
                + "`" + prefix + "online` — 현재 접속 중인 플레이어 목록\n"
                + "`" + prefix + "run <명령어>` — 서버 명령어 실행 (관리자 전용)\n"
                + "`" + prefix + "announce list` — 공지 목록 (관리자 전용)\n"
                + "`" + prefix + "announce add <내용>` — 공지 추가 (관리자 전용)\n"
                + "`" + prefix + "announce remove <번호>` — 공지 삭제 (관리자 전용)\n"
                + "`" + prefix + "title list` — 칭호 목록 임베드\n"
                + "`" + prefix + "title add <id> <가격💎> <표시>` — 칭호 추가 (관리자 전용)\n"
                + "`" + prefix + "title edit <id> price <가격>` — 가격 수정 (관리자 전용)\n"
                + "`" + prefix + "title edit <id> display <표시>` — 표시명 수정 (관리자 전용)\n"
                + "`" + prefix + "title delete <id>` — 칭호 삭제 (관리자 전용)\n"
                + "`" + prefix + "title color` — 색상코드 목록\n"
                + "`" + prefix + "kicka` — 전체 플레이어 킥 (관리자 전용)\n"
                + "`" + prefix + "update <제목> | <내용>` — 업데이트 공지 (관리자 전용)\n"
                + "`" + prefix + "schedule` — 운영 스케줄 현황\n"
                + "`" + prefix + "schedule add <요일> <시작>-<끝>` — 슬롯 추가 (관리자 전용)\n"
                + "`" + prefix + "schedule remove <번호>` — 슬롯 삭제 (관리자 전용)\n"
                + "`" + prefix + "schedule on/off` — 스케줄 활성/비활성 (관리자 전용)\n"
                + "`" + prefix + "opennow` — 즉시 강제 오픈 (관리자 전용)\n"
                + "`" + prefix + "closenow` — 즉시 강제 닫기 (관리자 전용)\n"
                + "`" + prefix + "patch` — 최신 패치노트 임베드 발송 (관리자 전용)";
        event.getChannel().sendMessage(help).queue();
    }

    // ── 스케줄 관리 ──
    private void handleSchedule(MessageReceivedEvent event, String args) {
        ScheduleManager sm = plugin.getScheduleManager();

        // 현황 조회 — 누구나
        if (args.isEmpty() || args.equalsIgnoreCase("status") || args.equalsIgnoreCase("현황")) {
            event.getChannel().sendMessage("📅 " + sm.statusString()).queue();
            return;
        }

        // 설정 임베드 — 관리자 전용
        if (args.equalsIgnoreCase("setting") || args.equalsIgnoreCase("settings")
                || args.equalsIgnoreCase("set") || args.equalsIgnoreCase("설정")) {
            if (!hasAdminRole(event.getMember())) {
                event.getChannel().sendMessage("❌ 관리자 역할이 없어 사용할 수 없습니다.").queue();
                return;
            }
            event.getChannel().sendMessageEmbeds(buildSettingEmbed())
                    .setComponents(buildSettingRows())
                    .queue();
            return;
        }

        // 이하 관리자 전용
        if (!hasAdminRole(event.getMember())) {
            event.getChannel().sendMessage("❌ 관리자 역할이 없어 사용할 수 없습니다.").queue();
            return;
        }

        // !schedule on / off
        if (args.equalsIgnoreCase("on") || args.equalsIgnoreCase("enable")) {
            sm.setEnabled(true);
            event.getChannel().sendMessage("✅ 스케줄이 **활성화**되었습니다.").queue();
            return;
        }
        if (args.equalsIgnoreCase("off") || args.equalsIgnoreCase("disable")) {
            sm.setEnabled(false);
            event.getChannel().sendMessage("✅ 스케줄이 **비활성화**되었습니다. (서버가 항상 열립니다)").queue();
            return;
        }

        // !schedule add <HH:mm>-<HH:mm>           → 요일 선택 UI
        // !schedule add <요일> <HH:mm>-<HH:mm>   → 직접 추가
        if (args.startsWith("add ")) {
            String rest = args.substring(4).trim();
            int lastSpace = rest.lastIndexOf(' ');

            if (lastSpace < 0) {
                // 시간만 입력됨 → 요일 선택 UI 표시
                String[] tp = rest.split("-", 2);
                if (tp.length != 2 || !tp[0].contains(":") || !tp[1].contains(":")) {
                    event.getChannel().sendMessage(
                            "❓ 사용법:\n"
                            + "`schedule add <시작>-<끝>` — 버튼으로 요일 선택\n"
                            + "`schedule add <요일> <시작>-<끝>` — 직접 추가\n"
                            + "예) `schedule add 18:00-23:00`  또는  `schedule add 평일 18:00-23:00`"
                    ).queue();
                    return;
                }
                showDayPicker(event, tp[0].trim(), tp[1].trim());
                return;
            }

            // 요일 직접 지정 (시간대 여러 개 지원: 00:00-08:40,12:30-13:20,...)
            String days   = rest.substring(0, lastSpace).trim();
            String ranges = rest.substring(lastSpace + 1).trim();
            int added = 0;
            StringBuilder addedList = new StringBuilder();
            for (String r : ranges.split(",")) {
                r = r.trim();
                String[] parts = r.split("-", 2);
                if (parts.length != 2 || !parts[0].contains(":") || !parts[1].contains(":")) {
                    event.getChannel().sendMessage("❌ 시간 형식 오류: `" + r + "` — 예) `18:00-23:00`").queue();
                    return;
                }
                if (sm.addSlot(days, parts[0].trim(), parts[1].trim())) {
                    addedList.append("  • ").append(parts[0].trim()).append(" ~ ").append(parts[1].trim()).append("\n");
                    added++;
                }
            }
            if (added > 0) {
                event.getChannel().sendMessage(
                        "✅ **" + days + "** 슬롯 " + added + "개 추가 완료:\n" + addedList
                        + "\n" + sm.statusString()
                ).queue();
            } else {
                event.getChannel().sendMessage("❌ 슬롯 추가 실패. 요일/시간 형식을 확인하세요.").queue();
            }
            return;
        }

        // !schedule sheets on/off/key/id — 구글 시트 설정
        if (args.startsWith("sheets")) {
            String sub = args.substring("sheets".length()).trim();
            var cfg = plugin.getConfig();

            if (sub.equalsIgnoreCase("on") || sub.equalsIgnoreCase("켜기")) {
                cfg.set("google.sheets.enabled", true);
                if (!cfg.contains("google.sheets.api-key"))  cfg.set("google.sheets.api-key", "");
                if (!cfg.contains("google.sheets.sheet-id")) cfg.set("google.sheets.sheet-id", "");
                cfg.set("google.sheets.range", "B2:J10");   // 항상 최신 범위로 강제 설정
                if (!cfg.contains("google.sheets.sync-interval-minutes")) cfg.set("google.sheets.sync-interval-minutes", 5);
                plugin.saveConfig();
                event.getChannel().sendMessage("✅ Google Sheets 연동 **활성화**됨.\n"
                        + "API 키와 시트 ID가 설정되어 있으면 `!schedule refresh` 로 동기화하세요.").queue();
                return;
            }
            if (sub.equalsIgnoreCase("off") || sub.equalsIgnoreCase("끄기")) {
                cfg.set("google.sheets.enabled", false);
                plugin.saveConfig();
                event.getChannel().sendMessage("✅ Google Sheets 연동 **비활성화**됨.").queue();
                return;
            }
            if (sub.startsWith("key ")) {
                String key = sub.substring(4).trim();
                cfg.set("google.sheets.api-key", key);
                plugin.saveConfig();
                event.getChannel().sendMessage("✅ API 키 저장 완료.").queue();
                return;
            }
            if (sub.startsWith("id ")) {
                String id = sub.substring(3).trim();
                cfg.set("google.sheets.sheet-id", id);
                plugin.saveConfig();
                event.getChannel().sendMessage("✅ 시트 ID 저장 완료.").queue();
                return;
            }
            boolean enabled = cfg.getBoolean("google.sheets.enabled", false);
            String apiKey  = cfg.getString("google.sheets.api-key", "(미설정)");
            String sheetId = cfg.getString("google.sheets.sheet-id", "(미설정)");
            event.getChannel().sendMessage(
                    "📊 **Google Sheets 설정**\n"
                    + "상태: " + (enabled ? "✅ 활성" : "❌ 비활성") + "\n"
                    + "API 키: `" + (apiKey.isEmpty() ? "미설정" : apiKey.substring(0, Math.min(8, apiKey.length())) + "...") + "`\n"
                    + "시트 ID: `" + (sheetId.isEmpty() ? "미설정" : sheetId) + "`\n\n"
                    + "`!schedule sheets on/off` — 활성화/비활성화\n"
                    + "`!schedule sheets key <API키>` — API 키 설정\n"
                    + "`!schedule sheets id <시트ID>` — 시트 ID 설정"
            ).queue();
            return;
        }

        // !schedule refresh / sync — 구글 시트 즉시 동기화
        if (args.equalsIgnoreCase("refresh") || args.equalsIgnoreCase("sync")
                || args.equalsIgnoreCase("동기화")) {
            var sheets = plugin.getSheetsManager();
            if (!plugin.getConfig().getBoolean("google.sheets.enabled", false)) {
                event.getChannel().sendMessage("⚠ Google Sheets 연동이 비활성화되어 있습니다.\n"
                        + "`!schedule sheets on` 으로 활성화하세요.").queue();
                return;
            }
            event.getChannel().sendMessage("🔄 구글 시트에서 스케줄 동기화 중...").queue(msg ->
                    sheets.syncNow(() -> msg.editMessage(
                            plugin.getSheetsManager().getLastResult()
                            + "\n\n" + plugin.getScheduleManager().statusString()).queue())
            );
            return;
        }

        // !schedule clearall — 모든 슬롯 삭제
        if (args.equalsIgnoreCase("clearall") || args.equalsIgnoreCase("clear")) {
            sm.clearSlots();
            event.getChannel().sendMessage("✅ 모든 슬롯이 삭제되었습니다.").queue();
            return;
        }

        // !schedule remove <n>
        if (args.startsWith("remove ") || args.startsWith("del ") || args.startsWith("삭제 ")) {
            String numStr = args.contains(" ") ? args.substring(args.indexOf(' ') + 1).trim() : "";
            try {
                int idx = Integer.parseInt(numStr);
                if (sm.removeSlot(idx)) {
                    event.getChannel().sendMessage(
                            "✅ " + idx + "번 슬롯 삭제 완료.\n\n" + sm.statusString()
                    ).queue();
                } else {
                    event.getChannel().sendMessage("❌ 잘못된 번호입니다. `schedule` 으로 목록을 확인하세요.").queue();
                }
            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("❓ 사용법: `schedule remove <번호>`").queue();
            }
            return;
        }

        event.getChannel().sendMessage(
                "❓ 사용법:\n"
                + "`schedule` — 현황\n"
                + "`schedule on/off` — 스케줄 활성/비활성\n"
                + "`schedule add <요일> <시작>-<끝>` — 슬롯 추가\n"
                + "`schedule remove <번호>` — 슬롯 삭제\n"
                + "`schedule refresh` — 구글 시트에서 즉시 동기화\n"
                + "`schedule setting` — 버튼 UI로 설정"
        ).queue();
    }

    // ── 요일 선택 UI ──
    private void showDayPicker(MessageReceivedEvent event, String start, String end) {
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        pendingScheduleAdds.put(sessionId, new PendingScheduleAdd(new HashSet<>(), start, end));
        event.getChannel().sendMessage(
                "📅 **요일을 선택하세요** (" + start + " ~ " + end + ")\n선택됨: _(없음)_\n\n"
                + "요일 버튼을 눌러 켜고/끄세요. 완료 후 **✅ 추가** 를 누르세요."
        ).setComponents(buildDayPickerRows(sessionId, new HashSet<>())).queue();
    }

    private List<ActionRow> buildDayPickerRows(String sid, Set<DayOfWeek> sel) {
        String[] labels = {"월", "화", "수", "목", "금", "토", "일"};
        DayOfWeek[] dows = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        };
        List<Button> dayRow = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            boolean on = sel.contains(dows[i]);
            dayRow.add(on
                ? Button.success("sched_day_" + dows[i].name() + "_" + sid, labels[i])
                : Button.secondary("sched_day_" + dows[i].name() + "_" + sid, labels[i]));
        }
        List<Button> presetRow = List.of(
            Button.primary("sched_pre_WEEKDAY_" + sid, "평일"),
            Button.primary("sched_pre_WEEKEND_" + sid, "주말"),
            Button.primary("sched_pre_ALL_" + sid, "매일"),
            Button.secondary("sched_pre_CLEAR_" + sid, "초기화")
        );
        List<Button> actionRow = List.of(
            Button.success("sched_confirm_" + sid, "✅ 추가"),
            Button.danger("sched_cancel_" + sid, "❌ 취소")
        );
        return List.of(ActionRow.of(dayRow), ActionRow.of(presetRow), ActionRow.of(actionRow));
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String id = event.getComponentId();

        // 스케줄 설정 임베드 버튼
        if (id.startsWith("ss_")) {
            handleSettingButton(event);
            return;
        }

        if (!id.startsWith("sched_")) return;

        String[] parts = id.split("_");
        // sched_day_MONDAY_sessionId  → parts[0]=sched, [1]=day,     [2]=MONDAY,   [3]=sessionId
        // sched_pre_WEEKDAY_sessionId → parts[0]=sched, [1]=pre,     [2]=WEEKDAY,  [3]=sessionId
        // sched_confirm_sessionId     → parts[0]=sched, [1]=confirm, [2]=sessionId
        // sched_cancel_sessionId      → parts[0]=sched, [1]=cancel,  [2]=sessionId
        String kind = parts[1];
        String sessionId = parts[parts.length - 1];

        PendingScheduleAdd pending = pendingScheduleAdds.get(sessionId);
        if (pending == null) {
            event.reply("⏱ 세션이 만료되었습니다. 다시 `schedule add` 명령어를 사용하세요.").setEphemeral(true).queue();
            return;
        }

        Set<DayOfWeek> sel = new HashSet<>(pending.selected());

        switch (kind) {
            case "cancel" -> {
                pendingScheduleAdds.remove(sessionId);
                event.editMessage("❌ 슬롯 추가가 취소되었습니다.").setComponents(List.of()).queue();
            }
            case "confirm" -> {
                if (sel.isEmpty()) {
                    event.reply("⚠ 요일을 하나 이상 선택해주세요.").setEphemeral(true).queue();
                    return;
                }
                pendingScheduleAdds.remove(sessionId);
                String daysStr = formatDaysForManager(sel);
                ScheduleManager sm = plugin.getScheduleManager();
                if (sm.addSlot(daysStr, pending.start(), pending.end())) {
                    event.editMessage(
                            "✅ **슬롯 추가 완료!** `" + daysStr + "` "
                            + pending.start() + " ~ " + pending.end()
                            + "\n\n" + sm.statusString()
                    ).setComponents(List.of()).queue();
                } else {
                    event.editMessage("❌ 슬롯 추가 실패. 시간 형식을 확인하세요.").setComponents(List.of()).queue();
                }
            }
            case "day" -> {
                DayOfWeek dow = DayOfWeek.valueOf(parts[2]);
                if (!sel.remove(dow)) sel.add(dow);
                updatePickerMessage(event, sessionId, pending, sel);
            }
            case "pre" -> {
                sel = switch (parts[2]) {
                    case "WEEKDAY" -> EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
                    case "WEEKEND" -> EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
                    case "ALL"     -> EnumSet.allOf(DayOfWeek.class);
                    default        -> new HashSet<>(); // CLEAR
                };
                updatePickerMessage(event, sessionId, pending, sel);
            }
        }
    }

    private void updatePickerMessage(ButtonInteractionEvent event, String sessionId,
                                     PendingScheduleAdd pending, Set<DayOfWeek> newSel) {
        pendingScheduleAdds.put(sessionId, new PendingScheduleAdd(newSel, pending.start(), pending.end()));
        String selStr = newSel.isEmpty() ? "_(없음)_" : "**" + formatDaysForManager(newSel) + "**";
        event.editMessage(
                "📅 **요일을 선택하세요** (" + pending.start() + " ~ " + pending.end() + ")\n"
                + "선택됨: " + selStr + "\n\n"
                + "요일 버튼을 눌러 켜고/끄세요. 완료 후 **✅ 추가** 를 누르세요."
        ).setComponents(buildDayPickerRows(sessionId, newSel)).queue();
    }

    /** ScheduleManager.parseDays() 가 인식하는 형식으로 변환 */
    private String formatDaysForManager(Set<DayOfWeek> days) {
        return ScheduleManager.formatDays(days);
    }

    // ── 설정 임베드 ──
    private MessageEmbed buildSettingEmbed() {
        ScheduleManager sm = plugin.getScheduleManager();
        ZonedDateTime now = ZonedDateTime.now(sm.getTimezone());
        String timeStr = now.format(DateTimeFormatter.ofPattern("MM/dd(E) HH:mm", Locale.KOREAN));
        boolean open = sm.isOpen();

        String stateStr = open ? "🟢 열림" : "🔴 닫힘";
        String detail = sm.isForceOpen() ? " (강제 열림)"
                : sm.isForceClose() ? " (강제 닫힘)"
                : sm.isEnabled() ? " (스케줄)" : " (스케줄 비활성)";

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🗓 서버 운영 스케줄 설정")
                .setColor(open ? new Color(0x57F287) : new Color(0xED4245))
                .addField("상태", stateStr + detail, true)
                .addField("현재 시각", timeStr, true)
                .addField("스케줄", sm.isEnabled() ? "✅ 활성화" : "❌ 비활성화", true);

        var slots = sm.getSlots();
        if (slots.isEmpty()) {
            eb.addField("운영 슬롯", "등록된 슬롯 없음\n➕ 버튼으로 추가하세요.", false);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < slots.size(); i++) {
                var s = slots.get(i);
                sb.append("`").append(i + 1).append(".` ")
                  .append(ScheduleManager.formatDays(s.days())).append("  ")
                  .append(s.start()).append(" ~ ").append(s.end()).append("\n");
            }
            eb.addField("운영 슬롯 (" + slots.size() + "개)", sb.toString(), false);
        }

        if (!open) {
            ZonedDateTime next = sm.getNextOpenTime();
            if (next != null) {
                Duration dur = Duration.between(now, next);
                long h = dur.toHours(), m = dur.toMinutesPart();
                String remaining = h > 0 ? h + "시간 " + m + "분" : m + "분";
                eb.addField("다음 오픈 예정",
                        next.format(DateTimeFormatter.ofPattern("MM/dd(E) HH:mm", Locale.KOREAN))
                        + " (" + remaining + " 후)", false);
            } else {
                eb.addField("다음 오픈 예정", "예정된 시간 없음", false);
            }
        }

        eb.setFooter("➕ 슬롯 추가 → 시간 입력 → 요일 버튼 선택  |  🗑 해당 슬롯 삭제");
        return eb.build();
    }

    private List<ActionRow> buildSettingRows() {
        ScheduleManager sm = plugin.getScheduleManager();
        var slots = sm.getSlots();

        // Row 1: 스케줄 ON/OFF
        Button onBtn  = Button.success("ss_on",  "✅ 스케줄 ON");
        Button offBtn = Button.danger("ss_off", "❌ 스케줄 OFF");
        if (sm.isEnabled())  onBtn  = onBtn.asDisabled();
        if (!sm.isEnabled()) offBtn = offBtn.asDisabled();

        // Row 2: 강제 오픈/닫기/해제
        Button foBtn    = Button.success("ss_fopen",  "🟢 강제 오픈");
        Button fcBtn    = Button.danger("ss_fclose", "🔴 강제 닫기");
        Button clearBtn = Button.secondary("ss_fclear", "↩ 강제 해제");
        if (sm.isForceOpen())  foBtn = foBtn.asDisabled();
        if (sm.isForceClose()) fcBtn = fcBtn.asDisabled();

        // Row 3: 슬롯 추가 + 새로고침 + 시트 동기화
        Button addBtn     = Button.primary("ss_add", "➕ 슬롯 추가");
        Button refreshBtn = Button.secondary("ss_refresh", "🔄 새로고침");
        boolean sheetsEnabled = plugin.getConfig().getBoolean("google.sheets.enabled", false);
        Button gsyncBtn   = Button.secondary("ss_gsync", "📊 시트 동기화");
        if (!sheetsEnabled) gsyncBtn = gsyncBtn.asDisabled();

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(onBtn, offBtn));
        rows.add(ActionRow.of(foBtn, fcBtn, clearBtn));
        rows.add(ActionRow.of(addBtn, refreshBtn, gsyncBtn));

        // Row 4(+5): 슬롯 삭제 버튼
        if (!slots.isEmpty()) {
            List<Button> delRow1 = new ArrayList<>(), delRow2 = new ArrayList<>();
            for (int i = 0; i < Math.min(slots.size(), 10); i++) {
                var s = slots.get(i);
                String label = "🗑 " + (i + 1) + ". "
                        + ScheduleManager.formatDays(s.days()) + " "
                        + s.start() + "~" + s.end();
                Button del = Button.danger("ss_del_" + (i + 1), label);
                if (i < 5) delRow1.add(del); else delRow2.add(del);
            }
            rows.add(ActionRow.of(delRow1));
            if (!delRow2.isEmpty()) rows.add(ActionRow.of(delRow2));
        }
        return rows;
    }

    private void handleSettingButton(ButtonInteractionEvent event) {
        if (!hasAdminRole(event.getMember())) {
            event.reply("❌ 관리자 역할이 필요합니다.").setEphemeral(true).queue();
            return;
        }
        String id = event.getComponentId();
        ScheduleManager sm = plugin.getScheduleManager();

        switch (id) {
            case "ss_on"      -> { sm.setEnabled(true);  refreshSetting(event); }
            case "ss_off"     -> { sm.setEnabled(false); refreshSetting(event); }
            case "ss_fopen"   -> { sm.setForceOpen(true);  refreshSetting(event); }
            case "ss_fclose"  -> { sm.setForceClose(true); refreshSetting(event); }
            case "ss_fclear"  -> { sm.clearForceFlags();   refreshSetting(event); }
            case "ss_refresh" -> refreshSetting(event);
            case "ss_gsync" -> {
                if (!plugin.getConfig().getBoolean("google.sheets.enabled", false)) {
                    event.reply("⚠ Google Sheets 연동이 비활성화되어 있습니다.").setEphemeral(true).queue();
                } else {
                    event.deferEdit().queue();
                    plugin.getSheetsManager().syncNow(() ->
                            event.getHook().editOriginalEmbeds(buildSettingEmbed())
                                    .setComponents(buildSettingRows()).queue()
                    );
                }
            }
            case "ss_add" -> {
                Modal modal = Modal.create("ss_time_modal", "슬롯 시간 설정")
                        .addActionRow(TextInput.create("start_time", "시작 시간 (HH:mm)", TextInputStyle.SHORT)
                                .setPlaceholder("예: 18:00").setMaxLength(5).setRequired(true).build())
                        .addActionRow(TextInput.create("end_time", "종료 시간 (HH:mm)", TextInputStyle.SHORT)
                                .setPlaceholder("예: 23:00").setMaxLength(5).setRequired(true).build())
                        .build();
                event.replyModal(modal).queue();
            }
            default -> {
                if (id.startsWith("ss_del_")) {
                    try {
                        int idx = Integer.parseInt(id.substring("ss_del_".length()));
                        if (sm.removeSlot(idx)) {
                            refreshSetting(event);
                        } else {
                            event.reply("❌ 잘못된 슬롯 번호입니다.").setEphemeral(true).queue();
                        }
                    } catch (NumberFormatException e) {
                        event.reply("❌ 오류가 발생했습니다.").setEphemeral(true).queue();
                    }
                }
            }
        }
    }

    private void refreshSetting(ButtonInteractionEvent event) {
        event.editMessageEmbeds(buildSettingEmbed())
                .setComponents(buildSettingRows())
                .queue();
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!event.getModalId().equals("ss_time_modal")) return;

        String start = event.getValue("start_time").getAsString().trim();
        String end   = event.getValue("end_time").getAsString().trim();

        if (!start.matches("\\d{1,2}:\\d{2}") || !end.matches("\\d{1,2}:\\d{2}")) {
            event.reply("❌ 시간 형식이 올바르지 않습니다. `HH:mm` 형식으로 입력하세요. (예: 18:00)")
                    .setEphemeral(true).queue();
            return;
        }

        // 요일 선택 UI 표시
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        pendingScheduleAdds.put(sessionId, new PendingScheduleAdd(new HashSet<>(), start, end));

        event.reply("📅 **요일을 선택하세요** (" + start + " ~ " + end + ")\n"
                + "선택됨: _(없음)_\n\n"
                + "요일 버튼을 눌러 켜고/끄세요. 완료 후 **✅ 추가** 를 누르세요.")
                .setComponents(buildDayPickerRows(sessionId, new HashSet<>()))
                .queue();
    }

    // ── 강제 오픈/클로즈 ──
    private void handleOpenNow(MessageReceivedEvent event, boolean open) {
        if (!hasAdminRole(event.getMember())) {
            event.getChannel().sendMessage("❌ 관리자 역할이 없어 사용할 수 없습니다.").queue();
            return;
        }
        ScheduleManager sm = plugin.getScheduleManager();
        if (open) {
            sm.setForceOpen(true);
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                            "🟢 관리자가 서버를 즉시 오픈했습니다.", NamedTextColor.GREEN)));
            event.getChannel().sendMessage("🟢 서버를 **강제 오픈**했습니다. (`!closenow` 또는 `!schedule on` 으로 해제 가능)").queue();
        } else {
            sm.setForceClose(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                var players = new java.util.ArrayList<>(Bukkit.getOnlinePlayers());
                for (var p : players) {
                    p.kick(net.kyori.adventure.text.Component.text(
                            "§c서버가 임시 점검 중입니다.\n§f잠시 후 다시 접속해주세요!"));
                }
            });
            event.getChannel().sendMessage("🔴 서버를 **강제 닫음** 처리했습니다. 접속 중인 플레이어는 모두 킥됩니다.").queue();
        }
    }

    // ── 전체 킥 ──
    private void handleKickAll(MessageReceivedEvent event) {
        if (!hasAdminRole(event.getMember())) {
            event.getChannel().sendMessage("❌ 관리자 역할이 없어 사용할 수 없습니다.").queue();
            return;
        }
        String executor = event.getMember() != null ? event.getMember().getEffectiveName() : event.getAuthor().getName();
        Bukkit.getScheduler().runTask(plugin, () -> {
            var players = new java.util.ArrayList<>(Bukkit.getOnlinePlayers());
            if (players.isEmpty()) {
                event.getChannel().sendMessage("⚠ 현재 접속 중인 플레이어가 없습니다.").queue();
                return;
            }
            for (var p : players) {
                p.kick(net.kyori.adventure.text.Component.text(
                        "§c[관리자] 서버 점검으로 인해 연결이 종료되었습니다."));
            }
            event.getChannel().sendMessage(
                    "✅ **" + players.size() + "명** 전체 킥 완료. (실행자: " + executor + ")").queue();
        });
    }

    // ── 패치노트 임베드 발송 (!patch / !changelog) ──
    private void handlePatchNote(MessageReceivedEvent event) {
        if (!hasAdminRole(event.getMember())) {
            event.getChannel().sendMessage("❌ 관리자 역할이 없어 사용할 수 없습니다.").queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📋 jeminSMPv1 패치노트 — v1.2")
                .setColor(new Color(0x5865F2))
                .addField("⚔️ 킬 스트릭 보상",
                        "3·5·10·15·20 연속킬 시 💎 다이아몬드 + XP 지급\nKDA에 따라 다이아 수량 자동 보정 (×0.75 ~ ×2.0)", false)
                .addField("💰 현상금 시스템",
                        "`/bounty <플레이어> <다이아>` 로 현상금 설정\n킬 시 합산 지급, 탭 목록에 💰 표시", false)
                .addField("💤 수면 투표",
                        "야간에 1명이 누우면 자동 투표 시작\n`[찬성]` `[반대]` 클릭 버튼, 과반수 시 즉시 낮", false)
                .addField("📜 발전과제 자동 칭호",
                        "10·25·50·75·100개 달성 시 칭호 자동 지급\n탐험가 → 모험가 → 탐구자 → 개척자 → 완성자", false)
                .addField("📈 /mystatus",
                        "플레이타임·킬·발전과제·칭호 현황을 한 번에 확인", false)
                .addField("🕐 서버 운영 스케줄 & 공허 대기열",
                        "운영 시간 외 접속 시 공허 대기 월드로 이동\n서버 오픈 시 대기열 순서대로 2초 간격 입장\n`!schedule setting` 으로 버튼 UI 설정", false)
                .addField("🎮 Discord 신규 명령어",
                        "`!schedule setting` — 버튼 UI 스케줄 관리\n"
                        + "`!opennow` / `!closenow` — 강제 오픈/닫기\n"
                        + "`!kicka` — 전체 킥\n"
                        + "`!patch` — 이 패치노트 발송", false)
                .setFooter("jeminSMPv1 v1.2 · 2026.05.12");

        var ch = discordBot.getEventsChannel();
        if (ch != null) {
            ch.sendMessageEmbeds(eb.build()).queue();
            event.getChannel().sendMessage("✅ 패치노트를 이벤트 채널에 발송했습니다.").queue();
        } else {
            event.getChannel().sendMessageEmbeds(eb.build()).queue();
        }

        // 인게임 방송
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.broadcast(Component.text(
                        "§9§l[업데이트] §r§fv1.2 패치노트가 공개되었습니다! 디스코드를 확인하세요.",
                        NamedTextColor.WHITE)));
    }

    // ── 업데이트 공지 ──
    private void handleUpdate(MessageReceivedEvent event, String args) {
        if (!hasAdminRole(event.getMember())) {
            event.getChannel().sendMessage("❌ 관리자 역할이 없어 사용할 수 없습니다.").queue();
            return;
        }
        if (args.isBlank()) {
            event.getChannel().sendMessage(
                    "❓ 사용법: `!update <제목> | <내용>`\n"
                    + "예시: `!update v1.2 패치 | - 현상금 시스템 추가\\n- 킬스트릭 보상 추가`"
            ).queue();
            return;
        }

        // `제목 | 내용` 형식 파싱 (| 없으면 제목=기본값)
        String title, body;
        if (args.contains("|")) {
            String[] split = args.split("\\|", 2);
            title = split[0].trim();
            body  = split[1].trim().replace("\\n", "\n");
        } else {
            title = "🆕 업데이트";
            body  = args.replace("\\n", "\n");
        }

        // Discord 임베드 전송 (이벤트 채널)
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🆕 " + title)
                .setDescription(body)
                .setColor(new Color(0x5865F2))
                .setFooter("jeminSMPv1 업데이트 공지");

        var ch = discordBot.getEventsChannel();
        if (ch != null) {
            ch.sendMessageEmbeds(eb.build()).queue();
            event.getChannel().sendMessage("✅ 업데이트 공지를 이벤트 채널에 전송했습니다.").queue();
        } else {
            event.getChannel().sendMessageEmbeds(eb.build()).queue();
        }

        // 인게임 방송
        String finalTitle = title;
        String finalBody  = body;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                    "§9§l[업데이트] §r§f" + finalTitle, NamedTextColor.WHITE));
            for (String line : finalBody.split("\n")) {
                Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                        "§7" + line, NamedTextColor.GRAY));
            }
        });
    }

    // ── 칭호 관리 ──
    private void handleTitle(MessageReceivedEvent event, String args) {
        if (args.isEmpty() || args.equalsIgnoreCase("list")) {
            sendTitleListEmbed(event);
            return;
        }
        // 아래는 관리자 전용
        if (!hasAdminRole(event.getMember())) {
            event.getChannel().sendMessage("❌ 관리자 역할이 없어 사용할 수 없습니다.").queue();
            return;
        }
        if (args.equalsIgnoreCase("color") || args.equalsIgnoreCase("colors") || args.equalsIgnoreCase("색상")) {
            sendColorEmbed(event);
            return;
        }
        if (args.startsWith("edit ")) {
            String rest = args.substring(5).trim();
            // edit <id> <price|display> <value...>
            String[] parts = rest.split(" ", 3);
            if (parts.length < 3) {
                event.getChannel().sendMessage(
                        "❓ 사용법:\n"
                        + "`title edit <id> price <가격💎>` — 가격 수정\n"
                        + "`title edit <id> display <표시>` — 표시명 수정"
                ).queue();
                return;
            }
            String id = parts[0];
            String field = parts[1];
            String value = parts[2];
            if (!field.equalsIgnoreCase("price") && !field.equalsIgnoreCase("display")) {
                event.getChannel().sendMessage("❌ field 는 `price` 또는 `display` 만 가능합니다.").queue();
                return;
            }
            if (plugin.getTitleManager().getTitle(id) == null) {
                event.getChannel().sendMessage("❌ 존재하지 않는 칭호 ID: `" + id + "`").queue();
                return;
            }
            if (plugin.getTitleManager().editTitle(id, field, value)) {
                String label = field.equalsIgnoreCase("price") ? "가격 💎" : "표시명";
                event.getChannel().sendMessage("✅ `" + id + "` 의 " + label + " → `" + value + "` 수정 완료!")
                        .queue(msg -> sendTitleListEmbed(event));
            } else {
                event.getChannel().sendMessage("❌ 수정 실패. 가격은 숫자여야 합니다.").queue();
            }
            return;
        }
        if (args.startsWith("add ")) {
            String rest = args.substring(4).trim();
            String[] parts = rest.split(" ", 3);
            if (parts.length < 3) {
                event.getChannel().sendMessage("❓ 사용법: `title add <id> <가격💎> <표시>`").queue();
                return;
            }
            long price;
            try { price = Long.parseLong(parts[1]); }
            catch (NumberFormatException e) {
                event.getChannel().sendMessage("❌ 가격은 숫자(다이아 개수)여야 합니다.").queue();
                return;
            }
            String id = parts[0];
            String display = parts[2];
            if (plugin.getTitleManager().createTitle(id, price, display)) {
                event.getChannel().sendMessage("✅ 칭호 `" + id + "` 추가 완료! (💎 " + price + "개, 표시: " + display + ")")
                        .queue(msg -> sendTitleListEmbed(event));
            } else {
                event.getChannel().sendMessage("❌ 이미 존재하는 칭호 ID입니다: `" + id + "`").queue();
            }
        } else if (args.startsWith("delete ")) {
            String id = args.substring(7).trim();
            if (plugin.getTitleManager().deleteTitle(id)) {
                event.getChannel().sendMessage("✅ 칭호 `" + id + "` 삭제 완료!")
                        .queue(msg -> sendTitleListEmbed(event));
            } else {
                event.getChannel().sendMessage("❌ 존재하지 않는 칭호 ID입니다: `" + id + "`").queue();
            }
        } else {
            event.getChannel().sendMessage(
                    "❓ 사용법:\n"
                    + "`title list` — 칭호 목록\n"
                    + "`title add <id> <가격> <표시>` — 칭호 추가\n"
                    + "`title delete <id>` — 칭호 삭제"
            ).queue();
        }
    }

    private void sendTitleListEmbed(MessageReceivedEvent event) {
        Collection<TitleInfo> allTitles = plugin.getTitleManager().getAllTitles();
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🏅 칭호 목록")
                .setColor(new Color(0xFFD700));

        var purchasable = allTitles.stream().filter(t -> !t.id().startsWith("auto_")).toList();
        var auto = allTitles.stream().filter(t -> t.id().startsWith("auto_")).toList();

        if (purchasable.isEmpty() && auto.isEmpty()) {
            eb.setDescription("등록된 칭호가 없습니다.");
        } else {
            if (!purchasable.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (TitleInfo t : purchasable) {
                    // strip color codes (& or §) for Discord display
                    String plain = t.display().replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
                    sb.append("`").append(t.id()).append("` — ")
                      .append(plain).append("  💎 **").append(t.price()).append("개**\n");
                }
                eb.addField("💰 구매 가능 칭호 (`/title buy <id>`)", sb.toString(), false);
            }
            if (!auto.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (TitleInfo t : auto) {
                    String plain = t.display().replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
                    sb.append("`").append(t.id()).append("` — ").append(plain).append("\n");
                }
                eb.addField("🤖 자동 부여 칭호 (킬/플레이타임 달성 시)", sb.toString(), false);
            }
        }
        eb.setFooter("!title add <id> <가격💎> <표시>  |  !title edit <id> price/display <값>  |  !title delete <id>");
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    private void sendColorEmbed(MessageReceivedEvent event) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🎨 Minecraft 색상코드 (& 기호 사용)")
                .setColor(new Color(0x00BFFF))
                .setDescription(
                    "칭호 표시명에 아래 코드를 붙여 쓰세요.\n"
                    + "예) `!title add vip 10 &6[VIP]` → **금색 [VIP]**\n​"
                )
                .addField("🌈 색상",
                    "`&0` ⬛ 검정\n"
                    + "`&1` 🟦 남색\n"
                    + "`&2` 🟩 초록\n"
                    + "`&3` 🩵 청록\n"
                    + "`&4` 🟥 빨강\n"
                    + "`&5` 🟣 보라\n"
                    + "`&6` 🟡 금색\n"
                    + "`&7` ⬜ 회색\n"
                    + "`&8` 🩶 진회색\n"
                    + "`&9` 🔵 파랑\n"
                    + "`&a` 💚 밝은초록\n"
                    + "`&b` 🩵 하늘\n"
                    + "`&c` ❤️ 밝은빨강\n"
                    + "`&d` 🌸 분홍\n"
                    + "`&e` 💛 노랑\n"
                    + "`&f` 🤍 흰색",
                    true
                )
                .addField("✨ 서식",
                    "`&l` **굵게**\n"
                    + "`&o` *기울임*\n"
                    + "`&n` __밑줄__\n"
                    + "`&m` ~~취소선~~\n"
                    + "`&k` 랜덤 글자 (obf)\n"
                    + "`&r` 초기화",
                    true
                )
                .setFooter("색상코드는 조합 가능 — 예) &l&6 → 굵은 금색");
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    private void handleAnnounce(MessageReceivedEvent event, String args) {
        if (!hasAdminRole(event.getMember())) {
            event.getChannel().sendMessage("❌ 관리자 역할이 없어 사용할 수 없습니다.").queue();
            return;
        }

        var manager = plugin.getAnnouncementManager();

        if (args.equalsIgnoreCase("list") || args.isEmpty()) {
            var list = manager.getMessages();
            if (list.isEmpty()) {
                event.getChannel().sendMessage("📋 등록된 공지가 없습니다.").queue();
                return;
            }
            StringBuilder sb = new StringBuilder("**📋 공지 목록**\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append("`").append(i + 1).append(".` ").append(list.get(i)).append("\n");
            }
            event.getChannel().sendMessage(sb.toString()).queue();

        } else if (args.startsWith("add ")) {
            String message = args.substring(4).trim();
            if (message.isEmpty()) {
                event.getChannel().sendMessage("❓ 사용법: `announce add <내용>`").queue();
                return;
            }
            manager.addMessage(message);
            event.getChannel().sendMessage("✅ 공지 추가 완료: `" + message + "`").queue();

        } else if (args.startsWith("remove ")) {
            try {
                int idx = Integer.parseInt(args.substring(7).trim());
                if (manager.removeMessage(idx)) {
                    event.getChannel().sendMessage("✅ " + idx + "번 공지 삭제 완료.").queue();
                } else {
                    event.getChannel().sendMessage("❌ 잘못된 번호입니다. `announce list` 로 확인하세요.").queue();
                }
            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("❓ 사용법: `announce remove <번호>`").queue();
            }
        } else {
            event.getChannel().sendMessage("❓ 사용법: `announce list` / `announce add <내용>` / `announce remove <번호>`").queue();
        }
    }

    private void handleWarn(MessageReceivedEvent event, String input) {
        if (!hasAdminRole(event.getMember())) {
            event.getChannel().sendMessage("❌ 관리자 역할이 없어 사용할 수 없습니다.").queue();
            return;
        }
        var wm = plugin.getWarnManager();
        String[] parts = input.split("\\s+", 3);
        String cmd = parts[0].toLowerCase();

        // !warnings [플레이어]
        if (cmd.equals("warnings")) {
            String targetName = parts.length > 1 ? parts[1] : null;
            if (targetName == null) {
                event.getChannel().sendMessage(
                        "❓ 사용법: `!warnings <플레이어>`").queue();
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                var uuid = wm.findUUID(targetName);
                if (uuid == null) {
                    event.getChannel().sendMessage("❌ 플레이어를 찾을 수 없습니다.").queue();
                    return;
                }
                event.getChannel().sendMessage(wm.formatWarnings(uuid, targetName)).queue();
            });
            return;
        }

        // !warn <플레이어> [사유]
        if (cmd.equals("warn")) {
            if (parts.length < 2) {
                event.getChannel().sendMessage("❓ 사용법: `!warn <플레이어> [사유]`").queue();
                return;
            }
            String targetName = parts[1];
            String reason = parts.length > 2 ? parts[2] : "사유 없음";
            int threshold = plugin.getConfig().getInt("warn.auto-punish-count", 3);
            Bukkit.getScheduler().runTask(plugin, () -> {
                var uuid = wm.findUUID(targetName);
                if (uuid == null) {
                    event.getChannel().sendMessage("❌ 플레이어를 찾을 수 없습니다.").queue();
                    return;
                }
                int count = wm.addWarning(uuid, reason, event.getAuthor().getName());
                String msg = "⚠ **" + targetName + "** 에게 경고 부여 (" + count + "/" + threshold + ")\n"
                        + "사유: " + reason;
                if (count >= threshold) msg += "\n🔴 경고 누적 — 대기열 징계 자동 적용됨";
                event.getChannel().sendMessage(msg).queue();
            });
            return;
        }

        // !unwarn <플레이어> [번호]
        if (cmd.equals("unwarn")) {
            if (parts.length < 2) {
                event.getChannel().sendMessage("❓ 사용법: `!unwarn <플레이어> [번호]`").queue();
                return;
            }
            String targetName = parts[1];
            Bukkit.getScheduler().runTask(plugin, () -> {
                var uuid = wm.findUUID(targetName);
                if (uuid == null) {
                    event.getChannel().sendMessage("❌ 플레이어를 찾을 수 없습니다.").queue();
                    return;
                }
                if (parts.length >= 3) {
                    try {
                        int idx = Integer.parseInt(parts[2]);
                        if (wm.removeWarning(uuid, idx)) {
                            event.getChannel().sendMessage("✅ **" + targetName + "** 의 " + idx + "번 경고 삭제 완료.").queue();
                        } else {
                            event.getChannel().sendMessage("❌ 잘못된 번호입니다.").queue();
                        }
                    } catch (NumberFormatException e) {
                        event.getChannel().sendMessage("❌ 숫자를 입력하세요.").queue();
                    }
                } else {
                    wm.clearWarnings(uuid);
                    event.getChannel().sendMessage("✅ **" + targetName + "** 의 경고 전부 삭제 완료.").queue();
                }
            });
            return;
        }

        // !punish <플레이어>
        if (cmd.equals("punish")) {
            if (parts.length < 2) {
                event.getChannel().sendMessage("❓ 사용법: `!punish <플레이어>`").queue();
                return;
            }
            String targetName = parts[1];
            Bukkit.getScheduler().runTask(plugin, () -> {
                var uuid = wm.findUUID(targetName);
                if (uuid == null) {
                    event.getChannel().sendMessage("❌ 플레이어를 찾을 수 없습니다.").queue();
                    return;
                }
                wm.setPunished(uuid, true, false);
                event.getChannel().sendMessage("🔴 **" + targetName + "** 에게 대기열 징계를 적용했습니다.").queue();
            });
            return;
        }

        // !unpunish <플레이어>
        if (cmd.equals("unpunish")) {
            if (parts.length < 2) {
                event.getChannel().sendMessage("❓ 사용법: `!unpunish <플레이어>`").queue();
                return;
            }
            String targetName = parts[1];
            Bukkit.getScheduler().runTask(plugin, () -> {
                var uuid = wm.findUUID(targetName);
                if (uuid == null) {
                    event.getChannel().sendMessage("❌ 플레이어를 찾을 수 없습니다.").queue();
                    return;
                }
                wm.setPunished(uuid, false, true);
                event.getChannel().sendMessage("✅ **" + targetName + "** 의 징계를 해제했습니다.").queue();
            });
        }
    }

    // ── 보상 관리 ──
    private void handleComp(MessageReceivedEvent event, String args) {
        if (!hasAdminRole(event.getMember())) {
            event.getChannel().sendMessage("❌ 관리자 역할이 없어 사용할 수 없습니다.").queue();
            return;
        }

        CompensationManager cm = plugin.getCompensationManager();

        // !comp list
        if (args.isEmpty() || args.equalsIgnoreCase("list")) {
            var all = cm.getAllEvents();
            if (all.isEmpty()) {
                event.getChannel().sendMessage("📦 등록된 보상이 없습니다.").queue();
                return;
            }
            StringBuilder sb = new StringBuilder("**📦 보상 목록**\n");
            long now = System.currentTimeMillis();
            for (var ev : all) {
                boolean expired = ev.expiresAt() != -1 && now > ev.expiresAt();
                sb.append(expired ? "~~`" : "`").append(ev.id()).append("`")
                  .append(expired ? "~~ (만료)" : "")
                  .append("  ").append(ev.description()).append("\n")
                  .append("  보상: ").append(cm.formatRewards(ev))
                  .append("  ·  ").append(cm.formatExpiry(ev.expiresAt()))
                  .append("  ·  수령: ").append(cm.claimedCount(ev.id())).append("명\n");
            }
            event.getChannel().sendMessage(sb.toString().trim()).queue();
            return;
        }

        // !comp info <id>
        if (args.startsWith("info ")) {
            String id = args.substring(5).trim();
            var ev = cm.getEvent(id);
            if (ev == null) {
                event.getChannel().sendMessage("❌ 존재하지 않는 보상 ID: `" + id + "`").queue();
                return;
            }
            event.getChannel().sendMessage(
                    "**📦 보상 정보 — `" + ev.id() + "`**\n"
                    + "설명: " + ev.description() + "\n"
                    + "보상: " + cm.formatRewards(ev) + "\n"
                    + "기간: " + cm.formatExpiry(ev.expiresAt()) + "\n"
                    + "수령자: " + cm.claimedCount(ev.id()) + "명"
            ).queue();
            return;
        }

        // !comp delete <id>
        if (args.startsWith("delete ") || args.startsWith("del ")) {
            String id = args.contains(" ") ? args.substring(args.indexOf(' ') + 1).trim() : "";
            if (cm.delete(id)) {
                event.getChannel().sendMessage("✅ 보상 `" + id + "` 삭제 완료.").queue();
            } else {
                event.getChannel().sendMessage("❌ 존재하지 않는 보상 ID: `" + id + "`").queue();
            }
            return;
        }

        // !comp create <id> <기간> <설명> | <보상>
        // 예) !comp create bug_fix 7d 5월 버그 보상 | diamond:32,balance:50,cooked_beef:64
        if (args.startsWith("create ")) {
            String rest = args.substring(7).trim();
            // 첫 번째 공백 = id, 두 번째 공백 = 기간, 그 이후 "|" 로 설명/보상 분리
            String[] firstTwo = rest.split("\\s+", 3);
            if (firstTwo.length < 3) {
                event.getChannel().sendMessage(
                        "❓ 사용법:\n"
                        + "`!comp create <id> <기간> <설명> | <보상목록>`\n\n"
                        + "**기간:** `7d` `24h` `30m` `무기한`\n"
                        + "**보상목록:** `diamond:32,balance:50,cooked_beef:64`\n"
                        + "**balance** = 경제 잔액\n\n"
                        + "예시:\n"
                        + "`!comp create bug_fix 7d 5월 버그 보상 | diamond:32,balance:50`"
                ).queue();
                return;
            }

            String id       = firstTwo[0];
            String duration = firstTwo[1];
            String rest2    = firstTwo[2];

            // 설명 | 보상 분리
            String description, rewardStr;
            if (rest2.contains("|")) {
                String[] split = rest2.split("\\|", 2);
                description = split[0].trim();
                rewardStr   = split[1].trim();
            } else {
                event.getChannel().sendMessage(
                        "❌ 보상 목록을 `|` 로 구분해주세요.\n"
                        + "예) `!comp create bug_fix 7d 버그 보상 | diamond:32,balance:50`"
                ).queue();
                return;
            }

            long expiresAt = CompensationManager.parseDuration(duration);

            // balance 파싱
            long balance = 0;
            StringBuilder itemStr = new StringBuilder();
            for (String part : rewardStr.split(",")) {
                part = part.trim();
                if (part.startsWith("balance:")) {
                    try { balance = Long.parseLong(part.substring(8).trim()); } catch (NumberFormatException ignored) {}
                } else {
                    if (!itemStr.isEmpty()) itemStr.append(",");
                    itemStr.append(part);
                }
            }
            var items = CompensationManager.parseItems(itemStr.toString());

            if (balance == 0 && items.isEmpty()) {
                event.getChannel().sendMessage(
                        "❌ 보상 내용을 인식할 수 없습니다.\n"
                        + "형식: `diamond:32,balance:50,cooked_beef:64`\n"
                        + "아이템 이름은 마인크래프트 영문 이름 (예: `golden_apple`, `iron_ingot`)"
                ).queue();
                return;
            }

            if (cm.create(id, expiresAt, description, balance, items)) {
                var ev = cm.getEvent(id);
                event.getChannel().sendMessage(
                        "✅ **보상 등록 완료!**\n"
                        + "ID: `" + id + "`\n"
                        + "설명: " + description + "\n"
                        + "보상: " + cm.formatRewards(ev) + "\n"
                        + "기간: " + cm.formatExpiry(expiresAt) + "\n\n"
                        + "플레이어는 인게임에서 `/claim` 으로 수령할 수 있습니다."
                ).queue();
            } else {
                event.getChannel().sendMessage("❌ 이미 존재하는 보상 ID입니다: `" + id + "`").queue();
            }
            return;
        }

        event.getChannel().sendMessage(
                "❓ **보상 명령어**\n"
                + "`!comp list` — 보상 목록\n"
                + "`!comp info <id>` — 보상 상세\n"
                + "`!comp create <id> <기간> <설명> | <보상>` — 보상 등록\n"
                + "`!comp delete <id>` — 보상 삭제\n\n"
                + "**기간 예시:** `7d` `24h` `30m` `무기한`\n"
                + "**보상 예시:** `diamond:32,balance:50,cooked_beef:64`"
        ).queue();
    }

    // ── 마크 ↔ 디스코드 연동 ──
    private void handleLink(MessageReceivedEvent event, String input) {
        var lm = plugin.getLinkManager();
        String discordId = event.getAuthor().getId();
        String discordUsername = event.getMember() != null
                ? event.getMember().getEffectiveName() : event.getAuthor().getName();

        // !unlink
        if (input.equalsIgnoreCase("unlink")) {
            if (lm.unlinkByDiscord(discordId)) {
                event.getChannel().sendMessage("✅ **" + discordUsername + "** 의 마인크래프트 연동이 해제되었습니다.").queue();
            } else {
                event.getChannel().sendMessage("❌ 연동된 마인크래프트 계정이 없습니다.").queue();
            }
            return;
        }

        // !link [마크닉네임]
        String[] parts = input.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            // 현재 연동 상태 확인
            String mcName = lm.getMinecraftName(discordId);
            if (mcName != null) {
                event.getChannel().sendMessage(
                        "✅ 이미 **" + mcName + "** 과 연동되어 있습니다.\n`!unlink` 로 해제 가능합니다.").queue();
            } else {
                event.getChannel().sendMessage(
                        "❓ 사용법: `!link <마인크래프트 닉네임>`\n"
                        + "예) `!link Steve`\n\n"
                        + "6자리 코드를 발급받아 마인크래프트에서 `/link <코드>` 를 입력하세요.").queue();
            }
            return;
        }

        String mcName = parts[1].trim();

        // 이미 연동된 경우
        if (lm.getMinecraftName(discordId) != null) {
            event.getChannel().sendMessage(
                    "❌ 이미 **" + lm.getMinecraftName(discordId) + "** 과 연동되어 있습니다.\n"
                    + "`!unlink` 로 먼저 해제하세요.").queue();
            return;
        }

        // 코드 발급
        String code = lm.generateCode(discordId, discordUsername);
        if (code == null) {
            event.getChannel().sendMessage("❌ 이미 연동된 디스코드 계정입니다.").queue();
            return;
        }

        final String finalMcName = mcName;
        final String finalCode   = code;

        // 원본 메시지 삭제 (코드 노출 방지)
        event.getMessage().delete().queue(null, err -> {});

        // DM으로 코드 전송
        event.getAuthor().openPrivateChannel().queue(dm -> {
            dm.sendMessage(
                    "🔗 **연동 코드 발급 완료!**\n"
                    + "마인크래프트 **" + finalMcName + "** 계정으로 접속한 뒤,\n"
                    + "채팅창에 아래 명령어를 입력하세요:\n\n"
                    + "```/link " + finalCode + "```\n"
                    + "⏱ 코드는 **5분** 이내에 입력해야 합니다."
            ).queue(
                    msg -> event.getChannel().sendMessage(
                            "🔗 **" + discordUsername + "** 님, 연동 코드를 DM으로 전송했습니다!").queue(),
                    err -> event.getChannel().sendMessage(
                            "❌ DM 전송에 실패했습니다. DM을 허용해주세요.\n"
                            + "설정 → 개인정보 및 안전 → 서버 멤버 DM 허용").queue()
            );
        });
    }

    private void handleOnline(MessageReceivedEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            var waitingManager = plugin.getScheduleManager().getWaitingManager();
            List<UUID> queue = waitingManager.getQueueOrder();
            String waitingWorldName = "jeminsmp_waiting";

            List<String> online = new ArrayList<>();
            List<String> waiting = new ArrayList<>();

            for (var p : Bukkit.getOnlinePlayers()) {
                boolean inQueue = queue.contains(p.getUniqueId());
                boolean inWaitingWorld = p.getWorld().getName().equals(waitingWorldName);
                if (inQueue || inWaitingWorld) {
                    int pos = queue.indexOf(p.getUniqueId()) + 1;
                    waiting.add((pos > 0 ? pos + "번 " : "") + p.getName());
                } else {
                    online.add(p.getName());
                }
            }

            StringBuilder sb = new StringBuilder();
            if (online.isEmpty() && waiting.isEmpty()) {
                sb.append("🔕 현재 접속 중인 플레이어가 없습니다.");
            } else {
                if (!online.isEmpty()) {
                    sb.append("🟢 **인게임 (").append(online.size()).append("명)**\n");
                    online.forEach(n -> sb.append("• ").append(n).append("\n"));
                }
                if (!waiting.isEmpty()) {
                    if (!online.isEmpty()) sb.append("\n");
                    sb.append("⏳ **대기열 (").append(waiting.size()).append("명)**\n");
                    waiting.forEach(n -> sb.append("• ").append(n).append("\n"));
                }
            }
            event.getChannel().sendMessage(sb.toString().trim()).queue();
        });
    }

    private void handleRun(MessageReceivedEvent event, String mcCommand) {
        if (!hasAdminRole(event.getMember())) {
            event.getChannel().sendMessage("❌ 관리자 역할이 없어 명령어를 실행할 수 없습니다.").queue();
            return;
        }
        if (mcCommand.isBlank()) {
            event.getChannel().sendMessage("❓ 사용법: `" + plugin.getConfig().getString("discord.command-prefix", "!") + "run <명령어>`").queue();
            return;
        }
        String executor = event.getMember() != null ? event.getMember().getEffectiveName() : event.getAuthor().getName();
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), mcCommand);
            String result = success
                    ? "✅ **명령어 실행 완료**\n> `/" + mcCommand + "`\n> 실행자: " + executor
                    : "❌ **명령어 실행 실패**\n> `/" + mcCommand + "`";
            event.getChannel().sendMessage(result).queue();
        });
    }

    private boolean hasAdminRole(Member member) {
        if (member == null) return false;
        String adminRoleId = plugin.getConfig().getString("discord.admin-role-id", "");
        if (adminRoleId.isBlank() || adminRoleId.equals("YOUR_ADMIN_ROLE_ID_HERE"))
            return member.isOwner();
        return member.getRoles().stream().anyMatch(role -> role.getId().equals(adminRoleId));
    }

    // ── !season ──
    private void handleSeason(MessageReceivedEvent event, String args) {
        if (!hasAdminRole(event.getMember())) {
            // 일반 조회는 누구나 가능
            if (args.isBlank() || args.equalsIgnoreCase("info") || args.equalsIgnoreCase("top")) {
                var sm = plugin.getSeasonManager();
                if (sm == null) { event.getChannel().sendMessage("❌ 시즌 매니저 없음").queue(); return; }
                if (!sm.isActive()) {
                    event.getChannel().sendMessage("📋 현재 진행 중인 시즌이 없습니다.").queue();
                } else {
                    long days = (System.currentTimeMillis() - sm.getStartedAt()) / 86400000L;
                    event.getChannel().sendMessage(
                            "🏆 **" + sm.getCurrentName() + "** 진행 중 (" + days + "일 경과)").queue();
                }
                return;
            }
            event.getChannel().sendMessage("❌ 관리자 전용 명령어입니다.").queue();
            return;
        }

        var sm = plugin.getSeasonManager();
        var seasonCmd = plugin.getSeasonCommand();
        if (sm == null || seasonCmd == null) { event.getChannel().sendMessage("❌ 시즌 매니저 없음").queue(); return; }

        if (args.isBlank() || args.equalsIgnoreCase("info")) {
            if (!sm.isActive()) {
                event.getChannel().sendMessage("📋 현재 진행 중인 시즌이 없습니다.").queue();
            } else {
                long days = (System.currentTimeMillis() - sm.getStartedAt()) / 86400000L;
                event.getChannel().sendMessage(
                        "🏆 **" + sm.getCurrentName() + "** 진행 중 (" + days + "일 경과)").queue();
            }
        } else if (args.equalsIgnoreCase("end")) {
            if (!sm.isActive()) { event.getChannel().sendMessage("❌ 진행 중인 시즌이 없습니다.").queue(); return; }
            String ending = sm.getCurrentName();
            Bukkit.getScheduler().runTask(plugin, () -> seasonCmd.doEnd(sm));
            event.getChannel().sendMessage("🏁 **" + ending + "** 종료 처리 중...").queue();
        } else {
            // !season start [이름] or !season 시즌1
            String name = args.startsWith("start") ? args.substring("start".length()).trim() : args;
            if (name.isBlank()) name = null;
            final String finalName = name;
            Bukkit.getScheduler().runTask(plugin, () -> {
                seasonCmd.doStart(sm, finalName);
                event.getChannel().sendMessage("✅ **" + sm.getCurrentName() + "** 시작!").queue();
            });
        }
    }

    // ── !startsmp [시즌이름] ──
    private void handleStartSmp(MessageReceivedEvent event, String args) {
        if (!hasAdminRole(event.getMember())) {
            event.getChannel().sendMessage("❌ 관리자 전용 명령어입니다.").queue();
            return;
        }
        event.getChannel().sendMessage("🎮 SMP 시작 중...").queue();
        Bukkit.getScheduler().runTask(plugin, () -> {
            // startSMP 명령어 실행 (콘솔로)
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "startSMP");
        });
    }
}
