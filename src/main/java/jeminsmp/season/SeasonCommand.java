package jeminsmp.season;

import jeminsmp.JeminSMPPlugin;
import jeminsmp.kills.KillsManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class SeasonCommand implements CommandExecutor, TabCompleter {

    private final JeminSMPPlugin plugin;

    public SeasonCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        SeasonManager sm = plugin.getSeasonManager();
        if (args.length == 0) { showInfo(sender, sm); return true; }

        switch (args[0].toLowerCase()) {
            case "info"    -> showInfo(sender, sm);
            case "top"     -> showTop(sender, "시즌 킬 TOP 5");
            case "history" -> sender.sendMessage(sm.getHistoryString());

            case "start"   -> {
                if (!sender.hasPermission("jeminsmp.admin")) { sender.sendMessage("§c권한 없음"); return true; }
                String name = args.length > 1
                        ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        : null;
                doStart(sm, name);
                sender.sendMessage("§a시즌 " + sm.getCurrentSeason() + " (" + sm.getCurrentName() + ") 시작 완료");
            }

            case "end"     -> {
                if (!sender.hasPermission("jeminsmp.admin")) { sender.sendMessage("§c권한 없음"); return true; }
                if (!sm.isActive()) { sender.sendMessage("§c진행 중인 시즌이 없습니다."); return true; }
                doEnd(sm);
            }

            case "reward"  -> handleReward(sender, args);

            default -> sender.sendMessage("§c사용법: /season [info|start <이름>|end|top|history|reward]");
        }
        return true;
    }

    // ── 시즌 시작 공통 로직 (Discord/인게임 공유) ──
    public void doStart(SeasonManager sm, String name) {
        sm.startSeason(name);
        String sName = sm.getCurrentName();

        // 보상 지급
        SeasonRewardManager rm = plugin.getSeasonRewardManager();
        if (rm != null) rm.distributeToAll();

        Bukkit.broadcast(Component.text("§6§l🏆 " + sName + " 시작! §f기록에 도전하세요!"));
        if (plugin.getDiscordBot() != null) {
            plugin.getDiscordBot().sendEvent(
                    "🏆 **" + sName + " 시작!**\n새로운 시즌이 시작되었습니다. 기록에 도전하세요!");
        }
    }

    // ── 시즌 종료 공통 로직 ──
    public void doEnd(SeasonManager sm) {
        String endName   = sm.getCurrentName();
        String topResult = buildTopResult();
        sm.endSeason();

        Bukkit.broadcast(Component.text("§c§l🏁 " + endName + " 종료!"));
        if (!topResult.isEmpty()) {
            Bukkit.broadcast(Component.text("§e§l── 최종 순위 ──"));
            for (String line : topResult.split("\n"))
                Bukkit.broadcast(Component.text(line));
        }
        if (plugin.getDiscordBot() != null) {
            plugin.getDiscordBot().sendEvent(
                    "🏁 **" + endName + " 종료!**\n" + topResult.replaceAll("§[0-9a-fk-or]", ""));
        }
    }

    // ── /season reward ──
    private void handleReward(CommandSender sender, String[] args) {
        if (!sender.hasPermission("jeminsmp.admin")) { sender.sendMessage("§c권한 없음"); return; }
        SeasonRewardManager rm = plugin.getSeasonRewardManager();
        if (rm == null) { sender.sendMessage("§c보상 매니저 오류"); return; }

        String sub = args.length > 1 ? args[1].toLowerCase() : "list";
        switch (sub) {
            case "add" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c인게임에서만 사용 가능합니다."); return;
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType().isAir()) {
                    player.sendMessage("§c손에 아이템을 들고 사용하세요."); return;
                }
                rm.addReward(held.clone());
                player.sendMessage("§a보상 아이템 추가: §f" + formatItem(held));
            }
            case "clear" -> {
                rm.clearRewards();
                sender.sendMessage("§a보상 아이템 목록을 초기화했습니다.");
            }
            case "list" -> {
                List<ItemStack> rewards = rm.getRewards();
                if (rewards.isEmpty()) { sender.sendMessage("§7설정된 보상 아이템 없음"); return; }
                sender.sendMessage("§e§l── 시즌 시작 보상 ──");
                for (int i = 0; i < rewards.size(); i++) {
                    sender.sendMessage("§7" + (i + 1) + ". §f" + formatItem(rewards.get(i)));
                }
            }
            default -> sender.sendMessage("§c사용법: /season reward [add|list|clear]");
        }
    }

    private String formatItem(ItemStack item) {
        String display = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : item.getType().name().toLowerCase().replace('_', ' ');
        return display + " x" + item.getAmount();
    }

    private void showInfo(CommandSender sender, SeasonManager sm) {
        if (!sm.isActive()) {
            sender.sendMessage("§7현재 진행 중인 시즌이 없습니다.");
            int last = sm.getLastSeason();
            if (last > 0) sender.sendMessage("§7마지막 시즌: §f시즌 " + last + " (종료됨)");
            return;
        }
        long elapsed = System.currentTimeMillis() - sm.getStartedAt();
        long days    = elapsed / 86400000L;
        String started = new SimpleDateFormat("yyyy-MM-dd").format(new Date(sm.getStartedAt()));

        sender.sendMessage("§6§l■ " + sm.getCurrentName());
        sender.sendMessage("§7시작: §f" + started + " §8(" + days + "일 경과)");
        showTop(sender, "이번 시즌 킬 TOP 5");
    }

    private void showTop(CommandSender sender, String header) {
        KillsManager km = plugin.getKillsManager();
        if (km == null) { sender.sendMessage("§c킬 데이터 없음"); return; }
        List<KillsManager.PlayerStats> top = km.getTopByKills(5);
        sender.sendMessage("§e§l── " + header + " ──");
        if (top.isEmpty()) { sender.sendMessage("§7기록 없음"); return; }
        String[] medals = {"§6①", "§7②", "§8③", "§f④", "§f⑤"};
        for (int i = 0; i < top.size(); i++) {
            KillsManager.PlayerStats s = top.get(i);
            sender.sendMessage(String.format("%s §f%s §8│ §c%d킬 §7/ §f%d데스 §8│ KDA §e%.2f",
                    medals[i], s.name, s.kills, s.deaths, s.kda()));
        }
    }

    public String buildTopResult() {
        KillsManager km = plugin.getKillsManager();
        if (km == null) return "";
        List<KillsManager.PlayerStats> top = km.getTopByKills(3);
        if (top.isEmpty()) return "";
        String[] medals = {"🥇", "🥈", "🥉"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            KillsManager.PlayerStats s = top.get(i);
            sb.append(medals[i]).append(" **").append(s.name).append("**  ")
              .append(s.kills).append("킬 / ").append(s.deaths).append("데스\n");
        }
        return sb.toString().stripTrailing();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1)
            return List.of("info", "start", "end", "top", "history", "reward");
        if (args.length == 2 && args[0].equalsIgnoreCase("reward"))
            return List.of("add", "list", "clear");
        return List.of();
    }
}
