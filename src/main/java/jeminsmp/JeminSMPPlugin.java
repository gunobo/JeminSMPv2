package jeminsmp;

import jeminsmp.uuid.OnlineUuidResolver;
import jeminsmp.uuid.UuidBridgeListener;
import jeminsmp.uuid.UuidCacheDb;
import jeminsmp.uuid.UuidMigrationManager;
import jeminsmp.uuid.UuidMigrateListener;
import jeminsmp.combat.CombatManager;
import jeminsmp.discord.ConsoleLogHandler;
import jeminsmp.discord.DiscordBot;
import jeminsmp.discord.MinecraftListener;
import jeminsmp.home.HomeManager;
import jeminsmp.home.commands.*;
import jeminsmp.kills.KillsListener;
import jeminsmp.kills.KillsManager;
import jeminsmp.kills.commands.KillsCommand;
import jeminsmp.nick.NickListener;
import jeminsmp.nick.NickManager;
import jeminsmp.nick.commands.NickCommand;
import jeminsmp.shop.MailboxManager;
import jeminsmp.shop.ShopGui;
import jeminsmp.shop.ShopListener;
import jeminsmp.shop.ShopManager;
import jeminsmp.shop.commands.MailboxCommand;
import jeminsmp.shop.commands.ShopCommand;
import jeminsmp.afk.AfkListener;
import jeminsmp.afk.AfkManager;
import jeminsmp.announcement.AnnouncementManager;
import jeminsmp.autotitle.AutoTitleManager;
import jeminsmp.waypoint.WaypointCommand;
import jeminsmp.waypoint.WaypointManager;

