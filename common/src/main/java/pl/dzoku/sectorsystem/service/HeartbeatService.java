package pl.dzoku.sectorsystem.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class HeartbeatService {
    private static final Logger logger = Logger.getLogger(HeartbeatService.class.getName());
    private static final long STALE_THRESHOLD_MS = 30_000;

    private final RedisService redisService;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Long> lastHeartbeats = new ConcurrentHashMap<>();
    private final Set<String> offlineSectors = ConcurrentHashMap.newKeySet();
    private String currentSectorId;

    public HeartbeatService(RedisService redisService) {
        this.redisService = redisService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    public void startHeartbeat(String sectorId) {
        this.currentSectorId = sectorId;
        scheduler.scheduleAtFixedRate(() -> {
            try {
                redisService.setSectorHeartbeat(sectorId).join();
            } catch (Exception e) {
                logger.warning("Failed to send heartbeat: " + e.getMessage());
            }
        }, 0, 10, TimeUnit.SECONDS);
        logger.info("Heartbeat started for sector: " + sectorId);
    }

    public CompletableFuture<Boolean> isSectorOnline(String sectorId) {
        return redisService.getAsync("sector:online:" + sectorId)
                .thenApply(value -> {
                    if (value == null) {
                        if (offlineSectors.add(sectorId)) {
                            logger.warning("Sector " + sectorId + " is OFFLINE (no key in Redis)");
                        }
                        return false;
                    }

                    // Sprawdź czy wartość to "1", "true", "yes" (cokolwiek oprócz "0", "false", "no")
                    boolean online = !value.equals("0") && !value.equalsIgnoreCase("false") && !value.equalsIgnoreCase("no");

                    if (online) {
                        offlineSectors.remove(sectorId);
                    } else {
                        if (offlineSectors.add(sectorId)) {
                            logger.warning("Sector " + sectorId + " is OFFLINE");
                        }
                    }
                    return online;
                });
    }

    public Set<String> getOfflineSectors() {
        return Set.copyOf(offlineSectors);
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
