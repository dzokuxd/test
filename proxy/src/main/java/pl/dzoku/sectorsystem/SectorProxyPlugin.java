package pl.dzoku.sectorsystem;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.command.AuthCommand;
import pl.dzoku.sectorsystem.command.BanCommand;
import pl.dzoku.sectorsystem.command.MuteCommand;
import pl.dzoku.sectorsystem.command.UnbanCommand;
import pl.dzoku.sectorsystem.command.UnmuteCommand;
import pl.dzoku.sectorsystem.discord.*;
import pl.dzoku.sectorsystem.listener.AuthListener;
import pl.dzoku.sectorsystem.listener.LimboQueueListener;
import pl.dzoku.sectorsystem.listener.VersionGuardListener;
import pl.dzoku.sectorsystem.managers.AuthManager;
import pl.dzoku.sectorsystem.managers.MySQLDiagnostic;
import pl.dzoku.sectorsystem.managers.ProxyConfigManager;
import pl.dzoku.sectorsystem.managers.QueueManager;
import pl.dzoku.sectorsystem.auth.LoginTask;
import pl.dzoku.sectorsystem.service.HeartbeatService;
import pl.dzoku.sectorsystem.service.NatsService;
import pl.dzoku.sectorsystem.service.RedisService;
import pl.sectorsystem.common.config.SystemConfig;
import pl.sectorsystem.common.mysql.MySQLService;

import java.nio.file.Path;
import java.util.logging.Logger;

@Plugin(id = "sectorsystem-proxy",
        name = "SectorSystem Proxy",
        version = "2.2.0",
        authors = {"dzokuxd"})
public class SectorProxyPlugin {

    private static SectorProxyPlugin instance;

    private final ProxyServer server;
    private final Logger logger;
    private RedisService redisService;
    private NatsService natsService;
    private HeartbeatService heartbeatService;
    private SectorHealthChecker healthChecker;
    private TransferRouter transferRouter;
    private RestartOrchestrator orchestrator;
    private LimboScoreboard limboScoreboard;
    private QueueManager queueManager;
    private MySQLService mysqlService;
    private SystemConfig config;
    private ProxyConfigManager cfg;
    private LoginTask loginTask;
    private DiscordManager discordManager;
    private DiscordDatabase discordDatabase;

    @Inject
    public SectorProxyPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        instance = this;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        Path dataDirectory = Path.of(System.getProperty("user.dir"), "plugins", "sectorsystem-proxy");
        try {
            java.nio.file.Files.createDirectories(dataDirectory);
        } catch (Exception e) {
            logger.warning("Nie udało się utworzyć folderu danych: " + e.getMessage());
        }

        this.cfg = new ProxyConfigManager(dataDirectory, logger);
        String redisHost = cfg.getRedisHost();
        int redisPort = cfg.getRedisPort();
        String natsUrl = cfg.getNatsUrl();
        logger.info("Config: Redis=" + redisHost + ":" + redisPort + " NATS=" + natsUrl);
        this.config = new SystemConfig();
        this.redisService = new RedisService(redisHost, redisPort, 10);
        this.natsService = new NatsService(natsUrl);
        this.mysqlService = new MySQLService(config.getMysql());

        MySQLDiagnostic.runDiagnostic(config.getMysql());

        this.heartbeatService = new HeartbeatService(redisService);
        this.healthChecker = new SectorHealthChecker(server, heartbeatService, logger);
        this.transferRouter = new TransferRouter(server, redisService, natsService, healthChecker, logger, this);
        this.orchestrator = new RestartOrchestrator(server, redisService, healthChecker, logger);
        this.orchestrator.start();
        this.limboScoreboard = new LimboScoreboard(server, redisService, orchestrator);
        this.limboScoreboard.start();
        this.queueManager = new QueueManager(this);

        natsService.subscribe("sector.transfer.request", message -> transferRouter.handleTransferRequest(message));
        natsService.subscribe("sector.restart", message -> orchestrator.handleRestartMessage(message));

        server.getEventManager().register(this, new pl.dzoku.sectorsystem.listener.BanLoginListener(this));
        server.getEventManager().register(this, new VersionGuardListener());
        server.getEventManager().register(this, new LimboQueueListener(this));
        server.getEventManager().register(this, orchestrator);
        server.getEventManager().register(this, new AuthListener(this));
        server.getEventManager().register(this, new pl.dzoku.sectorsystem.listener.ServerListListener(this));
        server.getEventManager().register(this, new pl.dzoku.sectorsystem.listener.WhitelistLoginListener(this));
        server.getEventManager().register(this, new pl.dzoku.sectorsystem.discord.GuildLeaderRoleListener(this));

        // === DISCORD - INICJALIZACJA ===
        this.discordDatabase = new DiscordDatabase(this.mysqlService);
        this.discordManager = new DiscordManager(
                this,
                cfg.getDiscordToken(),
                cfg.getDiscordGuildId(),
                cfg.getDiscordVerifiedRoleId(),
                cfg.getDiscordLeaderRoleId(),
                cfg.getDiscordStaffRoleId()
        );
        this.discordManager.startBot();

        if (this.discordManager.getJda() != null) {
            this.discordManager.getJda().addEventListener(new DiscordCommands(this));
            // Auto-generacja embedów po 3 sekundach (żeby bot się w pełni załadował)
            server.getScheduler().buildTask(this, () -> {
                new DiscordEmbedManager(this).createAllEmbeds();
            }).delay(java.time.Duration.ofSeconds(3)).schedule();
        }
        healthChecker.start();
        this.loginTask = new LoginTask(this, 60);
        this.loginTask.start();
        new pl.dzoku.sectorsystem.managers.ExpiredPunishmentsCleaner(this).start();
        registerAuthCommands();
        server.getCommandManager().register("discord", new DiscordCommand(this));

