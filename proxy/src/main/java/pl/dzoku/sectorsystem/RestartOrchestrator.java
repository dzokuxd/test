package pl.dzoku.sectorsystem;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import pl.dzoku.sectorsystem.service.RedisService;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class RestartOrchestrator {
    private static final Gson gson = new Gson();
    public static final String LIMBO = "limbo";
    private static final long BOOT_GRACE_MS = 60_000;
    private static final int MAX_ATTEMPTS = 3;
    private static final int SECONDS_PER_JOIN = 3;
    private static final long INFLIGHT_TIMEOUT_MS = 20_000;
    private static final Title.Times TITLE_TIMES = Title.Times.times(
            Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(300));

    private final ProxyServer proxy;
    private final RedisService redis;
    private final Logger logger;
    private final SectorHealthChecker healthChecker;

    private final Map<String, Deque<QueuedPlayer>> queues = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<String, Long> restartEndsAt = new ConcurrentHashMap<>();
    private final Set<UUID> bypass = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> inFlight = new ConcurrentHashMap<>();   // sektor -> timestamp startu proby
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

    // ── Gettery dla LimboScoreboard (title) ───────────────────────────────
    public int getAttempts(UUID uuid) { return attempts.getOrDefault(uuid, 0); }

    public boolean isConnecting(UUID uuid) {
        for (Map.Entry<String, Deque<QueuedPlayer>> e : queues.entrySet()) {
            QueuedPlayer head = e.getValue().peek();
            if (head != null && head.uuid.equals(uuid) && inFlight.containsKey(e.getKey())) return true;
        }
        return false;
    }

    public boolean isInQueue(UUID uuid) {
        for (Deque<QueuedPlayer> q : queues.values()) {
            for (QueuedPlayer qp : q) {
                if (qp.uuid.equals(uuid)) return true;
            }
        }
        return false;
    }

    public boolean isSectorOnline(String sector) { return healthChecker.isSectorOnline(sector); }
    public boolean isSectorRestarting(String sector) { return restartEndsAt.containsKey(sector); }
    // ──────────────────────────────────────────────────────────────────────

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
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Deque<QueuedPlayer>> e : queues.entrySet()) {
            String sector = e.getKey();
            Deque<QueuedPlayer> q = e.getValue();
            if (q.isEmpty()) continue;

            // Sektor offline / restartuje -> czekamy, proby tylko gdy zyje
            if (isRestarting(sector) || !healthChecker.isSectorOnline(sector)) continue;

            // Watchdog: jesli proba wisi >20s, odblokuj sektor
            Long since = inFlight.get(sector);
            if (since != null) {
                if (now - since < INFLIGHT_TIMEOUT_MS) continue;
                logger.warning("Kolejka " + sector + ": proba wisiala >20s - odblokowuje");
                inFlight.remove(sector);
            }
            inFlight.put(sector, now);

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
            int attemptNo = Math.min(3, attempts.getOrDefault(head.uuid, 0) + 1);
            p.showTitle(Title.title(
                    Component.text("Jesteś w kolejce", NamedTextColor.GOLD),
                    Component.text("Łączenie z serwerem... ", NamedTextColor.GREEN)
                            .append(Component.text("(próba " + attemptNo + "/3)", NamedTextColor.YELLOW)),
                    TITLE_TIMES));

            try {
                bypass.add(head.uuid);
                proxy.getServer(sector).ifPresentOrElse(rs ->
                        p.createConnectionRequest(rs).connect().whenComplete((res, ex) -> {
                            bypass.remove(head.uuid);
                            inFlight.remove(sector);
                            if (res != null && res.isSuccessful()) {
                                q.poll();
                                attempts.remove(head.uuid);
                                redis.deleteAsync("queue-sector:" + head.uuid);
                                p.clearTitle();
                                logger.info(head.name + " wszedl na " + sector + " z kolejki");
                            } else {
                                int att = attempts.merge(head.uuid, 1, Integer::sum);
                                if (att >= MAX_ATTEMPTS) {
                                    q.poll();
                                    q.addLast(head);
                                    attempts.put(head.uuid, 0);
                                    logger.info(head.name + ": 3 nieudane proby -> koniec kolejki " + sector);
                                    p.sendMessage(Component.text("§c3 nieudane próby połączenia! §7Przeniesiono Cię na koniec kolejki §e" + sector + "§7.", NamedTextColor.RED));
                                    p.showTitle(Title.title(
                                            Component.text("Jesteś w kolejce", NamedTextColor.GOLD),
                                            Component.text("3 nieudane próby — koniec kolejki!", NamedTextColor.RED),
                                            TITLE_TIMES));
                                } else {
                                    p.sendMessage(Component.text("§cNie udało się połączyć §7(próba §e" + att + "/3§7)§c, ponawiam...", NamedTextColor.RED));
                                }
                            }
                        }), () -> inFlight.remove(sector));
            } catch (Exception ex2) {
                bypass.remove(head.uuid);
                inFlight.remove(sector);
                logger.warning("Kolejka " + sector + ": blad connect(): " + ex2.getMessage());
            }
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