import jeminsmp.compensation.ClaimCommand;
import jeminsmp.compensation.CompensationManager;
import jeminsmp.schedule.ScheduleListener;
import jeminsmp.schedule.ScheduleManager;
import jeminsmp.schedule.SheetsManager;
import jeminsmp.warn.WarnCommand;
import jeminsmp.warn.WarnManager;
import jeminsmp.link.LinkCommand;
import jeminsmp.link.LinkManager;
import jeminsmp.sleep.SleepCommand;
import jeminsmp.sleep.SleepListener;
import jeminsmp.sleep.SleepManager;
import jeminsmp.death.DeathListener;
import jeminsmp.death.LastWillCommand;
import jeminsmp.death.LastWillManager;
import jeminsmp.letter.LetterCommand;
import jeminsmp.letter.LetterListener;
import jeminsmp.letter.LetterManager;
import jeminsmp.team.mission.TeamMissionListener;
import jeminsmp.team.mission.TeamMissionManager;
import jeminsmp.leaderboard.LeaderboardCommand;
import jeminsmp.season.SeasonCommand;
import jeminsmp.season.SeasonJoinListener;
import jeminsmp.season.SeasonManager;
import jeminsmp.season.SeasonRewardManager;
import jeminsmp.mathquiz.MathQuizCommand;
import jeminsmp.mathquiz.MathQuizListener;
import jeminsmp.mathquiz.MathQuizManager;
import jeminsmp.trade.TradeCommand;
import jeminsmp.trade.TradeListener;
import jeminsmp.trade.TradeManager;
import jeminsmp.smp.SMPListener;
import jeminsmp.smp.commands.StarterPackCommand;
import jeminsmp.tab.TabManager;
import jeminsmp.smp.commands.StartSMPCommand;
import jeminsmp.team.TeamLevelManager;
import jeminsmp.team.TeamListener;
import jeminsmp.team.TeamManager;
import jeminsmp.team.commands.EnderChestCommand;
import jeminsmp.team.commands.TeamChatCommand;
import jeminsmp.team.commands.TeamCommand;
import jeminsmp.title.EconomyHelper;
import jeminsmp.title.TitleListener;
import jeminsmp.title.TitleManager;
import jeminsmp.title.commands.BalanceCommand;
import jeminsmp.title.commands.EcoCommand;
import jeminsmp.title.commands.TitleCommand;
import jeminsmp.tpa.TpaManager;
import jeminsmp.tpa.commands.TpaAcceptCommand;
import jeminsmp.tpa.commands.TpaCommand;
import jeminsmp.tpa.commands.TpaDenyCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class JeminSMPPlugin extends JavaPlugin {

    private CombatManager combatManager;
    private HomeManager homeManager;
    private KillsManager killsManager;
    private NickManager nickManager;
    private ShopManager shopManager;
    private MailboxManager mailboxManager;
    private ShopGui shopGui;
    private TpaManager tpaManager;
    private TeamManager teamManager;
    private TeamLevelManager teamLevelManager;
    private TitleManager titleManager;
    private EconomyHelper economyHelper;
    private StarterPackCommand starterPackCommand;
    private TabManager tabManager;
    private AnnouncementManager announcementManager;
    private AfkManager afkManager;
    private WaypointManager waypointManager;
    private AutoTitleManager autoTitleManager;
    private SleepManager sleepManager;
    private ScheduleManager scheduleManager;
    private SheetsManager sheetsManager;
    private WarnManager warnManager;
    private LinkManager linkManager;
    private CompensationManager compensationManager;
    private TeamMissionManager teamMissionManager;
    private LetterManager letterManager;
    private LastWillManager lastWillManager;
    private SeasonManager seasonManager;
    private SeasonRewardManager seasonRewardManager;
    private SeasonCommand seasonCommand;
    private MathQuizManager mathQuizManager;
    private TradeManager tradeManager;
    private DiscordBot discordBot;
    private UuidMigrationManager uuidMigrationManager;
    private UuidCacheDb uuidCacheDb;
    private OnlineUuidResolver uuidResolver;
    private boolean smpActive = false;
    private boolean smpResetData = true;
    private File smpStateFile;
    private ConsoleLogHandler consoleLogHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        getDataFolder().mkdirs();
        smpStateFile = new File(getDataFolder(), "smp_state.yml");
        loadSmpState();

        // Managers
        combatManager = new CombatManager(this);
        homeManager = new HomeManager(this);
        killsManager = new KillsManager(this);
        nickManager = new NickManager(this);
        shopManager = new ShopManager(this);
        mailboxManager = new MailboxManager(this);
        shopGui = new ShopGui(this);
        tpaManager = new TpaManager(this);
        teamManager = new TeamManager(this);
        teamLevelManager = new TeamLevelManager(this);
        economyHelper = new EconomyHelper(this);
        titleManager = new TitleManager(this);
        tabManager = new TabManager(this);
        afkManager = new AfkManager(this);
        waypointManager = new WaypointManager(this);
        autoTitleManager = new AutoTitleManager(this);
        announcementManager = new AnnouncementManager(this);
        sleepManager = new SleepManager(this);
        scheduleManager = new ScheduleManager(this);
        sheetsManager = new SheetsManager(this);
        warnManager = new WarnManager(this);
        linkManager = new LinkManager(this);
        compensationManager = new CompensationManager(this);
        teamMissionManager = new TeamMissionManager(this);
        letterManager = new LetterManager(this);
        lastWillManager = new LastWillManager(this);
        seasonManager = new SeasonManager(this);
        seasonRewardManager = new SeasonRewardManager(this);
        mathQuizManager = new MathQuizManager(this);
        tradeManager = new TradeManager(this);
        uuidMigrationManager = new UuidMigrationManager(this);
        uuidCacheDb  = new UuidCacheDb(this);
        uuidResolver = new OnlineUuidResolver(this, uuidCacheDb);

        // Listeners
        getServer().getPluginManager().registerEvents(combatManager, this);
        getServer().getPluginManager().registerEvents(new SMPListener(this), this);
        getServer().getPluginManager().registerEvents(new KillsListener(this), this);
        getServer().getPluginManager().registerEvents(new NickListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this, shopGui), this);
        getServer().getPluginManager().registerEvents(new TeamListener(this), this);
        getServer().getPluginManager().registerEvents(new TitleListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new AfkListener(this), this);
        getServer().getPluginManager().registerEvents(new SleepListener(this), this);
        getServer().getPluginManager().registerEvents(new ScheduleListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamMissionListener(this), this);
        getServer().getPluginManager().registerEvents(new LetterListener(this), this);
        getServer().getPluginManager().registerEvents(new UuidMigrateListener(this), this);
        getServer().getPluginManager().registerEvents(new UuidBridgeListener(this, uuidResolver), this);

        // Home commands
        getCommand("sethome").setExecutor(new SetHomeCommand(homeManager));
        getCommand("home").setExecutor(new HomeCommand(homeManager, combatManager, this));
        getCommand("delhome").setExecutor(new DelHomeCommand(homeManager));
        getCommand("homes").setExecutor(new HomesCommand(homeManager));

        // Status
        getCommand("mystatus").setExecutor(new PlaytimeCommand(this));

        // TPA
        getCommand("tpa").setExecutor(new TpaCommand(this));
        getCommand("tpaccept").setExecutor(new TpaAcceptCommand(this));
        getCommand("tpdeny").setExecutor(new TpaDenyCommand(this));

        // Team
        var teamCmd = new TeamCommand(this);
        getCommand("team").setExecutor(teamCmd);
        getCommand("team").setTabCompleter(teamCmd);
        getCommand("tc").setExecutor(new TeamChatCommand(this));
        getCommand("ec").setExecutor(new EnderChestCommand(this));

        // Nick
        getCommand("nick").setExecutor(new NickCommand(this));

        // Kills
        getCommand("kills").setExecutor(new KillsCommand(this));

        // Shop & Mailbox
        getCommand("shop").setExecutor(new ShopCommand(this));
        getCommand("우편함").setExecutor(new MailboxCommand(this));

        // Title & Economy
        var titleCmd = new TitleCommand(this);
        getCommand("title").setExecutor(titleCmd);
        getCommand("title").setTabCompleter(titleCmd);
        getCommand("balance").setExecutor(new BalanceCommand(this));
        getCommand("eco").setExecutor(new EcoCommand(this));

        // Sleep vote
        var sleepCmd = new SleepCommand(this);
        getCommand("sleepvote").setExecutor(sleepCmd);
        getCommand("sleepvote").setTabCompleter(sleepCmd);

        // Waypoint
        var waypointCmd = new WaypointCommand(this);
        getCommand("wp").setExecutor(waypointCmd);
        getCommand("wp").setTabCompleter(waypointCmd);

        // Trade
        getServer().getPluginManager().registerEvents(new TradeListener(this), this);
        var tradeCmd = new TradeCommand(this);
        getCommand("거래").setExecutor(tradeCmd);
        getCommand("거래").setTabCompleter(tradeCmd);

        // MathQuiz
        getServer().getPluginManager().registerEvents(new MathQuizListener(this), this);
        var mathQuizCmd = new MathQuizCommand(this);
        getCommand("mathquiz").setExecutor(mathQuizCmd);
        getCommand("mathquiz").setTabCompleter(mathQuizCmd);

        // Season
        seasonCommand = new SeasonCommand(this);
        getCommand("season").setExecutor(seasonCommand);
        getCommand("season").setTabCompleter(seasonCommand);
        getServer().getPluginManager().registerEvents(new SeasonJoinListener(this), this);

        // Leaderboard
        var leaderboardCmd = new LeaderboardCommand(this);
        getCommand("top").setExecutor(leaderboardCmd);
        getCommand("top").setTabCompleter(leaderboardCmd);

        // SMP
        starterPackCommand = new StarterPackCommand(this);
        getCommand("스타터팩").setExecutor(starterPackCommand);
        getCommand("startSMP").setExecutor(new StartSMPCommand(this));

        // Compensation
        var claimCmd = new ClaimCommand(this);
        getCommand("claim").setExecutor(claimCmd);
        getCommand("claim").setTabCompleter(claimCmd);

        // Link
        var linkCmd = new LinkCommand(this);
        getCommand("link").setExecutor(linkCmd);
        getCommand("unlink").setExecutor(linkCmd);

        // Warn
        var warnCmd = new WarnCommand(this);
        getCommand("warn").setExecutor(warnCmd);
        getCommand("warn").setTabCompleter(warnCmd);
        getCommand("unwarn").setExecutor(warnCmd);
        getCommand("unwarn").setTabCompleter(warnCmd);
        getCommand("warnings").setExecutor(warnCmd);
        getCommand("warnings").setTabCompleter(warnCmd);
        getCommand("punish").setExecutor(warnCmd);
        getCommand("punish").setTabCompleter(warnCmd);
        getCommand("unpunish").setExecutor(warnCmd);
        getCommand("unpunish").setTabCompleter(warnCmd);

        // 유언 & 편지
        getCommand("유언").setExecutor(new LastWillCommand(this));
        var letterCmd = new LetterCommand(this);
        getCommand("편지").setExecutor(letterCmd);
        getCommand("편지함").setExecutor(letterCmd);

        // Discord
        if (getConfig().getBoolean("discord.enabled", false)) {
            discordBot = new DiscordBot(this);
            discordBot.start();
            getServer().getPluginManager().registerEvents(new MinecraftListener(this, discordBot), this);

            String levelStr = getConfig().getString("discord.console-log-level", "INFO");
            Level logLevel;
            try { logLevel = Level.parse(levelStr); } catch (Exception e) { logLevel = Level.INFO; }
            consoleLogHandler = new ConsoleLogHandler(discordBot, logLevel);
            getLogger().getParent().addHandler(consoleLogHandler);

            int flushInterval = getConfig().getInt("discord.console-flush-interval-ticks", 100);
            getServer().getScheduler().runTaskTimer(this, () -> {
                if (consoleLogHandler != null) consoleLogHandler.flushToDiscord();
            }, flushInterval, flushInterval);
        }

        getLogger().info("jeminSMPv1 활성화 완료!");
    }

    @Override
    public void onDisable() {
        if (tabManager != null) tabManager.restore();
        if (discordBot != null) {
            discordBot.sendShutdownMessage();
            discordBot.shutdown();
        }
        if (consoleLogHandler != null) {
            getLogger().getParent().removeHandler(consoleLogHandler);
            consoleLogHandler.close();
        }
        if (uuidCacheDb != null) uuidCacheDb.close();
        if (mathQuizManager != null) mathQuizManager.shutdown();
        getLogger().info("jeminSMPv1 비활성화 완료.");
    }

    public CombatManager getCombatManager() { return combatManager; }
    public HomeManager getHomeManager() { return homeManager; }
    public KillsManager getKillsManager() { return killsManager; }
    public SeasonManager getSeasonManager() { return seasonManager; }
    public SeasonRewardManager getSeasonRewardManager() { return seasonRewardManager; }
    public SeasonCommand getSeasonCommand() { return seasonCommand; }
    public MathQuizManager getMathQuizManager() { return mathQuizManager; }
    public TradeManager getTradeManager() { return tradeManager; }
    public NickManager getNickManager() { return nickManager; }
    public ShopManager getShopManager() { return shopManager; }
    public MailboxManager getMailboxManager() { return mailboxManager; }
    public ShopGui getShopGui() { return shopGui; }
    public TpaManager getTpaManager() { return tpaManager; }
    public TeamManager getTeamManager() { return teamManager; }
    public TeamLevelManager getTeamLevelManager() { return teamLevelManager; }
    public TitleManager getTitleManager() { return titleManager; }
    public EconomyHelper getEconomy() { return economyHelper; }
    public StarterPackCommand getStarterPackCommand() { return starterPackCommand; }
    public AnnouncementManager getAnnouncementManager() { return announcementManager; }
    public AfkManager getAfkManager() { return afkManager; }
    public TabManager getTabManager() { return tabManager; }
    public WaypointManager getWaypointManager() { return waypointManager; }
    public AutoTitleManager getAutoTitleManager() { return autoTitleManager; }
    public SleepManager getSleepManager() { return sleepManager; }
    public ScheduleManager getScheduleManager() { return scheduleManager; }
    public SheetsManager getSheetsManager() { return sheetsManager; }
    public WarnManager getWarnManager() { return warnManager; }
    public LinkManager getLinkManager() { return linkManager; }
    public CompensationManager getCompensationManager() { return compensationManager; }
    public TeamMissionManager getTeamMissionManager() { return teamMissionManager; }
    public LetterManager getLetterManager() { return letterManager; }
    public LastWillManager getLastWillManager() { return lastWillManager; }
    public UuidMigrationManager getUuidMigrationManager() { return uuidMigrationManager; }
    public OnlineUuidResolver getUuidResolver() { return uuidResolver; }
    public DiscordBot getDiscordBot() { return discordBot; }
    public boolean isSmpActive() { return smpActive; }
    public void setSmpActive(boolean active) {
        this.smpActive = active;
        saveSmpState();
    }
    public boolean isSmpResetData() { return smpResetData; }
    public void setSmpResetData(boolean reset) { this.smpResetData = reset; }

    /** 기존 config.yml에 없는 새 키를 자동으로 추가 */
    private void migrateConfig() {
        boolean dirty = false;
        var cfg = getConfig();

        if (!cfg.contains("google.sheets.enabled")) {
            cfg.set("google.sheets.enabled", false);
            dirty = true;
        }
        if (!cfg.contains("google.sheets.api-key")) {
            cfg.set("google.sheets.api-key", "");
            dirty = true;
        }
        if (!cfg.contains("google.sheets.sheet-id")) {
            cfg.set("google.sheets.sheet-id", "");
            dirty = true;
        }
        // range는 항상 최신 그리드 형식으로 강제 업데이트
        if (!cfg.getString("google.sheets.range", "").equals("B2:J10")) {
            cfg.set("google.sheets.range", "B2:J10");
            dirty = true;
        }
        if (!cfg.contains("google.sheets.sync-interval-minutes")) {
            cfg.set("google.sheets.sync-interval-minutes", 5);
            dirty = true;
        }

        if (dirty) {
            saveConfig();
            getLogger().info("[Config] 새 설정 키가 config.yml에 추가되었습니다.");
        }
    }

    private void loadSmpState() {
        if (!smpStateFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(smpStateFile);
        smpActive = cfg.getBoolean("active", false);
        smpResetData = cfg.getBoolean("resetData", true);
        if (smpActive) getLogger().info("SMP 진행 중 상태 복원됨.");
    }

    private void saveSmpState() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("active", smpActive);
        cfg.set("resetData", smpResetData);
        try { cfg.save(smpStateFile); } catch (IOException e) {
            getLogger().warning("smp_state.yml 저장 실패: " + e.getMessage());
        }
    }
}
