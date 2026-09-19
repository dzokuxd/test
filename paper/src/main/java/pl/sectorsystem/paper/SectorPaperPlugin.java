package pl.sectorsystem.paper;

import org.bukkit.plugin.java.JavaPlugin;
import pl.sectorsystem.common.config.SystemConfig;
import pl.sectorsystem.common.mysql.MySQLService;
import pl.sectorsystem.common.nats.NatsService;
import pl.sectorsystem.common.redis.RedisService;
import pl.sectorsystem.paper.api.PaperSectorAPI;
import pl.sectorsystem.paper.chat.GlobalChatHandler;
import pl.sectorsystem.paper.chat.GlobalChatListener;
import pl.sectorsystem.paper.command.AfkCommand;
import pl.sectorsystem.paper.command.SectorAdminCommand;
import pl.sectorsystem.paper.command.SpawnCommand;
import pl.sectorsystem.paper.command.StickCommand;
import pl.sectorsystem.paper.listener.DeathListener;
import pl.sectorsystem.paper.listener.JoinListener;
import pl.sectorsystem.paper.listener.PlayerListener;
import pl.sectorsystem.paper.listener.StickListener;
import pl.sectorsystem.paper.manager.FailsafeManager;
import pl.sectorsystem.paper.manager.SectorManager;
import pl.sectorsystem.paper.manager.TransferManager;
import pl.sectorsystem.paper.scoreboard.SectorScoreboard;
import pl.sectorsystem.paper.sync.WorldSyncManager;
import pl.sectorsystem.paper.tablist.SectorTablist;

public class SectorPaperPlugin extends JavaPlugin {

    private static SectorPaperPlugin instance;
    private SystemConfig systemConfig;
    private RedisService redis;
    private NatsService nats;
    private MySQLService mysql;
    private SectorManager sectorManager;
    private TransferManager transferManager;
    private PaperSectorAPI api;
    private SectorScoreboard scoreboard;
    private SectorTablist tablist;
    private String currentSectorId;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        try {
            this.systemConfig = new SystemConfig();
            applyConfigToSystem();

            this.currentSectorId = getConfig().getString("current-sector", "guild");

            this.redis = new RedisService(systemConfig.getRedis());
            this.nats = new NatsService(systemConfig.getNats());
            this.mysql = new MySQLService(systemConfig.getMysql());

            this.sectorManager = new SectorManager(this);
            this.transferManager = new TransferManager(this);
            this.api = new PaperSectorAPI(this);

            // Heartbeat + online count
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                try {
                    redis.setSectorOnline(currentSectorId, true);
                    redis.setSectorPlayerCount(currentSectorId, getServer().getOnlinePlayers().size());
                    var hb = new pl.sectorsystem.common.messaging.SectorMessage(
                            pl.sectorsystem.common.messaging.SectorMessage.Type.HEARTBEAT);
                    hb.setFromSector(currentSectorId);
                    nats.publish(hb);
                } catch (Exception e) {
                    getLogger().warning("Heartbeat failed: " + e.getMessage());
                }
            }, 20L, 20L * 15);

            new GlobalChatHandler(this);
            new FailsafeManager(this);
            if (getConfig().getBoolean("world-sync.enabled", true)) {
                new WorldSyncManager(this);
            }
            this.scoreboard = new SectorScoreboard(this);
            this.tablist = new SectorTablist(this);

            getServer().getPluginManager().registerEvents(new GlobalChatListener(this), this);
            getServer().getPluginManager().registerEvents(new DeathListener(this), this);
            getServer().getPluginManager().registerEvents(new StickListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
            getServer().getPluginManager().registerEvents(new JoinListener(this), this);

            getCommand("spawn").setExecutor(new SpawnCommand(this));
            getCommand("afk").setExecutor(new AfkCommand(this));
            getCommand("sector").setExecutor(new SectorAdminCommand(this));
            if (getCommand("sectorstick") != null) {
                getCommand("sectorstick").setExecutor(new StickCommand(this));
            }

            getLogger().info("SectorSystem Paper ON | sektor=" + currentSectorId
                    + " | mysql=" + (mysql != null && mysql.isEnabled()));
        } catch (Exception e) {
            getLogger().severe("Start failed: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void applyConfigToSystem() {
        systemConfig.getRedis().setHost(getConfig().getString("redis.host", "127.0.0.1"));
        systemConfig.getRedis().setPort(getConfig().getInt("redis.port", 6379));
        systemConfig.getRedis().setPassword(getConfig().getString("redis.password", ""));
        systemConfig.getRedis().setDatabase(getConfig().getInt("redis.database", 0));
        systemConfig.getNats().setUrl(getConfig().getString("nats.url", "nats://127.0.0.1:4222"));
        systemConfig.getMysql().setEnabled(getConfig().getBoolean("mysql.enabled", false));
        systemConfig.getMysql().setHost(getConfig().getString("mysql.host", "127.0.0.1"));
        systemConfig.getMysql().setPort(getConfig().getInt("mysql.port", 3306));
        systemConfig.getMysql().setDatabase(getConfig().getString("mysql.database", "sectorsystem"));
        systemConfig.getMysql().setUsername(getConfig().getString("mysql.username", "root"));
        systemConfig.getMysql().setPassword(getConfig().getString("mysql.password", ""));
    }

    public void reloadPluginConfig() {
        reloadConfig();
        applyConfigToSystem();
        if (sectorManager != null) sectorManager.reloadLimits();
        getLogger().info("Config przeładowany.");
    }

    @Override
    public void onDisable() {
        try {
            if (redis != null) {
                redis.setSectorOnline(currentSectorId, false);
                redis.setSectorPlayerCount(currentSectorId, 0);
                redis.close();
            }
        } catch (Exception ignored) {}
        try { if (nats != null) nats.close(); } catch (Exception ignored) {}
        try { if (mysql != null) mysql.close(); } catch (Exception ignored) {}
        getLogger().info("SectorSystem Paper OFF");
    }

    public static SectorPaperPlugin getInstance() { return instance; }
    public SystemConfig getSystemConfig() { return systemConfig; }
    public RedisService getRedis() { return redis; }
    public NatsService getNats() { return nats; }
    public MySQLService getMysql() { return mysql; }
    public SectorManager getSectorManager() { return sectorManager; }
    public TransferManager getTransferManager() { return transferManager; }
    public PaperSectorAPI getApi() { return api; }
    public SectorScoreboard getScoreboard() { return scoreboard; }
    public SectorTablist getTablist() { return tablist; }
    public String getCurrentSectorId() { return currentSectorId; }
}
