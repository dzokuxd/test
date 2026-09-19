package pl.dzoku.sectorsystem.restart;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import pl.dzoku.sectorsystem.service.NatsService;
import pl.dzoku.sectorsystem.service.RedisService;

public class RestartManager {
    private static final Gson gson = new Gson();

    private final SectorSystemPlugin plugin;
    private final RedisService redis;
    private final NatsService nats;
    private BukkitTask countdownTask;

    public RestartManager(SectorSystemPlugin plugin, RedisService redis, NatsService nats) {
        this.plugin = plugin;
        this.redis = redis;
        this.nats = nats;
    }

    public void requestRestart(String sector, int seconds) {
        long endsAt = System.currentTimeMillis() + seconds * 1000L;

        JsonObject msg = new JsonObject();
        msg.addProperty("sector", sector);
        msg.addProperty("endsAt", endsAt);
        nats.publish("sector.restart", gson.toJson(msg));

        redis.setWithTtlAsync("restart-at:" + sector, String.valueOf(endsAt + 5_000), 900);
        redis.setWithTtlAsync("sector-restarting:" + sector, String.valueOf(endsAt), 900);
    }

    public void handleRestartMessage(String json) {
        JsonObject obj = gson.fromJson(json, JsonObject.class);
        String sector = obj.get("sector").getAsString();
        long endsAt = obj.get("endsAt").getAsLong();

        if (!sector.equals(plugin.getConfigManager().getCurrentSector())) return;
        startCountdown(endsAt);
    }

    private void startCountdown(long endsAt) {
        if (countdownTask != null) countdownTask.cancel();
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long left = (endsAt - System.currentTimeMillis()) / 1000L;
            if (left <= 0) {
                countdownTask.cancel();
                Bukkit.getOnlinePlayers().forEach(p ->
                        p.sendActionBar(Component.text("RESTART SERWERA!", NamedTextColor.RED)));
                return;
            }
            Component bar = Component.text("Restart serwera za ", NamedTextColor.YELLOW)
                    .append(Component.text(left + "s", NamedTextColor.RED));
            Bukkit.getOnlinePlayers().forEach(p -> p.sendActionBar(bar));
        }, 0L, 20L);
    }
}
