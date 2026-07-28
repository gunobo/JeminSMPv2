package jeminsmp.title.commands;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.Locale;

public class BalanceCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;

    public BalanceCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        long balance = plugin.getEconomy().getBalance(player.getUniqueId());
        player.sendMessage("§6▶ 내 잔액: §a"
                + NumberFormat.getNumberInstance(Locale.KOREA).format(balance) + " 코인");
        return true;
    }
}
