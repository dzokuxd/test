package pl.dzoku.sectorsystem;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import pl.dzoku.sectorsystem.service.HeartbeatService;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class SectorHealthChecker {
    private final ProxyServer server;
    private final HeartbeatService heartbeatService;
    private final Logger logger;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Boolean> sectorStatus = new ConcurrentHashMap<>();

    public SectorHealthChecker(ProxyServer server, HeartbeatService heartbeatService, Logger logger) {
        this.server = server;
        this.heartbeatService = heartbeatService;
        this.logger = logger;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sector-health");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkAllSectors, 0, 15, TimeUnit.SECONDS);
        logger.info("Sector health checker started (interval: 15s)");
    }

    private void checkAllSectors() {
        for (RegisteredServer rs : server.getAllServers()) {
            String sectorId = rs.getServerInfo().getName();
            // NanoLimbo nie ma pluginu i nie wysyla heartbeatow - pomijamy
            if (sectorId.equals(RestartOrchestrator.LIMBO)) continue;

            heartbeatService.isSectorOnline(sectorId).thenAccept(online -> {
                boolean wasOnline = sectorStatus.getOrDefault(sectorId, true);
                sectorStatus.put(sectorId, online);

                if (wasOnline && !online) {
                    logger.severe("SECTOR " + sectorId + " WENT OFFLINE!");
                } else if (!wasOnline && online) {
                    logger.info("Sector " + sectorId + " is back online");
                }
            });
        }
    }

    public boolean isSectorOnline(String sectorId) {
        if (sectorId.equals(RestartOrchestrator.LIMBO)) return true;   // DODAC
        return sectorStatus.getOrDefault(sectorId, false);
    }

    public Set<String> getOfflineSectors() {
        return heartbeatService.getOfflineSectors();
    }

    public void stop() {
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
