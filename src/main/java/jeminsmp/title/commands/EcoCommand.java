package jeminsmp.title.commands;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.Locale;

public class EcoCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;

    public EcoCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) { sender.sendMessage("§c관리자만 사용할 수 있는 명령어입니다."); return true; }
        if (args.length < 3) { sender.sendMessage("§c사용법: /eco <give|take|set> <플레이어> <금액>"); return true; }
        Player target = sender.getServer().getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage("§c" + args[1] + " 님이 온라인 상태가 아닙니다."); return true; }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) { sender.sendMessage("§c금액은 0 이상의 정수여야 합니다."); return true; }
        String fmt = NumberFormat.getNumberInstance(Locale.KOREA).format(amount);
        switch (args[0].toLowerCase()) {
            case "give" -> {
                plugin.getEconomy().deposit(target.getUniqueId(), amount);
                sender.sendMessage("§a" + target.getName() + " 님에게 " + fmt + " 코인 지급 완료.");
                target.sendMessage("§a관리자가 §e" + fmt + " 코인§a을 지급했습니다!");
            }
            case "take" -> {
                if (!plugin.getEconomy().withdraw(target.getUniqueId(), amount)) {
                    sender.sendMessage("§c잔액 부족");
                    return true;
                }
                sender.sendMessage("§a" + target.getName() + " 님에게서 " + fmt + " 코인 차감 완료.");
                target.sendMessage("§c관리자가 §e" + fmt + " 코인§c을 차감했습니다.");
            }
            case "set" -> {
                plugin.getEconomy().set(target.getUniqueId(), amount);
                sender.sendMessage("§a" + target.getName() + " 님의 잔액을 " + fmt + " 코인으로 설정 완료.");
                target.sendMessage("§6잔액이 §e" + fmt + " 코인§6으로 설정되었습니다.");
            }
            default -> sender.sendMessage("§c사용법: /eco <give|take|set> <플레이어> <금액>");
        }
        return true;
    }
}
