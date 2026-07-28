package jeminsmp.tpa.commands;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpaCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;

    public TpaCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player requester)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        if (args.length < 1) { requester.sendMessage("§c사용법: /tpa <플레이어>"); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { requester.sendMessage("§c" + args[0] + " 님이 온라인 상태가 아닙니다."); return true; }
        plugin.getTpaManager().sendRequest(requester, target);
        return true;
    }
}
