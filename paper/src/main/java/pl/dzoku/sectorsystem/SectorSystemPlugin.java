package pl.dzoku.sectorsystem;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import pl.dzoku.sectorsystem.command.AfkCommand;
import pl.dzoku.sectorsystem.command.SectorCommand;
import pl.dzoku.sectorsystem.command.SpawnCommand;
import pl.dzoku.sectorsystem.command.StickCommand;
import pl.dzoku.sectorsystem.config.ConfigManager;
import pl.dzoku.sectorsystem.listener.PendingTeleportListener;
import pl.dzoku.sectorsystem.listener.SectorBorderListener;
import pl.dzoku.sectorsystem.listener.TransferProtectionListener;
import pl.dzoku.sectorsystem.metrics.TransferMetrics;
import pl.dzoku.sectorsystem.restart.RestartManager;
import pl.dzoku.sectorsystem.sync.WorldSyncManager;
import pl.dzoku.sectorsystem.transfer.CleanupService;
import pl.dzoku.sectorsystem.transfer.PlayerStateSerializer;
import pl.dzoku.sectorsystem.transfer.TransferStateMachine;
import pl.gildie.GildieModule;
import pl.gildie.commands.GuildCommand;
import pl.gildie.commands.TntCommand;
import pl.gildie.listeners.*;
import pl.gildie.managers.CowStackManager;
import pl.gildie.managers.TerritoryBarManager;
import pl.gildie.sector.SectorBridgeListener;
import pl.gildie.service.SugarcaneTask;
import pl.gildie.service.UserResyncTask;
import pl.gildie.listeners.UserSyncListener;
import pl.sectorsystem.common.mysql.MySQLService;
import pl.sectorsystem.common.nats.NatsService;
import pl.sectorsystem.common.redis.RedisService;

