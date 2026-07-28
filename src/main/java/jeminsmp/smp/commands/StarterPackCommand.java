package jeminsmp.smp.commands;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class StarterPackCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;
    private final File dataFile;
    private final Set<UUID> claimed = new HashSet<>();

    public StarterPackCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "starterpack.yml");
        load();
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        List<String> list = cfg.getStringList("claimed");
        for (String s : list) {
            try { claimed.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("claimed", claimed.stream().map(UUID::toString).toList());
        try { cfg.save(dataFile); } catch (IOException e) {
            plugin.getLogger().warning("starterpack.yml 저장 실패: " + e.getMessage());
        }
    }

    public void resetAll() {
        claimed.clear();
        save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }

        if (claimed.contains(player.getUniqueId())) {
            player.sendMessage("§c이미 스타터팩을 받았습니다.");
            return true;
        }

        player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 64));
        claimed.add(player.getUniqueId());
        save();
        player.sendMessage("§a스타터팩을 받았습니다! 구운소고기 §e64개§a가 지급되었습니다.");
        return true;
    }
}
