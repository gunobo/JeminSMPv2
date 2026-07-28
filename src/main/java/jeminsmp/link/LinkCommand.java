package jeminsmp.link;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LinkCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;

    public LinkCommand(JeminSMPPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }

        LinkManager lm = plugin.getLinkManager();

        // /unlink
        if (label.equalsIgnoreCase("unlink")) {
            if (lm.unlink(player.getUniqueId())) {
                player.sendMessage(Component.text(
                        "✅ 디스코드 연동이 해제되었습니다.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text(
                        "❌ 연동된 디스코드 계정이 없습니다.", NamedTextColor.RED));
            }
            return true;
        }

        // /link <코드>
        if (args.length < 1) {
            if (lm.isLinked(player.getUniqueId())) {
                player.sendMessage(Component.text(
                        "✅ 이미 디스코드 계정과 연동되어 있습니다.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text(
                        "디스코드에서 !link " + player.getName() + " 을 입력하고",
                        NamedTextColor.YELLOW));
                player.sendMessage(Component.text(
                        "받은 코드를 /link <코드> 로 입력하세요.", NamedTextColor.YELLOW));
            }
            return true;
        }

        String code = args[0].trim();
        switch (lm.confirmLink(player, code)) {
            case SUCCESS -> player.sendMessage(Component.text(
                    "✅ 디스코드 연동 완료! 역할이 자동으로 부여됩니다.", NamedTextColor.GREEN));
            case INVALID_CODE -> player.sendMessage(Component.text(
                    "❌ 코드가 올바르지 않습니다. 디스코드에서 !link 를 다시 입력하세요.",
                    NamedTextColor.RED));
            case EXPIRED -> player.sendMessage(Component.text(
                    "❌ 코드가 만료되었습니다. (5분 이내 입력 필요) 다시 시도하세요.",
                    NamedTextColor.RED));
            case ALREADY_LINKED -> player.sendMessage(Component.text(
                    "❌ 이미 연동된 계정입니다. /unlink 로 해제 후 다시 시도하세요.",
                    NamedTextColor.RED));
        }
        return true;
    }
}
