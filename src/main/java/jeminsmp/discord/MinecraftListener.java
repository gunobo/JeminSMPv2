package jeminsmp.discord;

import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.event.player.AsyncChatEvent;
import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MinecraftListener implements Listener {

    private final JeminSMPPlugin plugin;
    private final DiscordBot discordBot;

    public MinecraftListener(JeminSMPPlugin plugin, DiscordBot discordBot) {
        this.plugin = plugin;
        this.discordBot = discordBot;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("discord.log.join", true)) return;
        discordBot.sendEvent("✅ **" + event.getPlayer().getName() + "** 님이 접속했습니다.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("discord.log.quit", true)) return;
        discordBot.sendEvent("🚪 **" + event.getPlayer().getName() + "** 님이 퇴장했습니다.");
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("discord.log.chat", true)) return;
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        // 닉네임
        String nick = plugin.getNickManager().hasNick(player.getUniqueId())
                ? stripColor(plugin.getNickManager().getRawNick(player.getUniqueId()))
                : player.getName();

        // 칭호 (색상코드 제거)
        String equippedDisplay = plugin.getTitleManager().getEquippedDisplay(player.getUniqueId());
        String titlePrefix = equippedDisplay != null
                ? stripColor(equippedDisplay) + " "
                : "";

        discordBot.sendEvent("💬 " + titlePrefix + "**" + nick + "**: " + message);
    }

    /** &코드, §코드 제거 */
    private String stripColor(String s) {
        return s.replaceAll("(?i)[&§][0-9a-fk-or]", "");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("discord.log.death", true)) return;
        if (event.deathMessage() == null) return;
        String deathMsg = PlainTextComponentSerializer.plainText().serialize(event.deathMessage());
        discordBot.sendEvent("💀 " + deathMsg);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        // 도전과제 자동 칭호 체크
        if (plugin.getAutoTitleManager() != null)
            plugin.getAutoTitleManager().checkAdvancements(event.getPlayer());

        if (!plugin.getConfig().getBoolean("discord.log.advancement", true)) return;
        Advancement adv = event.getAdvancement();
        if (adv.getKey().getKey().startsWith("recipes/")) return;
        AdvancementDisplay display = adv.getDisplay();
        if (display == null) return;
        String title = PlainTextComponentSerializer.plainText().serialize(display.title());
        String desc  = PlainTextComponentSerializer.plainText().serialize(display.description());
        String icon  = display.frame() == AdvancementDisplay.Frame.CHALLENGE ? "⚔"
                     : display.frame() == AdvancementDisplay.Frame.GOAL      ? "🎯" : "📜";
        discordBot.sendEvent(icon + " **" + event.getPlayer().getName() + "** 님이 **[" + title + "]** 달성!\n> " + desc);
    }
}
