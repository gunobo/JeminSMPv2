package jeminsmp.sleep;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class SleepCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;

    public SleepCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }

        SleepManager sm = plugin.getSleepManager();

        if (!sm.isVoteActive()) {
            player.sendMessage("§c현재 진행 중인 투표가 없습니다.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§c사용법: /sleepvote yes|no");
            return true;
        }

        boolean yes = args[0].equalsIgnoreCase("yes") || args[0].equalsIgnoreCase("찬성");
        boolean no  = args[0].equalsIgnoreCase("no")  || args[0].equalsIgnoreCase("반대");

        if (!yes && !no) {
            player.sendMessage("§c사용법: /sleepvote yes|no");
            return true;
        }

        switch (sm.vote(player, yes)) {
            case OK        -> {} // 메시지는 SleepManager에서 방송
            case DUPLICATE -> player.sendMessage("§c이미 투표하셨습니다.");
            case NO_VOTE   -> player.sendMessage("§c진행 중인 투표가 없습니다.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("yes", "no");
        return List.of();
    }
}
