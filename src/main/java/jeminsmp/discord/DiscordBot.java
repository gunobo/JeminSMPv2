package jeminsmp.discord;

import jeminsmp.JeminSMPPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class DiscordBot {

    private final JeminSMPPlugin plugin;
    private JDA jda;

    private volatile TextChannel eventsChannel;
    private volatile TextChannel consoleChannel;

    public DiscordBot(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        String token = plugin.getConfig().getString("discord.bot-token", "");
        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new DiscordListener(plugin, this))
                    .build();
        } catch (Exception e) {
            plugin.getLogger().severe("Discord 봇 초기화 실패: " + e.getMessage());
        }
    }

    public void onReady(JDA readyJda) {
        String eventsId  = plugin.getConfig().getString("discord.channels.events", "");
        String consoleId = plugin.getConfig().getString("discord.channels.console", "");
        eventsChannel = readyJda.getChannelById(TextChannel.class, eventsId);
        if (eventsChannel == null) plugin.getLogger().warning("이벤트 채널을 찾을 수 없습니다 (discord.channels.events=" + eventsId + ")");
        else plugin.getLogger().info("이벤트 채널: #" + eventsChannel.getName());
        consoleChannel = readyJda.getChannelById(TextChannel.class, consoleId);
        if (consoleChannel == null) plugin.getLogger().warning("콘솔 채널을 찾을 수 없습니다 (discord.channels.console=" + consoleId + ")");
        else plugin.getLogger().info("콘솔 채널: #" + consoleChannel.getName());
        if (eventsChannel != null && plugin.getConfig().getBoolean("discord.log.server-start", true))
            sendEvent("🟢 **서버가 시작되었습니다.**");
    }

    public void sendEvent(String message) {
        if (eventsChannel != null) eventsChannel.sendMessage(message).queue(null,
                err -> plugin.getLogger().warning("Discord 이벤트 전송 실패: " + err.getMessage()));
    }

    public void sendShutdownMessage() {
        if (eventsChannel != null) {
            try { eventsChannel.sendMessage("🔴 **서버가 종료되었습니다.**").complete(); } catch (Exception ignored) {}
        }
    }

    public void sendConsoleLog(String message) {
        if (consoleChannel != null) consoleChannel.sendMessage(message).queue(null, err -> {});
    }

    public TextChannel getEventsChannel() { return eventsChannel; }
    public JDA getJda() { return jda; }

    public void shutdown() {
        if (jda == null) return;
        jda.shutdownNow();
        try {
            // JDA 내부 웹소켓 스레드가 완전히 죽기 전에 Paper가 플러그인 jar를 닫아버리면
            // 그 스레드가 나중에 클래스를 로드하려다 "zip file closed" 에러를 냄. 완전히 죽을 때까지 대기.
            jda.awaitShutdown(java.time.Duration.ofSeconds(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
