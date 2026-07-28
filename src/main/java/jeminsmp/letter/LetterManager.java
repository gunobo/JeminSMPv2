package jeminsmp.letter;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LetterManager {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JeminSMPPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public LetterManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "letters.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void sendLetter(String senderName, UUID recipientUUID, String recipientName, String message) {
        String sentAt = LocalDateTime.now().format(FORMATTER);
        Player online = Bukkit.getPlayer(recipientUUID);
        if (online != null && online.isOnline()) {
            deliverLetter(online, senderName, message, sentAt);
        } else {
            savePending(recipientUUID, senderName, message, sentAt);
        }
    }

    public void deliverPending(Player player) {
        List<LetterData> pending = getPending(player.getUniqueId());
        if (pending.isEmpty()) return;

        for (LetterData letter : pending) {
            deliverLetter(player, letter.sender(), letter.message(), letter.sentAt());
        }

        // Clear pending
        config.set("pending." + player.getUniqueId().toString(), null);
        save();
    }

    public List<LetterData> getPending(UUID uuid) {
        List<LetterData> result = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("pending." + uuid.toString());
        if (section == null) return result;

        List<?> letters = config.getList("pending." + uuid.toString());
        if (letters == null) return result;

        for (Object obj : letters) {
            if (obj instanceof java.util.Map<?, ?> map) {
                String sender = String.valueOf(map.get("sender"));
                String message = String.valueOf(map.get("message"));
                String sentAt = String.valueOf(map.get("sentAt"));
                result.add(new LetterData(sender, message, sentAt));
            }
        }
        return result;
    }

    private void savePending(UUID recipientUUID, String senderName, String message, String sentAt) {
        String path = "pending." + recipientUUID.toString();
        List<java.util.Map<String, String>> letters = new ArrayList<>();

        // Load existing
        List<?> existing = config.getList(path);
        if (existing != null) {
            for (Object obj : existing) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    java.util.Map<String, String> entry = new java.util.LinkedHashMap<>();
                    entry.put("sender", String.valueOf(map.get("sender")));
                    entry.put("message", String.valueOf(map.get("message")));
                    entry.put("sentAt", String.valueOf(map.get("sentAt")));
                    letters.add(entry);
                }
            }
        }

        java.util.Map<String, String> newLetter = new java.util.LinkedHashMap<>();
        newLetter.put("sender", senderName);
        newLetter.put("message", message);
        newLetter.put("sentAt", sentAt);
        letters.add(newLetter);

        config.set(path, letters);
        save();
    }

    private void deliverLetter(Player player, String sender, String message, String sentAt) {
        player.sendMessage("§6✉ §e새 편지가 도착했습니다!");
        player.sendMessage("§7보낸 이: §f" + sender);
        player.sendMessage("§7내용: §f" + message);
        player.sendMessage("§7보낸 시간: §8" + sentAt);
    }

    private void save() {
        try { config.save(file); } catch (IOException e) {
            plugin.getLogger().warning("letters.yml 저장 실패: " + e.getMessage());
        }
    }

    public record LetterData(String sender, String message, String sentAt) {}
}
