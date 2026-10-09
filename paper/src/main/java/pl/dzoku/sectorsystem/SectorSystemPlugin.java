package pl.dzoku.sectorsystem;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import pl.dzoku.sectorsystem.chat.GlobalChatHandler;
import pl.dzoku.sectorsystem.chat.GlobalChatListener;
import pl.dzoku.sectorsystem.command.AfkCommand;
import pl.dzoku.sectorsystem.command.SectorCommand;
import pl.dzoku.sectorsystem.command.SpawnCommand;
import pl.dzoku.sectorsystem.command.StickCommand;
import pl.dzoku.sectorsystem.config.ConfigManager;
import pl.dzoku.sectorsystem.listener.MuteChatListener;
import pl.dzoku.sectorsystem.listener.PendingTeleportListener;
import pl.dzoku.sectorsystem.listener.SectorBorderListener;
import pl.dzoku.sectorsystem.listener.TransferProtectionListener;
import pl.dzoku.sectorsystem.manager.FailsafeManager;
import pl.dzoku.sectorsystem.metrics.TransferMetrics;
import pl.dzoku.sectorsystem.restart.RestartManager;
import pl.dzoku.sectorsystem.sync.WorldSyncManager;
import pl.dzoku.sectorsystem.tablist.SectorTablist;
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
import pl.sectorsystem.common.config.SystemConfig;
import pl.sectorsystem.common.messaging.SectorMessage;
import pl.sectorsystem.common.mysql.MySQLService;
import pl.sectorsystem.common.nats.NatsService;
import pl.sectorsystem.common.redis.RedisService;

public final class SectorSystemPlugin extends JavaPlugin {

    private static SectorSystemPlugin instance;

    // ── KONFIGURACJA ──────────────────────────────────────────────────────
    private ConfigManager configManager;
    private SystemConfig systemConfig;

    // ── USŁUGI SIECIOWE I BAZA DANYCH ─────────────────────────────────────
    private RedisService redisService;
    private NatsService natsService;
    private MySQLService mysqlService;

    // ── SYSTEM TRANSFERU MIĘDZY SEKTORAMI ─────────────────────────────────
    private TransferMetrics metrics;
    private TransferStateMachine transferStateMachine;
    private PlayerStateSerializer stateSerializer;
    private CleanupService cleanupService;
    private RestartManager restartManager;

    // ── MODUŁY GRY ────────────────────────────────────────────────────────
    private GildieModule gildieModule;
    private SectorTablist tablist;
    private CowStackManager stackManager;

    // ── ZADANIA HARMONOGRAMU ──────────────────────────────────────────────
    private BukkitTask metricsReportTask;
    private BukkitTask heartbeatTask;

