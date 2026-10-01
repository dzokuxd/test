package pl.dzoku.sectorsystem.transfer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import pl.sectorsystem.common.messaging.SectorMessage;
import pl.sectorsystem.common.nats.NatsService;
import pl.sectorsystem.common.redis.RedisService;
import redis.clients.jedis.Jedis;

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
    private final Map<UUID, Long> transferCooldowns = new ConcurrentHashMap<>();

    private static final String SNAPSHOT_KEY = "sector:snapshot:";
    private static final String TRANSFER_STATE_KEY = "sector:transfer_state:";
    private static final String TRANSFER_FROM_KEY = "transfer_from:";

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
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§cJuz jestes przenoszony!"));
            return;
        }

        // Sprawdź cooldown (30 sekund)
        Long cooldownExpiry = transferCooldowns.get(uuid);
        if (cooldownExpiry != null && System.currentTimeMillis() < cooldownExpiry) {
            long remaining = (cooldownExpiry - System.currentTimeMillis()) / 1000;
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§cCooldown: " + remaining + "s"));
            return;
        }

        // Sprawdź czy sektor jest online
        boolean online = targetSector.equals("limbo") || redisService.isSectorOnline(targetSector);

        if (!online) {
            String fallback = "spawn";
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§cSektor offline -> " + fallback));
            initiateTransfer(player, fallback);
            return;
        }

        startTransfer(player, targetSector);
    }

    private void startTransfer(Player player, String targetSector) {
        UUID uuid = player.getUniqueId();
        String currentSector = configManager.getCurrentSector();
        TransferContext ctx = new TransferContext(uuid, currentSector, targetSector);
        activeTransfers.put(uuid, ctx);
        metrics.recordTransferStart(currentSector, targetSector);

        ctx.setState(TransferState.SAVING_STATE);
        player.setInvulnerable(true);
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§eZapisywanie ekwipunku..."));

        // Zrób snapshot synchronicznie
        PlayerSnapshot snapshot = stateSerializer.takeSnapshot(player);

        // Serializuj async
        stateSerializer.serializeAsync(snapshot).thenAccept(json -> {
            // Zapisz do Redis async
            CompletableFuture.runAsync(() -> {
                try (Jedis jedis = redisService.getResource()) {
                    // Zapisz snapshot z TTL 5 minut
                    jedis.setex(SNAPSHOT_KEY + uuid, 300, json);

                    // Zapisz stan transferu
                    jedis.setex(TRANSFER_STATE_KEY + uuid, 300, TransferState.DISCONNECTING.name());

                    // Ustaw sektor gracza
                    redisService.setPlayerSector(uuid, targetSector);

                    // Zapisz skąd przyszedł (TTL 5 minut)
                    jedis.setex(TRANSFER_FROM_KEY + uuid, 300, currentSector);

                    // Ustaw cooldown 30 sekund
                    transferCooldowns.put(uuid, System.currentTimeMillis() + 30000);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        ctx.setState(TransferState.TRANSFERRING);
                        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                                "§ePrzenoszenie do " + targetSector + "..."));

                        // Wyślij wiadomość przez NATS jako SectorMessage
                        SectorMessage msg = new SectorMessage(SectorMessage.Type.TRANSFER_REQUEST);
                        msg.setPlayerUuid(uuid);
                        msg.setPlayerName(player.getName());
                        msg.setFromSector(currentSector);
                        msg.setToSector(targetSector);
                        msg.setTimestamp(System.currentTimeMillis());
                        natsService.publish(msg);
                    });
                } catch (Exception ex) {
                    logger.severe("Transfer save fail: " + ex.getMessage());
                    failTransfer(ctx, "save_error");
                }
            });
        }).exceptionally(ex -> {
            logger.severe("Serialization failed: " + ex.getMessage());
            failTransfer(ctx, "serialization_error");
            return null;
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyFromRedis(player), 3L);
    }

    private void applyFromRedis(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        // Odczytaj snapshot z Redis async
        CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = redisService.getResource()) {
                return jedis.get(SNAPSHOT_KEY + uuid);
            } catch (Exception e) {
                logger.warning("Redis get snapshot failed: " + e.getMessage());
                return null;
            }
        }).thenAccept(json -> {
            if (json == null || json.isBlank()) return;

            // Deserializuj async
            stateSerializer.deserializeAsync(json).thenAccept(snap -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    // Zastosuj snapshot
                    stateSerializer.applySnapshot(player, snap);

                    // Usuń snapshot i stan transferu
                    CompletableFuture.runAsync(() -> {
                        try (Jedis jedis = redisService.getResource()) {
                            jedis.del(SNAPSHOT_KEY + uuid);
                            jedis.del(TRANSFER_STATE_KEY + uuid);
                            jedis.del(TRANSFER_FROM_KEY + uuid);
                        } catch (Exception ignored) {}
                    });

                    TransferContext ctx = activeTransfers.remove(uuid);
                    if (ctx != null) {
                        long duration = System.currentTimeMillis() - ctx.startTime;
                        metrics.recordTransferComplete(ctx.fromSector, ctx.toSector, duration);
                    }

                    player.setInvulnerable(false);
                    player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                            "§aEkwipunek i efekty przeniesione."));
                });
            }).exceptionally(ex -> {
                logger.warning("Deserialization failed: " + ex.getMessage());
                return null;
            });
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        TransferContext ctx = activeTransfers.get(uuid);
        if (ctx != null && ctx.state == TransferState.TRANSFERRING) {
            activeTransfers.remove(uuid);
            return;
        }
        cleanupService.cleanup(uuid);
        activeTransfers.remove(uuid);
    }

    // UWAGA: przyjmuje SectorMessage, nie String!
    public void handleIncomingTransfer(SectorMessage msg) {
        if (msg.getType() != SectorMessage.Type.TRANSFER_REQUEST) return;
        logger.info("Incoming transfer: " + msg.getPlayerName()
                + " from " + msg.getFromSector() + " to " + msg.getToSector());
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
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    "§cTransfer nie udal sie: " + reason));
        }

        cleanupService.cleanup(ctx.uuid);
        activeTransfers.remove(ctx.uuid);

        // Zapisz stan FAILED
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = redisService.getResource()) {
                jedis.setex(TRANSFER_STATE_KEY + ctx.uuid, 300, TransferState.FAILED.name());
            } catch (Exception ignored) {}
        });
    }

    public boolean isInTransfer(UUID uuid) {
        return activeTransfers.containsKey(uuid);
    }

    public TransferState getTransferState(UUID uuid) {
        TransferContext ctx = activeTransfers.get(uuid);
        return ctx != null ? ctx.state : TransferState.IDLE;
    }

    public void shutdown() {
        timeoutScheduler.shutdown();
        try {
            if (!timeoutScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                timeoutScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}