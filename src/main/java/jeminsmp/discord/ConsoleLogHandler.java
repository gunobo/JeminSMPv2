package jeminsmp.discord;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class ConsoleLogHandler extends Handler {

    private static final int MAX_CHARS = 1800;
    private static final int MAX_LINES = 15;

    private final DiscordBot discordBot;
    private final Level minLevel;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    public ConsoleLogHandler(DiscordBot discordBot, Level minLevel) {
        this.discordBot = discordBot;
        this.minLevel = minLevel;
    }

    @Override
    public void publish(LogRecord record) {
        if (record.getLevel().intValue() < minLevel.intValue()) return;
        String loggerName = record.getLoggerName();
        if (loggerName != null && (loggerName.startsWith("net.dv8tion")
                || loggerName.startsWith("okhttp3") || loggerName.startsWith("okio"))) return;
        String raw = record.getMessage();
        if (raw == null || raw.isBlank()) return;
        String clean = raw.replaceAll("\\[[;\\d]*[ -/]*[@-~]", "").trim();
        if (clean.isEmpty()) return;
        String emoji = record.getLevel().intValue() >= Level.SEVERE.intValue() ? "❌"
                     : record.getLevel().intValue() >= Level.WARNING.intValue() ? "⚠️" : "📋";
        queue.offer(emoji + " " + clean);
    }

    public void flushToDiscord() {
        if (queue.isEmpty()) return;
        StringBuilder sb = new StringBuilder("```\n");
        int lines = 0;
        String msg;
        while ((msg = queue.peek()) != null) {
            String stripped = msg.replaceAll("[❌⚠️📋] ", "");
            if (sb.length() + stripped.length() + 4 > MAX_CHARS || lines >= MAX_LINES) break;
            queue.poll();
            sb.append(stripped).append("\n");
            lines++;
        }
        if (lines == 0) return;
        sb.append("```");
        discordBot.sendConsoleLog(sb.toString());
    }

    @Override public void flush() {}
    @Override public void close() { queue.clear(); }
}
