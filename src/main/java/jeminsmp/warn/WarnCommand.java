package jeminsmp.warn;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.UUID;

public class WarnCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;

    public WarnCommand(JeminSMPPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("❌ OP 전용 명령어입니다.", NamedTextColor.RED));
            return true;
        }

        WarnManager wm = plugin.getWarnManager();

        // /warn <플레이어> [사유]
        if (label.equalsIgnoreCase("warn")) {
            if (args.length < 1) {
                sender.sendMessage(Component.text(
                        "사용법: /warn <플레이어> [사유]", NamedTextColor.YELLOW));
                return true;
            }
            UUID target = wm.findUUID(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("❌ 플레이어를 찾을 수 없습니다.", NamedTextColor.RED));
                return true;
            }
            String reason = args.length > 1
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                    : "사유 없음";
            int count = wm.addWarning(target, reason, sender.getName());
            int threshold = plugin.getConfig().getInt("warn.auto-punish-count", 3);

            var tp = Bukkit.getPlayer(target);
            String name = tp != null ? tp.getName() : args[0];

            sender.sendMessage(Component.text(
                    "⚠ " + name + " 에게 경고를 부여했습니다. (" + count + "/" + threshold + ")",
                    NamedTextColor.YELLOW));

            if (tp != null) {
                tp.sendMessage(Component.text(
                        "⚠ 경고를 받았습니다: " + reason + " (" + count + "/" + threshold + ")",
                        NamedTextColor.RED));
            }

            if (count >= threshold) {
                sender.sendMessage(Component.text(
                        "🔴 경고 " + threshold + "회 도달 — 대기열 징계가 적용되었습니다.",
                        NamedTextColor.RED));
            }
            return true;
        }

        // /unwarn <플레이어> [번호]
        if (label.equalsIgnoreCase("unwarn")) {
            if (args.length < 1) {
                sender.sendMessage(Component.text(
                        "사용법: /unwarn <플레이어> [번호]", NamedTextColor.YELLOW));
                return true;
            }
            UUID target = wm.findUUID(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("❌ 플레이어를 찾을 수 없습니다.", NamedTextColor.RED));
                return true;
            }
            if (args.length >= 2) {
                try {
                    int idx = Integer.parseInt(args[1]);
                    if (wm.removeWarning(target, idx)) {
                        sender.sendMessage(Component.text(
                                "✅ " + args[0] + " 의 " + idx + "번 경고를 삭제했습니다.",
                                NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("❌ 잘못된 번호입니다.", NamedTextColor.RED));
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("❌ 숫자를 입력하세요.", NamedTextColor.RED));
                }
            } else {
                wm.clearWarnings(target);
                sender.sendMessage(Component.text(
                        "✅ " + args[0] + " 의 경고를 전부 삭제했습니다.", NamedTextColor.GREEN));
            }
            return true;
        }

        // /warnings [플레이어]
        if (label.equalsIgnoreCase("warnings")) {
            String targetName = args.length > 0 ? args[0] : sender.getName();
            UUID target = wm.findUUID(targetName);
            if (target == null) {
                sender.sendMessage(Component.text("❌ 플레이어를 찾을 수 없습니다.", NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(Component.text(wm.formatWarnings(target, targetName), NamedTextColor.YELLOW));
            return true;
        }

        // /punish <플레이어>
        if (label.equalsIgnoreCase("punish")) {
            if (args.length < 1) {
                sender.sendMessage(Component.text("사용법: /punish <플레이어>", NamedTextColor.YELLOW));
                return true;
            }
            UUID target = wm.findUUID(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("❌ 플레이어를 찾을 수 없습니다.", NamedTextColor.RED));
                return true;
            }
            wm.setPunished(target, true, false);
            sender.sendMessage(Component.text(
                    "🔴 " + args[0] + " 에게 대기열 징계를 적용했습니다.", NamedTextColor.RED));
            return true;
        }

        // /unpunish <플레이어>
        if (label.equalsIgnoreCase("unpunish")) {
            if (args.length < 1) {
                sender.sendMessage(Component.text("사용법: /unpunish <플레이어>", NamedTextColor.YELLOW));
                return true;
            }
            UUID target = wm.findUUID(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("❌ 플레이어를 찾을 수 없습니다.", NamedTextColor.RED));
                return true;
            }
            wm.setPunished(target, false, true);
            sender.sendMessage(Component.text(
                    "✅ " + args[0] + " 의 징계를 해제했습니다.", NamedTextColor.GREEN));
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(p -> p.getName())
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
