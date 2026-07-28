package jeminsmp.tab;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TabManager {

    private final JeminSMPPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public TabManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public void update(Player player) {
        int ping = player.getPing();
        NamedTextColor pingColor = ping < 50 ? NamedTextColor.GREEN
                : ping < 100 ? NamedTextColor.YELLOW
                : ping < 200 ? NamedTextColor.RED
                : NamedTextColor.DARK_RED;

        boolean afk = plugin.getAfkManager() != null && plugin.getAfkManager().isAfk(player.getUniqueId());

        // 칭호 prefix
        String equippedDisplay = plugin.getTitleManager().getEquippedDisplay(player.getUniqueId());
        Component titlePrefix = equippedDisplay != null
                ? LEGACY.deserialize(equippedDisplay + " ")
                : Component.empty();

        // 현상금 표시
        int bounty = plugin.getBountyManager().getTotalBounty(player.getUniqueId());
        Component bountyTag = bounty > 0
                ? Component.text(" 💰" + bounty, NamedTextColor.GOLD)
                : Component.empty();

        Component tabName = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text(ping + "ms", pingColor))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(afk ? Component.text("[AFK] ", NamedTextColor.GRAY) : Component.empty())
                .append(titlePrefix)
                .append(player.displayName())
                .append(bountyTag);

        player.playerListName(tabName);
    }

    public void restore() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playerListName(player.displayName());
        }
    }
}
