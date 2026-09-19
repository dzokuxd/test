package pl.dzoku.sectorsystem;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import pl.dzoku.sectorsystem.command.SectorCommand;
import pl.dzoku.sectorsystem.command.SpawnCommand;
import pl.dzoku.sectorsystem.config.ConfigManager;
import pl.dzoku.sectorsystem.listener.PendingTeleportListener;
import pl.dzoku.sectorsystem.listener.SectorBorderListener;
import pl.dzoku.sectorsystem.listener.TransferProtectionListener;
import pl.dzoku.sectorsystem.metrics.TransferMetrics;
import pl.dzoku.sectorsystem.restart.RestartManager;
import pl.dzoku.sectorsystem.service.HeartbeatService;
import pl.dzoku.sectorsystem.service.NatsService;
import pl.dzoku.sectorsystem.service.RedisService;
import pl.dzoku.sectorsystem.transfer.CleanupService;
import pl.dzoku.sectorsystem.transfer.PlayerStateSerializer;
import pl.dzoku.sectorsystem.transfer.TransferStateMachine;
import pl.gildie.GildieModule;
import pl.gildie.commands.GCommand;
import pl.gildie.commands.TntCommand;
import pl.gildie.listeners.ExplosionListener;
import pl.gildie.listeners.InventoryListener;
import pl.gildie.listeners.InviteWandListener;
import pl.gildie.listeners.JoinListener;
import pl.gildie.listeners.PeriscopeListener;
import pl.gildie.listeners.ProtectionListener;
import pl.gildie.listeners.TerritoryListener;
import pl.gildie.listeners.WarListener;
import pl.gildie.managers.TerritoryBarManager;
import pl.gildie.sector.SectorBridgeListener;
import pl.gildie.service.UserResyncTask;
import pl.gildie.service.UserSyncListener;

import java.io.File;

public final class SectorSystemPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private RedisService redisService;
    private NatsService natsService;
    private HeartbeatService heartbeatService;
    private TransferMetrics metrics;
    private TransferStateMachine transferStateMachine;
    private PlayerStateSerializer stateSerializer;
    private CleanupService cleanupService;
    private RestartManager restartManager;
    private GildieModule gildieModule;
    private BukkitTask metricsReportTask;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        String redisHost = getConfig().getString("redis.host", "localhost");
        int redisPort = getConfig().getInt("redis.port", 6379);
        String natsUrl = getConfig().getString("nats.url", "nats://localhost:4222");
        String currentSector = configManager.getCurrentSector();

        this.redisService = new RedisService(redisHost, redisPort, 20);
        this.natsService = new NatsService(natsUrl);
        this.heartbeatService = new HeartbeatService(redisService);
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

        this.restartManager = new RestartManager(this, redisService, natsService);
        natsService.subscribe("sector.restart", restartManager::handleRestartMessage);
        natsService.subscribe("sector.transfer.request." + currentSector, transferStateMachine::handleIncomingTransfer);

        heartbeatService.startHeartbeat(currentSector);
        metricsReportTask = getServer().getScheduler().runTaskTimerAsynchronously(this, metrics::printReport, 1200L, 1200L);

        // ── MODUL GILDII + MONUMENT ────────────────────────────────────────
        this.gildieModule = new GildieModule(this);
        gildieModule.enable();

        TerritoryBarManager bar = gildieModule.getTerritoryBarManager();
        GCommand gCommand = new GCommand(gildieModule, gildieModule.getGuildManager(),
                gildieModule.getRegenManager(), bar);
        getCommand("g").setExecutor(gCommand);
        getCommand("g").setTabCompleter(gCommand);

        getCommand("tnt").setExecutor(new TntCommand());

        getServer().getPluginManager().registerEvents(new ProtectionListener(gildieModule.getGuildManager(),
                gildieModule.getBuildLockManager(), gildieModule.getGuildRepository()), this);
        getServer().getPluginManager().registerEvents(new ExplosionListener(gildieModule.getGuildManager(), gildieModule.getRegenManager(), gildieModule.getBuildLockManager(), gildieModule.getWarManager()), this);
        getServer().getPluginManager().registerEvents(new TerritoryListener(bar, gildieModule.getRegenManager()), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(gildieModule.getDigManager()), this);
        getServer().getPluginManager().registerEvents(new InviteWandListener(gildieModule, gCommand), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this, gildieModule.getGuildManager()), this);
        getServer().getPluginManager().registerEvents(new PeriscopeListener(gildieModule.getPeriscopeManager()), this);
        getServer().getPluginManager().registerEvents(new WarListener(gildieModule.getGuildManager(), gildieModule.getWarManager()), this);
        getServer().getPluginManager().registerEvents(new UserSyncListener(gildieModule), this);
        getServer().getPluginManager().registerEvents(new SectorBridgeListener(gildieModule, gildieModule.getGuildManager()), this);
        new UserResyncTask(gildieModule).start();

        getLogger().info("SectorSystem v2.5 (gildie + monument) na sektorze: " + currentSector);
    }

    @Override
    public void onDisable() {
        if (gildieModule != null) gildieModule.disable();
        if (metricsReportTask != null) metricsReportTask.cancel();
        if (heartbeatService != null) heartbeatService.shutdown();
        if (redisService != null) redisService.shutdown();
        if (natsService != null) natsService.shutdown();
        getLogger().info("SectorSystem disabled");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public RedisService getRedisService() { return redisService; }
    public NatsService getNatsService() { return natsService; }
    public HeartbeatService getHeartbeatService() { return heartbeatService; }
    public TransferMetrics getMetrics() { return metrics; }
    public TransferStateMachine getTransferStateMachine() { return transferStateMachine; }
    public PlayerStateSerializer getStateSerializer() { return stateSerializer; }
    public CleanupService getCleanupService() { return cleanupService; }
    public RestartManager getRestartManager() { return restartManager; }
    public GildieModule getGildieModule() { return gildieModule; }
}