    @Override
    public void onEnable() {
        instance = this;
        this.configManager = new ConfigManager(this);
        String currentSector = configManager.getCurrentSector();

        // 1. Inicjalizacja konfiguracji
        this.systemConfig = new SystemConfig();
        systemConfig.getRedis().setHost(getConfig().getString("redis.host", "localhost"));
        systemConfig.getRedis().setPort(getConfig().getInt("redis.port", 6379));
        systemConfig.getRedis().setPassword(getConfig().getString("redis.password", ""));
        systemConfig.getRedis().setDatabase(getConfig().getInt("redis.database", 0));
        systemConfig.getNats().setUrl(getConfig().getString("nats.url", "nats://localhost:4222"));

        // 2. Uruchomienie usług sieciowych
        try {
            this.redisService = new RedisService(systemConfig.getRedis());
            this.natsService = new NatsService(systemConfig.getNats());
        } catch (Exception e) {
            getLogger().severe("Nie udało się połączyć z Redis/NATS: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. System transferu między serwerami
        this.metrics = new TransferMetrics();
        this.stateSerializer = new PlayerStateSerializer(this);
        this.cleanupService = new CleanupService(this, redisService);
        this.transferStateMachine = new TransferStateMachine(
                this, redisService, natsService, stateSerializer, cleanupService, metrics, configManager);

        // 4. Rejestracja listenerów podstawowych
        getServer().getPluginManager().registerEvents(new SectorBorderListener(this), this);
        getServer().getPluginManager().registerEvents(new TransferProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(transferStateMachine, this);
        getServer().getPluginManager().registerEvents(new PendingTeleportListener(this), this);
        getServer().getPluginManager().registerEvents(new MuteChatListener(this), this);

        // 5. Rejestracja komend
        getCommand("sector").setExecutor(new SectorCommand(this));

        SpawnCommand spawnCmd = new SpawnCommand(this);
        getCommand("spawn").setExecutor(spawnCmd);
        getServer().getPluginManager().registerEvents(spawnCmd, this);

        if (getCommand("afk") != null) getCommand("afk").setExecutor(new AfkCommand(this));
        if (getCommand("stick") != null) getCommand("stick").setExecutor(new StickCommand(this));

        // 6. Subskrypcje NATS i Restart Manager
        this.restartManager = new RestartManager(this, redisService, natsService);
        natsService.subscribeRaw("restart", restartManager::handleRestartMessage);
        natsService.subscribe("sector.transfer.request." + currentSector, transferStateMachine::handleIncomingTransfer);

        // 7. Heartbeat (oznaczenie sektora jako online)
        sendInitialHeartbeat(currentSector);
        heartbeatTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                redisService.setSectorOnline(currentSector, true);
                redisService.setSectorPlayerCount(currentSector, getServer().getOnlinePlayers().size());
                var hb = new SectorMessage(SectorMessage.Type.HEARTBEAT);
                hb.setFromSector(currentSector);
                natsService.publish(hb);
            } catch (Exception e) {
                getLogger().warning("Heartbeat failed: " + e.getMessage());
            }
        }, 20L * 15, 20L * 15);

        metricsReportTask = getServer().getScheduler().runTaskTimerAsynchronously(this, metrics::printReport, 1200L, 1200L);

        // 8. Inicjalizacja MySQL
        systemConfig.getMysql().setEnabled(getConfig().getBoolean("mysql.enabled", false));
        systemConfig.getMysql().setHost(getConfig().getString("mysql.host", "127.0.0.1"));
        systemConfig.getMysql().setPort(getConfig().getInt("mysql.port", 3306));
        systemConfig.getMysql().setDatabase(getConfig().getString("mysql.database", "sectorsystem"));
        systemConfig.getMysql().setUsername(getConfig().getString("mysql.username", "root"));
        systemConfig.getMysql().setPassword(getConfig().getString("mysql.password", ""));
        this.mysqlService = new MySQLService(systemConfig.getMysql());

        // 9. Moduł Gildii + Monument
        this.gildieModule = new GildieModule(this);
        gildieModule.setMysqlService(mysqlService);
        gildieModule.enable();

        TerritoryBarManager bar = gildieModule.getTerritoryBarManager();
        GuildCommand guildCommand = new GuildCommand(
                gildieModule, gildieModule.getGuildManager(), gildieModule.getRegenManager(),
                bar, gildieModule.getRatingManager(), gildieModule.getModularScoreboardManager());

        getCommand("g").setExecutor(guildCommand);
        getCommand("g").setTabCompleter(guildCommand);
        getCommand("tnt").setExecutor(new TntCommand());

        // Listenery gildii
        getServer().getPluginManager().registerEvents(new ProtectionListener(gildieModule.getGuildManager(), gildieModule.getBuildLockManager(), gildieModule.getGuildRepository()), this);
        getServer().getPluginManager().registerEvents(new ExplosionListener(gildieModule.getGuildManager(), gildieModule.getRegenManager(), gildieModule.getBuildLockManager(), gildieModule.getWarManager()), this);
        getServer().getPluginManager().registerEvents(new TerritoryListener(bar, gildieModule.getRegenManager()), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(gildieModule.getDigManager()), this);
        getServer().getPluginManager().registerEvents(new InviteWandListener(gildieModule, guildCommand), this);
        getServer().getPluginManager().registerEvents(new PeriscopeListener(gildieModule.getPeriscopeManager()), this);
        getServer().getPluginManager().registerEvents(new WarListener(gildieModule.getGuildManager(), gildieModule.getWarManager()), this);
        getServer().getPluginManager().registerEvents(new UserSyncListener(gildieModule), this);
        getServer().getPluginManager().registerEvents(new SectorBridgeListener(gildieModule, gildieModule.getGuildManager()), this);
        this.stackManager = new CowStackManager(this);
        getServer().getPluginManager().registerEvents(new CowStackListener(this, stackManager), this);
        getServer().getPluginManager().registerEvents(new GuildDialogListener(guildCommand), this);

        new UserResyncTask(gildieModule).start();
        new SugarcaneTask().runTaskTimer(this, 600L, 600L);

        // 10. Moduły UI i Chat
        new GlobalChatHandler(this);
        new FailsafeManager(this);
        this.tablist = new SectorTablist(this);
        getServer().getPluginManager().registerEvents(new GlobalChatListener(this), this);

        if (getConfig().getBoolean("world-sync.enabled", true)) {
            new WorldSyncManager(this);
        }

        getLogger().info("✓ SectorSystem v2.5 (gildie + monument) aktywny na sektorze: " + currentSector);
    }

