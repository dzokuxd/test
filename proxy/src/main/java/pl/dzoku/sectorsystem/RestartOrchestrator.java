package pl.dzoku.sectorsystem;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.service.RedisService;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class RestartOrchestrator {
    private static final Gson gson = new Gson();
    public static final String LIMBO = "limbo";
    private static final long BOOT_GRACE_MS = 60_000;
    private static final int MAX_ATTEMPTS = 3;
    private static final int SECONDS_PER_JOIN = 3;

    private final ProxyServer proxy;
    private final RedisService redis;
    private final Logger logger;
    private final SectorHealthChecker healthChecker;

    private final Map<String, Deque<QueuedPlayer>> queues = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<String, Long> restartEndsAt = new ConcurrentHashMap<>();
    private final Set<UUID> bypass = ConcurrentHashMap.newKeySet();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "restart-orchestrator");
        t.setDaemon(true);
        return t;
    });

    public static class QueuedPlayer {
        public final UUID uuid;
        public final String name;
        QueuedPlayer(UUID uuid, String name) { this.uuid = uuid; this.name = name; }
    }

    public RestartOrchestrator(ProxyServer proxy, RedisService redis,
                               SectorHealthChecker healthChecker, Logger logger) {
        this.proxy = proxy;
        this.redis = redis;
        this.healthChecker = healthChecker;
        this.logger = logger;
    }

    public void start() {
        sched.scheduleAtFixedRate(this::tick, 3, 3, TimeUnit.SECONDS);
    }

    public void handleRestartMessage(String json) {
        JsonObject obj = gson.fromJson(json, JsonObject.class);
        String sector = obj.get("sector").getAsString();
        long endsAt = obj.get("endsAt").getAsLong();
        restartEndsAt.put(sector, endsAt);

        long delay = Math.max(0, endsAt - System.currentTimeMillis());
        sched.schedule(() -> evacuate(sector), delay, TimeUnit.MILLISECONDS);
        logger.info("Restart " + sector + " zaplanowany; ewakuacja graczy za " + (delay / 1000) + "s");
    }

    private void evacuate(String sector) {
        proxy.getServer(sector).ifPresent(rs -> {
            Collection<Player> players = rs.getPlayersConnected();
            for (Player p : players) {
                enqueue(p, sector);
                sendToLimbo(p);
            }
            logger.info("Ewakuowano " + players.size() + " graczy z " + sector + " na limbo");
        });
    }

    public void enqueuePlayer(Player p, String sector) {
        enqueue(p, sector);
    }

    private void enqueue(Player p, String sector) {
        queues.computeIfAbsent(sector, k -> new ConcurrentLinkedDeque<>())
                .addLast(new QueuedPlayer(p.getUniqueId(), p.getUsername()));
        attempts.put(p.getUniqueId(), 0);
        redis.setWithTtlAsync("queue-sector:" + p.getUniqueId(), sector, 3600);
    }

    private void sendToLimbo(Player p) {
        proxy.getServer(LIMBO).ifPresent(limbo -> {
            bypass.add(p.getUniqueId());
            p.createConnectionRequest(limbo).connect()
                    .whenComplete((r, e) -> bypass.remove(p.getUniqueId()));
        });
    }

    private boolean isRestarting(String sector) {
        Long endsAt = restartEndsAt.get(sector);
        if (endsAt == null) return false;
        if (System.currentTimeMillis() > endsAt + BOOT_GRACE_MS
                && healthChecker.isSectorOnline(sector)) {
            restartEndsAt.remove(sector);
            redis.deleteAsync("sector-restarting:" + sector);
            return false;
        }
        return true;
    }

    @Subscribe
    public void onPreConnect(ServerPreConnectEvent event) {
        Player p = event.getPlayer();
        if (bypass.contains(p.getUniqueId())) return;
        String target = event.getOriginalServer().getServerInfo().getName();
        if (target.equals(LIMBO)) return;

        if (isRestarting(target)) {
            proxy.getServer(LIMBO).ifPresent(limbo -> {
                enqueue(p, target);
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(limbo));
                p.sendMessage(Component.text("Sektor " + target + " jest restartowany - czekasz w kolejce na limbo.", NamedTextColor.YELLOW));
            });
        }
    }

    private void tick() {
        for (Map.Entry<String, Deque<QueuedPlayer>> e : queues.entrySet()) {
            String sector = e.getKey();
            Deque<QueuedPlayer> q = e.getValue();
            if (q.isEmpty()) continue;
            if (isRestarting(sector) || !healthChecker.isSectorOnline(sector)) continue;
            if (!inFlight.add(sector)) continue;

            QueuedPlayer head = q.peek();
            Optional<Player> opt = proxy.getPlayer(head.uuid);
            if (opt.isEmpty() || !isOnLimbo(opt.get())) {
                q.poll();
                attempts.remove(head.uuid);
                redis.deleteAsync("queue-sector:" + head.uuid);
                inFlight.remove(sector);
                continue;
            }

            Player p = opt.get();
            bypass.add(head.uuid);
            proxy.getServer(sector).ifPresentOrElse(rs ->
                    p.createConnectionRequest(rs).connect().whenComplete((res, ex) -> {
                        bypass.remove(head.uuid);
                        inFlight.remove(sector);
                        if (res != null && res.isSuccessful()) {
                            q.poll();
                            attempts.remove(head.uuid);
                            redis.deleteAsync("queue-sector:" + head.uuid);
                            logger.info(head.name + " wszedl na " + sector + " z kolejki");
                        } else {
                            int att = attempts.merge(head.uuid, 1, Integer::sum);
                            if (att >= MAX_ATTEMPTS) {
                                q.poll();
                                q.addLast(head);
                                attempts.put(head.uuid, 0);
                                logger.info(head.name + ": 3 nieudane proby -> koniec kolejki " + sector);
                            }
                        }
                    }), () -> inFlight.remove(sector));
        }
        updateViews();
    }

    private boolean isOnLimbo(Player p) {
        return p.getCurrentServer()
                .map(cs -> cs.getServerInfo().getName().equals(LIMBO))
                .orElse(false);
    }

    private void updateViews() {
        for (Map.Entry<String, Deque<QueuedPlayer>> e : queues.entrySet()) {
            String sector = e.getKey();
            Deque<QueuedPlayer> q = e.getValue();
            if (q.isEmpty()) {
                redis.deleteAsync("queue-view:" + sector);
                continue;
            }
            List<String> names = new ArrayList<>();
            for (QueuedPlayer qp : q) names.add(qp.name);

            long base = 0;
            Long endsAt = restartEndsAt.get(sector);
            if (endsAt != null) {
                base = Math.max(0, (endsAt - System.currentTimeMillis()) / 1000) + 45;
            }
            int eta = (int) (base + (long) names.size() * SECONDS_PER_JOIN);

            JsonObject view = new JsonObject();
            view.add("names", gson.toJsonTree(names));
            view.addProperty("eta", eta);
            redis.setWithTtlAsync("queue-view:" + sector, gson.toJson(view), 30);
        }
    }
}
