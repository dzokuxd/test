package pl.dzoku.sectorsystem.transfer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import pl.dzoku.sectorsystem.config.ConfigManager;
import pl.dzoku.sectorsystem.metrics.TransferMetrics;
import pl.dzoku.sectorsystem.model.PlayerSnapshot;
import pl.dzoku.sectorsystem.model.TransferState;
import pl.dzoku.sectorsystem.service.NatsService;
import pl.dzoku.sectorsystem.service.RedisService;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class TransferStateMachine implements Listener {
    private static final Logger logger = Logger.getLogger(TransferStateMachine.class.getName());
    private static final Gson gson = new Gson();

    private final SectorSystemPlugin plugin;
    private final RedisService redisService;
    private final NatsService natsService;
    private final PlayerStateSerializer stateSerializer;
    private final CleanupService cleanupService;
    private final TransferMetrics metrics;
    private final ConfigManager configManager;

    private final Map<UUID, TransferContext> activeTransfers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "transfer-timeout");
        t.setDaemon(true);
        return t;
    });

    public TransferStateMachine(SectorSystemPlugin plugin, RedisService redisService,
                                NatsService natsService, PlayerStateSerializer stateSerializer,
                                CleanupService cleanupService, TransferMetrics metrics,
                                ConfigManager configManager) {
        this.plugin = plugin;
        this.redisService = redisService;
        this.natsService = natsService;
        this.stateSerializer = stateSerializer;
        this.cleanupService = cleanupService;
        this.metrics = metrics;
        this.configManager = configManager;
        timeoutScheduler.scheduleAtFixedRate(this::checkTimeouts, 2, 2, TimeUnit.SECONDS);
    }

    private static class TransferContext {
        final UUID uuid;
        final String fromSector;
        final String toSector;
        volatile TransferState state;
        volatile long stateTimestamp;
        final long startTime;

        TransferContext(UUID uuid, String fromSector, String toSector) {
            this.uuid = uuid;
            this.fromSector = fromSector;
            this.toSector = toSector;
            this.state = TransferState.IDLE;
            this.stateTimestamp = System.currentTimeMillis();
            this.startTime = System.currentTimeMillis();
        }

        void setState(TransferState newState) {
            this.state = newState;
            this.stateTimestamp = System.currentTimeMillis();
        }
    }

    public void initiateTransfer(Player player, String targetSector) {
        UUID uuid = player.getUniqueId();
        if (activeTransfers.containsKey(uuid)) {
            player.sendActionBar("§cJuz jestes przenoszony!");
            return;
        }
        redisService.getTransferCooldown(uuid, "transfer").thenAccept(cooldownExpiry -> {
            if (System.currentTimeMillis() < cooldownExpiry) {
                player.sendActionBar("§cCooldown: " + ((cooldownExpiry - System.currentTimeMillis()) / 1000) + "s");
                return;
            }
            CompletableFuture<Boolean> onlineFut = targetSector.equals("limbo")
                    ? CompletableFuture.completedFuture(true)
                    : plugin.getHeartbeatService().isSectorOnline(targetSector);
            onlineFut.thenAccept(online -> {
                if (!online) {
                    var sc = configManager.getSectorConfig(targetSector);
                    String fallback = sc != null ? sc.getFallbackSector() : "spawn";
                    player.sendActionBar("§cSektor offline -> " + fallback);
                    initiateTransfer(player, fallback);
                    return;
                }
                startTransfer(player, targetSector);
            });
        });
    }

    private void startTransfer(Player player, String targetSector) {
        UUID uuid = player.getUniqueId();
        String currentSector = configManager.getCurrentSector();

        TransferContext ctx = new TransferContext(uuid, currentSector, targetSector);
        activeTransfers.put(uuid, ctx);
        metrics.recordTransferStart(currentSector, targetSector);

        ctx.setState(TransferState.SAVING_STATE);
        player.setInvulnerable(true);
        player.sendActionBar("§eZapisywanie ekwipunku...");

        PlayerSnapshot snapshot = stateSerializer.takeSnapshot(player);
        stateSerializer.serializeAsync(snapshot).thenAccept(json -> {
            // RedisService oczekuje byte[] - konwertujemy JSON String -> byte[] (UTF-8)
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            redisService.savePlayerSnapshot(uuid, data)
                    .thenCompose(v -> redisService.saveTransferState(uuid, TransferState.DISCONNECTING))
                    .thenCompose(v -> redisService.setPlayerSector(uuid, targetSector))
                    .thenCompose(v -> redisService.setWithTtlAsync("transfer_from:" + uuid, currentSector, 300))
                    .thenRun(() -> {
                        ctx.setState(TransferState.TRANSFERRING);
                        player.sendActionBar("§ePrzenoszenie do " + targetSector + "...");

                        JsonObject msg = new JsonObject();
                        msg.addProperty("uuid", uuid.toString());
                        msg.addProperty("name", player.getName());
                        msg.addProperty("from", currentSector);
                        msg.addProperty("to", targetSector);
                        msg.addProperty("timestamp", System.currentTimeMillis());
                        natsService.publish("sector.transfer.request", gson.toJson(msg));
                    }).exceptionally(ex -> {
                        logger.severe("Transfer save fail: " + ex.getMessage());
                        failTransfer(ctx, "save_error");
                        return null;
                    });
        });
    }

    // ── JOIN: aplikuj snapshot 3 ticki PO pelnym zaladowaniu ───────────────
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyFromRedis(player), 3L);
    }

    private void applyFromRedis(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        redisService.getPlayerSnapshot(uuid).thenAccept(bytes -> {
            // RedisService zwraca byte[] - konwertujemy -> String (UTF-8)
            if (bytes == null || bytes.length == 0) return; // nie ma transferu w toku
            String json = new String(bytes, StandardCharsets.UTF_8);
            stateSerializer.deserializeAsync(json).thenAccept(snap -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    stateSerializer.applySnapshot(player, snap);
                    redisService.deletePlayerSnapshot(uuid);
                    redisService.deleteAsync("transfer_from:" + uuid);
                    redisService.saveTransferState(uuid, TransferState.COMPLETE);
                    TransferContext ctx = activeTransfers.remove(uuid);
                    if (ctx != null) {
                        long duration = System.currentTimeMillis() - ctx.startTime;
                        metrics.recordTransferComplete(ctx.fromSector, ctx.toSector, duration);
                    }
                    player.setInvulnerable(false);
                    player.sendActionBar("§aEkwipunek i efekty przeniesione.");
                });
            });
        });
    }

    // ── QUIT: nie kasuj snapshotu jesli transfer w toku ────────────────────
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        TransferContext ctx = activeTransfers.get(uuid);
        if (ctx != null && ctx.state == TransferState.TRANSFERRING) {
            activeTransfers.remove(uuid);
            return; // snapshot zostaje w Redisie - docelowy serwer go zastosuje
        }
        cleanupService.cleanup(uuid);
        activeTransfers.remove(uuid);
    }

    public void handleIncomingTransfer(String message) {
        JsonObject json = gson.fromJson(message, JsonObject.class);
        logger.info("Incoming transfer: " + json.get("name").getAsString()
                + " from " + json.get("from").getAsString());
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        activeTransfers.forEach((uuid, ctx) -> {
            if (now - ctx.stateTimestamp > ctx.state.getMaxDurationMs()) {
                logger.warning("Transfer timeout " + uuid + " in " + ctx.state);
                failTransfer(ctx, "timeout");
            }
        });
    }

    private void failTransfer(TransferContext ctx, String reason) {
        logger.warning("Transfer FAILED " + ctx.uuid + ": " + ctx.fromSector + " -> " + ctx.toSector + " (" + reason + ")");
        metrics.recordTransferFailure(ctx.fromSector, ctx.toSector, reason);
        Player player = Bukkit.getPlayer(ctx.uuid);
        if (player != null && player.isOnline()) {
            player.setInvulnerable(false);
            player.sendActionBar("§cTransfer nie udal sie: " + reason);
        }
        cleanupService.cleanup(ctx.uuid);
        activeTransfers.remove(ctx.uuid);
        redisService.saveTransferState((UUID) uuidSafe(ctx.uuid), TransferState.FAILED);
    }

    private Object uuidSafe(UUID u) { return u; }

    public boolean isInTransfer(UUID uuid) { return activeTransfers.containsKey(uuid); }

    public TransferState getTransferState(UUID uuid) {
        TransferContext ctx = activeTransfers.get(uuid);
        return ctx != null ? ctx.state : TransferState.IDLE;
    }

    public void shutdown() {
        timeoutScheduler.shutdown();
        try {
            if (!timeoutScheduler.awaitTermination(3, TimeUnit.SECONDS)) timeoutScheduler.shutdownNow();
        } catch (InterruptedException e) {
            timeoutScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
