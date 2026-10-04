package pl.dzoku.sectorsystem.restart;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import pl.sectorsystem.common.nats.NatsService;
import pl.sectorsystem.common.redis.RedisService;
import redis.clients.jedis.Jedis;

public class RestartManager {
    private static final String RESTART_AT_KEY = "restart-at:";
    private static final String SECTOR_RESTARTING_KEY = "sector-restarting:";

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

        // Użyj publishRaw() - wysyła zwykły JSON, nie wymaga SectorMessage.Type.RESTART
        String json = "{\"sector\":\"" + sector + "\",\"endsAt\":" + endsAt + "}";
        nats.publishRaw("restart", json);

        // Zapisz do Redis z TTL 15 minut
        try (Jedis jedis = redis.getResource()) {
            jedis.setex(RESTART_AT_KEY + sector, 900, String.valueOf(endsAt + 5_000));
            jedis.setex(SECTOR_RESTARTING_KEY + sector, 900, String.valueOf(endsAt));
        } catch (Exception e) {
            plugin.getLogger().warning("Redis restart save failed: " + e.getMessage());
        }
    }

    // UWAGA: użyj subscribeRaw() w SectorSystemPlugin!
    public void handleRestartMessage(String json) {
        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            String sector = obj.get("sector").getAsString();
            long endsAt = obj.get("endsAt").getAsLong();

            if (!sector.equals(plugin.getConfigManager().getCurrentSector())) return;

            startCountdown(endsAt);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid restart message: " + e.getMessage());
        }
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