    private void sendInitialHeartbeat(String currentSector) {
        try {
            redisService.setSectorOnline(currentSector, true);
            redisService.setSectorPlayerCount(currentSector, 0);
            var hb = new SectorMessage(SectorMessage.Type.HEARTBEAT);
            hb.setFromSector(currentSector);
            natsService.publish(hb);
            getLogger().info("✓ Sektor " + currentSector + " oznaczony jako online w Redis");
        } catch (Exception e) {
            getLogger().warning("Initial heartbeat failed: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        // 1. Zatrzymanie modułu gildii (w tym czyszczenie kryształów End Crystal)
        if (gildieModule != null) {
            if (gildieModule.getMonumentManager() != null) {
                gildieModule.getMonumentManager().shutdown();
            }
            gildieModule.disable();
        }

        // 2. Zatrzymanie zadań
        if (metricsReportTask != null) metricsReportTask.cancel();
        if (heartbeatTask != null) heartbeatTask.cancel();

        // 3. Zamknięcie połączeń sieciowych i bazy danych
        if (mysqlService != null) mysqlService.close();

        if (redisService != null) {
            try {
                String sector = configManager.getCurrentSector();
                redisService.setSectorOnline(sector, false);
                redisService.setSectorPlayerCount(sector, 0);
                redisService.close();
            } catch (Exception ignored) {}
        }

        if (natsService != null) {
            try {
                natsService.close();
            } catch (Exception ignored) {}
        }

        getLogger().info("✓ SectorSystem wyłączony pomyślnie.");
    }

    // ── GETTERY ───────────────────────────────────────────────────────────
    public static SectorSystemPlugin getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public SystemConfig getSystemConfig() { return systemConfig; }
    public RedisService getRedisService() { return redisService; }
    public NatsService getNatsService() { return natsService; }
    public MySQLService getMysqlService() { return mysqlService; }
    public MySQLService getMysql() { return mysqlService; } // Alias dla kompatybilności
    public TransferStateMachine getTransferStateMachine() { return transferStateMachine; }
    public PlayerStateSerializer getStateSerializer() { return stateSerializer; }
    public RestartManager getRestartManager() { return restartManager; }
    public GildieModule getGildieModule() { return gildieModule; }
    public SectorTablist getTablist() { return tablist; }
    public String getCurrentSectorId() { return configManager.getCurrentSector(); }
    public CowStackManager getStackManager() { return stackManager; }
}