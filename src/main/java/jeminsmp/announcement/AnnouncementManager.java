package jeminsmp.announcement;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AnnouncementManager {

    private final JeminSMPPlugin plugin;
    private int index = 0;

    public AnnouncementManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        start();
    }

    private void start() {
        if (!plugin.getConfig().getBoolean("announcements.enabled", true)) return;
        int minutes = plugin.getConfig().getInt("announcements.interval-minutes", 5);
        long ticks = minutes * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::broadcast, ticks, ticks);
    }

    private void broadcast() {
        List<String> messages = getMessages();
        if (messages.isEmpty()) return;
        String raw = messages.get(index % messages.size());
        index++;
        Component msg = LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
        Bukkit.broadcast(msg);
    }

    // ── Discord에서 호출하는 메서드들 ──

    public List<String> getMessages() {
        return new ArrayList<>(plugin.getConfig().getStringList("announcements.messages"));
    }

    public void addMessage(String message) {
        List<String> list = getMessages();
        list.add(message);
        saveMessages(list);
    }

    public boolean removeMessage(int index) {
        List<String> list = getMessages();
        if (index < 1 || index > list.size()) return false;
        list.remove(index - 1);
        saveMessages(list);
        return true;
    }

    private void saveMessages(List<String> list) {
        // saveConfig()는 반드시 메인 스레드에서 실행
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getConfig().set("announcements.messages", list);
            plugin.saveConfig();
        });
    }
}
