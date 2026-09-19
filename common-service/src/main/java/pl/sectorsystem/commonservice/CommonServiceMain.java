package pl.sectorsystem.commonservice;

import pl.sectorsystem.common.config.SystemConfig;
import pl.sectorsystem.common.messaging.SectorMessage;
import pl.sectorsystem.common.nats.NatsService;
import pl.sectorsystem.common.redis.RedisService;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommonServiceMain {

    private static final long SECTOR_TIMEOUT_MS = 45_000;

    private final RedisService redis;
    private final NatsService nats;
    private final Map<String, Long> lastHeartbeat = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public CommonServiceMain(SystemConfig config) throws Exception {
        this.redis = new RedisService(config.getRedis());
        this.nats = new NatsService(config.getNats());
    }

    public void start() {
        System.out.println("=== SectorSystem Common Service (Brain) ===");

        nats.subscribe("heartbeat", msg -> {
            if (msg.getFromSector() != null) {
                lastHeartbeat.put(msg.getFromSector(), System.currentTimeMillis());
                redis.setSectorOnline(msg.getFromSector(), true);
            }
        });

        scheduler.scheduleAtFixedRate(this::healthCheck, 5, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::printStatus, 10, 15, TimeUnit.SECONDS);

        System.out.println("Common Service uruchomiony. Ctrl+C aby zatrzymać.");
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        try {
            while (running.get()) Thread.sleep(1000);
        } catch (InterruptedException ignored) {}
    }

    private void healthCheck() {
        boolean redisOk = false, natsOk = false;
        try {
            redisOk = redis.isHealthy();
            if (!redisOk) { redis.reconnect(); redisOk = redis.isHealthy(); }
        } catch (Exception e) {
            System.err.println("[HEALTH] Redis DOWN: " + e.getMessage());
        }
        try {
            natsOk = nats.isHealthy();
            if (!natsOk) { nats.reconnect(); natsOk = nats.isHealthy(); }
        } catch (Exception e) {
            System.err.println("[HEALTH] NATS DOWN: " + e.getMessage());
        }

        long now = System.currentTimeMillis();
        lastHeartbeat.forEach((sector, ts) -> {
            if (now - ts > SECTOR_TIMEOUT_MS) {
                redis.setSectorOnline(sector, false);
                System.out.println("[SECTOR] " + sector + " OFFLINE (brak heartbeat)");
            }
        });

        if (!redisOk || !natsOk) {
            System.err.println("[FAILSAFE] Infrastruktura DOWN! Wysyłam FAILSAFE_SHUTDOWN...");
            SectorMessage msg = new SectorMessage(SectorMessage.Type.FAILSAFE_SHUTDOWN);
            msg.setReason("Redis=" + redisOk + " NATS=" + natsOk);
            try { nats.publish(msg); } catch (Exception e) {
                System.err.println("Nie wysłano FAILSAFE: " + e.getMessage());
            }
        }
    }

    private void printStatus() {
        System.out.println("--- Status ---");
        System.out.println("Redis: " + (redis.isHealthy() ? "OK" : "DOWN"));
        System.out.println("NATS : " + (nats.isHealthy() ? "OK" : "DOWN"));
        lastHeartbeat.forEach((s, t) -> {
            long ago = (System.currentTimeMillis() - t) / 1000;
            System.out.println("Sektor " + s + ": heartbeat " + ago + "s temu");
        });
    }

    private void shutdown() {
        running.set(false);
        scheduler.shutdownNow();
        try { redis.close(); } catch (Exception ignored) {}
        try { nats.close(); } catch (Exception ignored) {}
        System.out.println("Common Service zatrzymany.");
    }

    public static void main(String[] args) throws Exception {
        new CommonServiceMain(new SystemConfig()).start();
    }
}
