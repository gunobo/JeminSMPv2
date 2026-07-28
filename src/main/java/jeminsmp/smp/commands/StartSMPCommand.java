package jeminsmp.smp.commands;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.util.Iterator;
import java.util.Random;

public class StartSMPCommand implements CommandExecutor {

    private final JeminSMPPlugin plugin;

    public StartSMPCommand(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("jeminsmp.start")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        // 플레이어 데이터 초기화 여부: 기본값 true, "keep" 인자 시 false
        boolean resetData = true;
        if (args.length > 0 && args[0].equalsIgnoreCase("keep")) {
            resetData = false;
        }

        plugin.setSmpActive(true);
        plugin.setSmpResetData(resetData);

        String worldName = plugin.getConfig().getString("smp.world-name", "smp_world");
        int radius = plugin.getConfig().getInt("smp.scatter-radius", 500);

        sender.sendMessage("§eSMP를 시작합니다... (데이터 초기화: " + (resetData ? "§a예" : "§c아니오") + "§e)");

        // 데이터 초기화
        if (resetData) {
            plugin.getKillsManager().resetAll();
            plugin.getStarterPackCommand().resetAll();
        }

        // 월드 언로드 및 삭제
        World fallback = Bukkit.getWorlds().get(0);
        String[] relatedWorlds = {worldName, worldName + "_nether", worldName + "_the_end"};
        for (String name : relatedWorlds) {
            World w = Bukkit.getWorld(name);
            if (w != null) {
                for (Player p : w.getPlayers()) p.teleport(fallback.getSpawnLocation());
                Bukkit.unloadWorld(w, false);
            }
            File folder = new File(Bukkit.getWorldContainer(), name);
            if (folder.exists()) deleteFolder(folder);
        }

        // 새 월드 생성 (랜덤 시드)
        WorldCreator wc = new WorldCreator(worldName);
        wc.environment(World.Environment.NORMAL);
        wc.seed(new Random().nextLong());
        World smpWorld = wc.createWorld();
        if (smpWorld == null) {
            sender.sendMessage("§c월드 생성 실패!");
            return true;
        }

        // 현재 접속 중인 플레이어 산개
        Random random = new Random();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (resetData) resetPlayer(player);
            int x = random.nextInt(radius * 2 + 1) - radius;
            int z = random.nextInt(radius * 2 + 1) - radius;
            int y = smpWorld.getHighestBlockYAt(x, z) + 1;
            player.teleport(new Location(smpWorld, x + 0.5, y, z + 0.5));
            player.sendMessage("§aSMP가 시작되었습니다! 즐거운 플레이 되세요!");
        }

        sender.sendMessage("§aSMP 시작 완료! " + Bukkit.getOnlinePlayers().size() + "명 산개.");

        if (plugin.getDiscordBot() != null) {
            plugin.getDiscordBot().sendEvent("🎮 **SMP가 시작되었습니다!**");
        }

        return true;
    }

    public static void resetPlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setExp(0f);
        player.setLevel(0);
        player.setTotalExperience(0);

        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) player.setHealth(maxHealth.getValue());

        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setExhaustion(0f);
        player.setFireTicks(0);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement adv = it.next();
            AdvancementProgress progress = player.getAdvancementProgress(adv);
            for (String criteria : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criteria);
            }
        }

        player.setStatistic(Statistic.DEATHS, 0);
        player.setGameMode(GameMode.SURVIVAL);
    }

    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteFolder(f);
                else f.delete();
            }
        }
        folder.delete();
    }
}
