package jeminsmp.mathquiz;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class MathQuizCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;

    public MathQuizCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MathQuizManager mgr = plugin.getMathQuizManager();
        if (mgr == null) { sender.sendMessage("§c퀴즈 매니저 오류"); return true; }

        if (!sender.hasPermission("jeminsmp.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "start";
        switch (sub) {
            case "start", "force" -> {
                if (mgr.isActive()) { sender.sendMessage("§c이미 퀴즈가 진행 중입니다."); return true; }
                mgr.forcePostQuiz();
                sender.sendMessage("§a수학 퀴즈를 출제했습니다.");
            }
            case "stop", "skip" -> {
                if (!mgr.isActive()) { sender.sendMessage("§c진행 중인 퀴즈가 없습니다."); return true; }
                // 현재 문제 강제 종료
                org.bukkit.Bukkit.broadcast(
                    net.kyori.adventure.text.Component.text("§7[퀴즈] 관리자에 의해 퀴즈가 취소되었습니다."));
                // 내부 상태 초기화 (checkAnswer 호출 대신 직접 종료)
                mgr.forceStop();
                sender.sendMessage("§a퀴즈를 취소했습니다.");
            }
            case "status" -> {
                if (mgr.isActive()) {
                    sender.sendMessage("§e진행 중: §f" + mgr.getCurrentQuiz().question());
                } else {
                    sender.sendMessage("§7현재 진행 중인 퀴즈 없음");
                }
                sender.sendMessage("§7주기: §f" + mgr.getIntervalMinutes() + "분 / 타임아웃: §f" + mgr.getTimeoutSeconds() + "초");
            }
            case "reload" -> {
                mgr.reload();
                sender.sendMessage("§a퀴즈 설정을 다시 불러왔습니다.");
            }
            default -> sender.sendMessage("§c사용법: /mathquiz [start|stop|status|reload]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("start", "stop", "status", "reload");
        return List.of();
    }
}