        logger.info("SectorSystem Proxy v2.2 initialized (Auth + Sectors ready)");
    }

    private void registerAuthCommands() {
        var cm = server.getCommandManager();

        // Komendy moderacyjne
        cm.register("ban", new BanCommand(this.mysqlService, this.server));
        cm.register("mute", new MuteCommand(this.mysqlService, this.server));
        cm.register("unban", new UnbanCommand(this.mysqlService));
        cm.register("unmute", new UnmuteCommand(this.mysqlService));

        cm.register("auth", new AuthCommand(this));
        cm.register("register", new SimpleCommand() {
            public void execute(Invocation inv) {
                String[] args = inv.arguments();
                if (args.length < 1) {
                    inv.source().sendMessage(Component.text("Użycie: /register <hasło>", NamedTextColor.RED));
                    return;
                }
                Player player = (Player) inv.source();
                if (AuthManager.register(player, args[0])) {
                    inv.source().sendMessage(Component.text("Zarejestrowano pomyślnie!", NamedTextColor.GREEN));
                    queueManager.enqueueAfterAuth(player);
                } else {
                    inv.source().sendMessage(Component.text("Błąd rejestracji lub już jesteś premium.", NamedTextColor.RED));
                }
            }
        });

        // /login <hasło>
        cm.register("login", new SimpleCommand() {
            public void execute(Invocation inv) {
                String[] args = inv.arguments();
                if (args.length < 1) {
                    inv.source().sendMessage(Component.text("Użycie: /login <hasło>", NamedTextColor.RED));
                    return;
                }
                Player player = (Player) inv.source();
                if (AuthManager.login(player, args[0])) {
                    inv.source().sendMessage(Component.text("Zalogowano pomyślnie!", NamedTextColor.GREEN));
                    queueManager.enqueueAfterAuth(player);
                } else {
                    inv.source().sendMessage(Component.text("Błędne hasło lub przekroczono limit prób!", NamedTextColor.RED));
                }
            }
        });

        // /remember (zapamiętaj IP)
        cm.register("remember", new SimpleCommand() {
            public void execute(Invocation inv) {
                Player player = (Player) inv.source();
                if (AuthManager.rememberIP(player)) {
                    inv.source().sendMessage(Component.text("IP zapamiętane! Następnym razem nie będziesz musiał się logować.", NamedTextColor.GREEN));
                } else {
                    inv.source().sendMessage(Component.text("Błąd: musisz być zalogowany.", NamedTextColor.RED));
                }
            }
        });

        // /changepassword <stare> <nowe>
        cm.register("changepassword", new SimpleCommand() {
            public void execute(Invocation inv) {
                String[] args = inv.arguments();
                if (args.length < 2) {
                    inv.source().sendMessage(Component.text("Użycie: /changepassword <stare_hasło> <nowe_hasło>", NamedTextColor.RED));
                    return;
                }
                Player player = (Player) inv.source();
                if (AuthManager.changePassword(player, args[0], args[1])) {
                    inv.source().sendMessage(Component.text("Hasło zmienione pomyślnie!", NamedTextColor.GREEN));
                } else {
                    inv.source().sendMessage(Component.text("Błąd: złe stare hasło lub nie jesteś zalogowany.", NamedTextColor.RED));
                }
            }
        });
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        Component kickMessage = Component.text()
                .append(Component.text("Serwer jest restartowany.\n", NamedTextColor.RED))
                .append(Component.text("Wróć za chwilę!", NamedTextColor.GRAY))
                .build();
        server.getAllPlayers().forEach(player -> {
            player.disconnect(kickMessage);
        });
        if (loginTask != null) loginTask.stop();
        if (limboScoreboard != null) limboScoreboard.stop();
        if (healthChecker != null) healthChecker.stop();
        if (heartbeatService != null) heartbeatService.shutdown();
        if (discordManager != null) discordManager.shutdown();
        if (natsService != null) natsService.shutdown();
        if (redisService != null) redisService.shutdown();
        if (mysqlService != null) mysqlService.close();
        AuthManager.shutdown();
        logger.info("SectorSystem Proxy shutdown");
    }

    public static SectorProxyPlugin getInstance() { return instance; }
    public ProxyServer getServer() { return server; }
    public Logger getLogger() { return logger; }
    public RedisService getRedisService() { return redisService; }
    public NatsService getNatsService() { return natsService; }
    public MySQLService getMysql() { return mysqlService; }
    public QueueManager getQueueManager() { return queueManager; }
    public RestartOrchestrator getOrchestrator() { return orchestrator; }
    public SectorHealthChecker getHealthChecker() { return healthChecker; }
    public DiscordManager getDiscordManager() { return discordManager; }
    public DiscordDatabase getDiscordDatabase() { return discordDatabase; }
    public ProxyConfigManager getCfg() { return cfg; }
    public SystemConfig getSystemConfig() { return config; }

    public void reloadConfig() {
        ProxyConfigManager configManager = new ProxyConfigManager(
                Path.of(System.getProperty("user.dir"), "plugins", "sectorsystem-proxy"),
                logger
        );
        configManager.load();
        logger.info("Konfiguracja przeładowana!");
    }
}