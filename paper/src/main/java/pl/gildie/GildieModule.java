package pl.gildie;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import pl.gildie.db.Database;
import pl.gildie.db.GuildRepository;
import pl.gildie.db.MonumentRepository;
import pl.gildie.db.UserRepository;
import pl.gildie.managers.BuildLockManager;
import pl.gildie.managers.DigManager;
import pl.gildie.managers.GuildManager;
import pl.gildie.managers.PeriscopeManager;
import pl.gildie.managers.PingScoreboard;
import pl.gildie.managers.RegenManager;
import pl.gildie.managers.TerritoryBarManager;
import pl.gildie.model.Guild;
import pl.gildie.monument.BannerService;
import pl.gildie.monument.BannerWaypointTracker;
import pl.gildie.monument.GuildBonusManager;
import pl.gildie.monument.MonumentManager;
import pl.gildie.monument.MonumentScoreboard;
import pl.gildie.monument.PointsManager;
import pl.gildie.monument.commands.MonumentCommand;
import pl.gildie.monument.listeners.CrystalListener;
import pl.gildie.monument.listeners.MonumentBannerLockListener;
import pl.gildie.monument.listeners.MonumentEggListener;
import pl.gildie.monument.listeners.MonumentProtectionListener;
import pl.gildie.monument.util.MonumentBannerItem;
import pl.gildie.util.ItemCost;
import pl.gildie.util.WaypointHook;
import pl.gildie.war.EggHologram;
import pl.gildie.war.TntManager;
import pl.gildie.war.WarManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GildieModule {
    private final String serverId = UUID.randomUUID().toString();
    private final SectorSystemPlugin sector;

    private Database database;
    private GuildRepository guildRepository;
    private UserRepository userRepository;
    private MonumentRepository monumentRepository;

    private GuildManager guildManager;
    private RegenManager regenManager;
    private TerritoryBarManager territoryBarManager;
    private BuildLockManager buildLockManager;
    private DigManager digManager;
    private PeriscopeManager periscopeManager;
    private WarManager warManager;
    private EggHologram eggHologram;
    private MonumentManager monumentManager;
    private ItemCost inviteCost;
    private final Map<UUID, Long> inviteWandUsers = new ConcurrentHashMap<>();
    private boolean enabled = false;

    private FileConfiguration cfg;

    public GildieModule(SectorSystemPlugin sector) { this.sector = sector; }

    public JavaPlugin plugin() { return sector; }
    public FileConfiguration config() { return cfg; }
    public SectorSystemPlugin sector() { return sector; }

    private FileConfiguration buildConfig() {
        FileConfiguration c = new YamlConfiguration();
        c.set("invite-cost.DIAMOND", Const.INVITE_DIAMOND);
        c.set("war.egg-hits", Const.WAR_EGG_HITS);
        c.set("war.egg-regen-per-tick", Const.WAR_EGG_REGEN);
        c.set("war.egg-alert-every-hp", Const.WAR_EGG_ALERT_EVERY);
        c.set("war.min-hours", Const.WAR_MIN_HOURS);
        c.set("war.max-hours", Const.WAR_MAX_HOURS);
        c.set("tnt.start-hour", Const.TNT_START_HOUR);
        c.set("tnt.end-hour", Const.TNT_END_HOUR);
        c.set("tnt.timezone", Const.TNT_TIMEZONE);
        c.set("egg.room-y", Const.EGG_ROOM_Y);
        c.set("egg.room-radius", Const.EGG_ROOM_RADIUS);
        c.set("egg.material", Const.EGG_MATERIAL);
        return c;
    }

    public void enable() {
        cfg = buildConfig();

        database = new Database();
        database.init();
        if (database.isFailed()) {
            sector.getLogger().severe("GildieModule: MySQL niedostepny - modul wylaczony");
            return;
        }

        guildRepository = new GuildRepository(database);
        userRepository = new UserRepository(database);
        monumentRepository = new MonumentRepository(database);

        sector.getNatsService().subscribe("guilds.update", msg -> {
            String[] parts = msg.split(";");
            if (parts.length == 2 && !parts[1].equals(serverId)) guildManager.reloadAsync(parts[0]);
        });

        guildManager = new GuildManager(this);
        regenManager = new RegenManager(this);
        territoryBarManager = new TerritoryBarManager(sector, guildManager, regenManager);
        territoryBarManager.start();
        buildLockManager = new BuildLockManager(sector, guildManager);
        digManager = new DigManager(sector, guildManager);
        periscopeManager = new PeriscopeManager(sector, guildManager);
        warManager = new WarManager(this, guildManager);
        eggHologram = new EggHologram(sector);

        MonumentBannerItem.init(sector);
        monumentManager = new MonumentManager(sector, monumentRepository);
        PointsManager.init(monumentRepository);
        GuildBonusManager.init(sector, monumentRepository, guildManager);
        BannerService.init(sector);
        BannerWaypointTracker.init(sector);
        MonumentScoreboard.init(sector, monumentRepository, monumentManager);
        PingScoreboard.init(sector);

        Map<Material, Integer> defaults = new LinkedHashMap<>();
        defaults.put(Material.DIAMOND, Const.INVITE_DIAMOND);
        inviteCost = ItemCost.fromConfig(cfg.getConfigurationSection("invite-cost"), defaults);

        TntManager.start(this);

        var sch = sector.getServer().getScheduler();
        sch.runTaskTimer(sector, () -> guildManager.tickRaidBases(), 20L * 30, 20L * 30);
        sch.runTaskTimer(sector, () -> warManager.tick(), 20L * 20, 20L * 20);
        sch.runTaskTimer(sector, () -> warManager.tickEggRegen(), 20L * 10, 20L * 10);
        sch.runTaskTimer(sector, () -> warManager.flush(), 20L * 5, 20L * 5);
        sch.runTaskTimer(sector, () -> warManager.tickBannerWaypoints(), 40L, 30L);
        sch.runTaskTimer(sector, () -> guildManager.saveIfDirty(), 20L * 5, 20L * 5);
        sch.runTaskTimer(sector, () -> {
            long now = System.currentTimeMillis();
            for (Guild g : guildManager.getAll()) g.getPendingAlliance().entrySet().removeIf(e -> e.getValue() < now);
        }, 20L * 30, 20L * 30);

        sch.runTaskLater(sector, WaypointHook::reset, 40L);
        sch.runTaskLater(sector, () -> eggHologram.cleanupWorldAndRespawn(guildManager.getAll()), 60L);

        sector.getServer().getPluginManager().registerEvents(new CrystalListener(monumentManager, guildManager, monumentRepository), sector);
        sector.getServer().getPluginManager().registerEvents(new MonumentEggListener(monumentManager, guildManager), sector);
        sector.getServer().getPluginManager().registerEvents(new MonumentProtectionListener(monumentManager), sector);
        sector.getServer().getPluginManager().registerEvents(new MonumentBannerLockListener(sector, guildManager), sector);

        MonumentCommand.register(sector, monumentManager, guildManager);

        enabled = true;
        sector.getLogger().info("GildieModule (MySQL + Monument) aktywny na sektorze: "
                + pl.gildie.sector.SectorProvider.currentSector(sector));
    }

    public void disable() {
        if (!enabled) return;
        TntManager.stop();
        if (eggHologram != null) eggHologram.removeAll();
        if (territoryBarManager != null) territoryBarManager.shutdown();
        if (buildLockManager != null) buildLockManager.shutdown();
        if (guildManager != null) guildManager.save();
        if (regenManager != null) regenManager.saveSync();
        if (warManager != null) warManager.saveSync();
        BannerService.removeBannerItemEntities();
        GuildBonusManager.save();
        if (database != null) database.close();
        inviteWandUsers.clear();
    }

    public void publishGuildUpdate(String tag) {
        sector.getNatsService().publish("guilds.update", tag + ";" + serverId);
    }

    public GuildRepository getGuildRepository() { return guildRepository; }
    public UserRepository getUserRepository() { return userRepository; }
    public MonumentRepository getMonumentRepository() { return monumentRepository; }
    public pl.dzoku.sectorsystem.service.RedisService getRedisService() { return sector.getRedisService(); }
    public GuildManager getGuildManager() { return guildManager; }
    public RegenManager getRegenManager() { return regenManager; }
    public TerritoryBarManager getTerritoryBarManager() { return territoryBarManager; }
    public BuildLockManager getBuildLockManager() { return buildLockManager; }
    public DigManager getDigManager() { return digManager; }
    public PeriscopeManager getPeriscopeManager() { return periscopeManager; }
    public WarManager getWarManager() { return warManager; }
    public EggHologram getEggHologram() { return eggHologram; }
    public MonumentManager getMonumentManager() { return monumentManager; }
    public ItemCost getInviteCost() { return inviteCost; }
    public Map<UUID, Long> getInviteWandUsers() { return inviteWandUsers; }
}
