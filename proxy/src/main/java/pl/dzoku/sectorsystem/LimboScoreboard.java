package pl.dzoku.sectorsystem;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.service.RedisService;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Widok kolejki dla graczy stojacych na limbo (NanoLimbo).
 * Renderowany przez PROXY jako BossBar - dziala na kazdej wersji Adventure.
 */
public class LimboScoreboard {
    private static final String LIMBO = RestartOrchestrator.LIMBO;

    private final ProxyServer proxy;
    private final RedisService redis;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "limbo-scoreboard");
        t.setDaemon(true);
        return t;
    });

    public LimboScoreboard(ProxyServer proxy, RedisService redis) {
        this.proxy = proxy;
        this.redis = redis;
    }

    public void start() {
        sched.scheduleAtFixedRate(this::tick, 2, 2, TimeUnit.SECONDS);
    }

    private void tick() {
        for (Player player : proxy.getAllPlayers()) {
            UUID uuid = player.getUniqueId();
            try {
                if (!isOnLimbo(player)) {
                    removeBar(player);
                    continue;
                }

                String sector = redis.getAsync("queue-sector:" + uuid).join();
                if (sector == null) {
                    removeBar(player);
                    continue;
                }

                String viewJson = redis.getAsync("queue-view:" + sector).join();
                if (viewJson == null) continue;
                JsonObject view = JsonParser.parseString(viewJson).getAsJsonObject();
                JsonArray names = view.getAsJsonArray("names");
                int eta = view.get("eta").getAsInt();
                boolean restarting = redis.getAsync("sector-restarting:" + sector).join() != null;

                int myPos = 0;
                StringBuilder list = new StringBuilder();
                for (int i = 0; i < names.size() && i < 6; i++) {
                    String n = names.get(i).getAsString();
                    if (n.equals(player.getUsername())) myPos = i + 1;
                    if (i > 0) list.append(", ");
                    list.append(i + 1).append(".").append(n);
                }
                if (names.size() > 6) list.append(" ...");

                Component title = Component.text("Kolejka " + sector + ": ", NamedTextColor.GOLD)
                        .append(Component.text(list.toString(), NamedTextColor.WHITE))
                        .append(Component.text(" | Pozycja: " + (myPos > 0 ? myPos : "?") + "/" + names.size(), NamedTextColor.AQUA))
                        .append(Component.text(restarting ? " | Restart ~" + eta + "s" : " | Wejscie ~" + eta + "s", NamedTextColor.YELLOW));

                float progress = 1.0f;
                if (myPos > 0 && names.size() > 0) {
                    progress = (float) (names.size() - myPos + 1) / names.size();
                }

                BossBar bar = bars.computeIfAbsent(uuid, k -> {
                    BossBar b = BossBar.bossBar(Component.text("Kolejka"), 1.0f,
                            BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
                    player.showBossBar(b);
                    return b;
                });
                bar.name(title);
                bar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
                bar.color(restarting ? BossBar.Color.RED : BossBar.Color.GREEN);
            } catch (Exception ignored) {
                // Redis chwilowo niedostepny - pomijamy tyk
            }
        }

        // Sprzatanie barow po graczach, ktorzy wyszli z serwera
        for (UUID uuid : bars.keySet()) {
            Optional<Player> opt = proxy.getPlayer(uuid);
            if (opt.isEmpty() || !isOnLimbo(opt.get())) {
                BossBar bar = bars.remove(uuid);
                if (bar != null) opt.ifPresent(p -> p.hideBossBar(bar));
            }
        }
    }

    private boolean isOnLimbo(Player p) {
        return p.getCurrentServer()
                .map(cs -> cs.getServerInfo().getName().equals(LIMBO))
                .orElse(false);
    }

    private void removeBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    public void stop() {
        sched.shutdown();
    }
}