public final class SectorSystemPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private RedisService redisService;
    private NatsService natsService;
    private TransferMetrics metrics;
    private TransferStateMachine transferStateMachine;
    private PlayerStateSerializer stateSerializer;
    private CleanupService cleanupService;
    private RestartManager restartManager;
    private MySQLService mysqlService;
    private GildieModule gildieModule;
    private pl.sectorsystem.common.config.SystemConfig systemConfig;
    //private pl.dzoku.sectorsystem.scoreboard.SectorScoreboard scoreboard;
    private pl.dzoku.sectorsystem.tablist.SectorTablist tablist;
    private static SectorSystemPlugin instance;
    private BukkitTask metricsReportTask;
    private BukkitTask heartbeatTask;
    private CowStackManager stackManager;
    private pl.discord.DiscordManager discordManager;
    private pl.discord.DiscordDatabase discordDatabase;

    @Override
    public void onEnable() {
        stackManager = new CowStackManager(this);
        instance = this;
        this.configManager = new ConfigManager(this);

        // Wczytaj SystemConfig z common i nadpisz z config.yml
        this.systemConfig = new pl.sectorsystem.common.config.SystemConfig();
        systemConfig.getRedis().setHost(getConfig().getString("redis.host", "localhost"));
        systemConfig.getRedis().setPort(getConfig().getInt("redis.port", 6379));
        systemConfig.getRedis().setPassword(getConfig().getString("redis.password", ""));
        systemConfig.getRedis().setDatabase(getConfig().getInt("redis.database", 0));
        systemConfig.getNats().setUrl(getConfig().getString("nats.url", "nats://localhost:4222"));

        String currentSector = configManager.getCurrentSector();

        // Utwórz usługi z common używając SystemConfig
        try {
            this.redisService = new RedisService(systemConfig.getRedis());
            this.natsService = new NatsService(systemConfig.getNats());
        } catch (Exception e) {
            getLogger().severe("Nie udało się połączyć z Redis/NATS: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.metrics = new TransferMetrics();
        this.stateSerializer = new PlayerStateSerializer(this);
        this.cleanupService = new CleanupService(this, redisService);
        this.transferStateMachine = new TransferStateMachine(
                this, redisService, natsService, stateSerializer, cleanupService, metrics, configManager);

        getServer().getPluginManager().registerEvents(new SectorBorderListener(this), this);
        getServer().getPluginManager().registerEvents(new TransferProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(transferStateMachine, this);
        getServer().getPluginManager().registerEvents(new PendingTeleportListener(this), this);

        getCommand("sector").setExecutor(new SectorCommand(this));
        SpawnCommand spawnCmd = new SpawnCommand(this);
        getCommand("spawn").setExecutor(spawnCmd);
        getServer().getPluginManager().registerEvents(spawnCmd, this);

        AfkCommand afkCmd = new AfkCommand(this);
        if (getCommand("afk") != null) {
            getCommand("afk").setExecutor(afkCmd);
        }

        if (getCommand("stick") != null) {
            getCommand("stick").setExecutor(new StickCommand(this));
        }

        this.restartManager = new RestartManager(this, redisService, natsService);
        natsService.subscribeRaw("restart", restartManager::handleRestartMessage);
        natsService.subscribe("sector.transfer.request." + currentSector, transferStateMachine::handleIncomingTransfer);

        // ═══════════════════════════════════════════════════════════════
        // HEARTBEAT - NATYCHMIASTOWE WYKONANIE PRZY STARCIЕ
        // ═══════════════════════════════════════════════════════════════
        try {
            redisService.setSectorOnline(currentSector, true);
            redisService.setSectorPlayerCount(currentSector, 0);
            var hb = new pl.sectorsystem.common.messaging.SectorMessage(
                    pl.sectorsystem.common.messaging.SectorMessage.Type.HEARTBEAT);
            hb.setFromSector(currentSector);
            natsService.publish(hb);
            getLogger().info("✓ Sektor " + currentSector + " oznaczony jako online w Redis");
        } catch (Exception e) {
            getLogger().warning("Initial heartbeat failed: " + e.getMessage());
        }

        // Potem kontynuuj cyklicznie co 15 sekund
        heartbeatTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                redisService.setSectorOnline(currentSector, true);
                redisService.setSectorPlayerCount(currentSector, getServer().getOnlinePlayers().size());
                var hb = new pl.sectorsystem.common.messaging.SectorMessage(
                        pl.sectorsystem.common.messaging.SectorMessage.Type.HEARTBEAT);
                hb.setFromSector(currentSector);
                natsService.publish(hb);
            } catch (Exception e) {
                getLogger().warning("Heartbeat failed: " + e.getMessage());
            }
        }, 20L * 15, 20L * 15);

        metricsReportTask = getServer().getScheduler().runTaskTimerAsynchronously(this, metrics::printReport, 1200L, 1200L);

        // ── MYSQL SERVICE ────────────────────────────────────────────────
        systemConfig.getMysql().setEnabled(getConfig().getBoolean("mysql.enabled", false));
        systemConfig.getMysql().setHost(getConfig().getString("mysql.host", "127.0.0.1"));
        systemConfig.getMysql().setPort(getConfig().getInt("mysql.port", 3306));
        systemConfig.getMysql().setDatabase(getConfig().getString("mysql.database", "sectorsystem"));
        systemConfig.getMysql().setUsername(getConfig().getString("mysql.username", "root"));
        systemConfig.getMysql().setPassword(getConfig().getString("mysql.password", ""));
        this.mysqlService = new MySQLService(systemConfig.getMysql());

        // ─ MODUŁ GILDII + MONUMENT ───────────────────────────────────────
        this.gildieModule = new GildieModule(this);
        gildieModule.setMysqlService(mysqlService);
        gildieModule.enable();

        TerritoryBarManager bar = gildieModule.getTerritoryBarManager();
        GuildCommand guildCommand = new GuildCommand(gildieModule, gildieModule.getGuildManager(), gildieModule.getRegenManager(), bar, gildieModule.getRatingManager(), gildieModule.getModularScoreboardManager());
        getCommand("g").setExecutor(guildCommand);
        getCommand("g").setTabCompleter(guildCommand);
        getCommand("tnt").setExecutor(new TntCommand());

        getServer().getPluginManager().registerEvents(new ProtectionListener(gildieModule.getGuildManager(),
                gildieModule.getBuildLockManager(), gildieModule.getGuildRepository()), this);
        getServer().getPluginManager().registerEvents(new ExplosionListener(gildieModule.getGuildManager(),
                gildieModule.getRegenManager(), gildieModule.getBuildLockManager(), gildieModule.getWarManager()), this);
        getServer().getPluginManager().registerEvents(new TerritoryListener(bar, gildieModule.getRegenManager()), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(gildieModule.getDigManager()), this);
        getServer().getPluginManager().registerEvents(new InviteWandListener(gildieModule, guildCommand), this);
        getServer().getPluginManager().registerEvents(new PeriscopeListener(gildieModule.getPeriscopeManager()), this);
        getServer().getPluginManager().registerEvents(new WarListener(gildieModule.getGuildManager(), gildieModule.getWarManager()), this);
        getServer().getPluginManager().registerEvents(new UserSyncListener(gildieModule), this);
        getServer().getPluginManager().registerEvents(new SectorBridgeListener(gildieModule, gildieModule.getGuildManager()), this);
        getServer().getPluginManager().registerEvents(new CowStackListener(this, stackManager), this);
        getServer().getPluginManager().registerEvents(new GuildDialogListener(guildCommand), this);
        new UserResyncTask(gildieModule).start();
        new SugarcaneTask().runTaskTimer(this, 600L, 600L);

        // ── MODUŁY UI ────────────────────────────────────────────────────
        new pl.dzoku.sectorsystem.chat.GlobalChatHandler(this);
        new pl.dzoku.sectorsystem.manager.FailsafeManager(this);

        if (getConfig().getBoolean("world-sync.enabled", true)) {
            new WorldSyncManager(this);
        }
        // === MODUŁ DISCORD ===
        if (getCommand("discord") != null) {
            getCommand("discord").setExecutor(new pl.discord.DiscordCommand(this));
        }
        if (getConfig().getBoolean("discord.enabled", false)) {
            this.discordDatabase = new pl.discord.DiscordDatabase(this);
            this.discordManager = new pl.discord.DiscordManager(this);
            if (this.discordManager.getJda() != null) {
                this.discordManager.getJda().addEventListener(new pl.discord.DiscordCommands(this));
            }
        }
        this.discordManager.startBot();
        getServer().getPluginManager().registerEvents(new pl.discord.GuildLeaderRoleListener(this), this);

        //this.scoreboard = new pl.dzoku.sectorsystem.scoreboard.SectorScoreboard(this);
        this.tablist = new pl.dzoku.sectorsystem.tablist.SectorTablist(this);
        getServer().getPluginManager().registerEvents(new pl.dzoku.sectorsystem.chat.GlobalChatListener(this), this);

        getLogger().info("SectorSystem v2.5 (gildie + monument) na sektorze: " + currentSector);
    }

    @Override
    public void onDisable() {
        if (this.discordManager != null) {
            this.discordManager.shutdown();
        }
        if (this.discordDatabase != null) {
            this.discordDatabase.close();
        }
        // ── pkt 3: CLEAR END CRYSTALS PRZED wyłączeniem modułu gildii ───
        if (gildieModule != null && gildieModule.getMonumentManager() != null) {
            gildieModule.getMonumentManager().shutdown();
        }
        // ──────────────────────────────────────────────────────────────────

        if (gildieModule != null) gildieModule.disable();
        if (mysqlService != null) mysqlService.close();
        if (metricsReportTask != null) metricsReportTask.cancel();
        if (heartbeatTask != null) heartbeatTask.cancel();
        if (redisService != null) {
            try {
                redisService.setSectorOnline(configManager.getCurrentSector(), false);
                redisService.setSectorPlayerCount(configManager.getCurrentSector(), 0);
                redisService.close();
            } catch (Exception ignored) {}
        }
        if (natsService != null) {
            try {
                natsService.close();
            } catch (Exception ignored) {}
        }
        getLogger().info("SectorSystem disabled");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public RedisService getRedisService() { return redisService; }
    public NatsService getNatsService() { return natsService; }
    public TransferStateMachine getTransferStateMachine() { return transferStateMachine; }
    public PlayerStateSerializer getStateSerializer() { return stateSerializer; }
    public RestartManager getRestartManager() { return restartManager; }
    public GildieModule getGildieModule() { return gildieModule; }
    public MySQLService getMysqlService() { return mysqlService; }
    //public pl.dzoku.sectorsystem.scoreboard.SectorScoreboard getScoreboard() { return scoreboard; }
    public pl.dzoku.sectorsystem.tablist.SectorTablist getTablist() { return tablist; }
    public String getCurrentSectorId() { return configManager.getCurrentSector(); }
    public static SectorSystemPlugin getInstance() { return instance; }
    public CowStackManager getStackManager() {return stackManager;}
    public pl.discord.DiscordManager getDiscordManager() {return discordManager;}

    public pl.discord.DiscordDatabase getDiscordDatabase() {return discordDatabase;}

}