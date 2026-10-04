package pl.dzoku.sectorsystem;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import pl.dzoku.sectorsystem.service.RedisService;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Title kolejki na limbo:
 * Linia 1: "Jestes w kolejce (sektor)"
 * Linia 2: laczenie (proba X/3) / sektor offline / restart / pozycja+ETA
 */
public class LimboScoreboard {
    private static final String LIMBO = RestartOrchestrator.LIMBO;
    private static final Title.Times TIMES = Title.Times.times(
            Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(300));

    private final ProxyServer proxy;
    private final RedisService redis;
    private final RestartOrchestrator orchestrator;
    private final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "limbo-queue-title");
        t.setDaemon(true);
        return t;
    });

    public LimboScoreboard(ProxyServer proxy, RedisService redis, RestartOrchestrator orchestrator) {
        this.proxy = proxy;
        this.redis = redis;
        this.orchestrator = orchestrator;
    }

    public void start() {
        sched.scheduleAtFixedRate(this::tick, 1, 2, TimeUnit.SECONDS);
    }

    private void tick() {
        for (Player player : proxy.getAllPlayers()) {
            try {
                if (!isOnLimbo(player)) continue;

                UUID uuid = player.getUniqueId();
                String sector = redis.getAsync("queue-sector:" + uuid).join();
                if (sector == null) continue;

                String viewJson = redis.getAsync("queue-view:" + sector).join();
                if (viewJson == null) continue;
                JsonObject view = JsonParser.parseString(viewJson).getAsJsonObject();
                JsonArray names = view.getAsJsonArray("names");
                int eta = view.get("eta").getAsInt();

                int myPos = 0;
                for (int i = 0; i < names.size(); i++) {
                    if (names.get(i).getAsString().equals(player.getUsername())) { myPos = i + 1; break; }
                }

                Component title = Component.text("Jesteś w kolejce", NamedTextColor.GOLD)
                        .append(Component.text(" §7(§e" + sector + "§7)", NamedTextColor.GRAY));

                Component subtitle;
                if (orchestrator.isConnecting(uuid)) {
                    int attempt = Math.min(3, orchestrator.getAttempts(uuid) + 1);
                    subtitle = Component.text("Łączenie z serwerem... ", NamedTextColor.GREEN)
                            .append(Component.text("(próba " + attempt + "/3)", NamedTextColor.YELLOW));
                } else if (!orchestrator.isSectorOnline(sector)) {
                    subtitle = Component.text("Sektor offline — czekam na start...", NamedTextColor.RED);
                } else if (orchestrator.isSectorRestarting(sector)) {
                    subtitle = Component.text("Restart sektora — wejście ~" + eta + "s", NamedTextColor.YELLOW);
                } else {
                    subtitle = Component.text("Pozycja: ", NamedTextColor.AQUA)
                            .append(Component.text("#" + (myPos > 0 ? myPos : "?") + "/" + names.size(), NamedTextColor.WHITE))
                            .append(Component.text(" | Wejście ~" + eta + "s", NamedTextColor.YELLOW));
                }

                player.showTitle(Title.title(title, subtitle, TIMES));
            } catch (Exception ignored) {
                // Redis chwilowo niedostepny - pomijamy tyk
            }
        }
    }

    private boolean isOnLimbo(Player p) {
        return p.getCurrentServer()
                .map(cs -> cs.getServerInfo().getName().equals(LIMBO))
                .orElse(false);
    }

    public void stop() {
        sched.shutdown();
    }
}