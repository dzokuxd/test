package pl.dzoku.sectorsystem.transfer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import pl.sectorsystem.common.redis.RedisService;
import redis.clients.jedis.Jedis;

import java.util.UUID;
import java.util.logging.Logger;

public class CleanupService {
    private static final Logger logger = Logger.getLogger(CleanupService.class.getName());
    private final SectorSystemPlugin plugin;
    private final RedisService redisService;

    private static final String SNAPSHOT_KEY = "sector:snapshot:";
    private static final String TRANSFER_STATE_KEY = "sector:transfer_state:";

    public CleanupService(SectorSystemPlugin plugin, RedisService redisService) {
        this.plugin = plugin;
        this.redisService = redisService;
    }

    public void cleanup(UUID uuid) {
        // Usuń snapshot i stan transferu z Redis
        try (Jedis jedis = redisService.getResource()) {
            jedis.del(SNAPSHOT_KEY + uuid);
            jedis.del(TRANSFER_STATE_KEY + uuid);
        } catch (Exception e) {
            logger.warning("Redis cleanup failed: " + e.getMessage());
        }

        // Usuń dropped items w pobliżu gracza
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.getWorld().getEntitiesByClass(Item.class).stream()
                        .filter(item -> item.getLocation().distanceSquared(player.getLocation()) < 25)
                        .forEach(Item::remove);
            }
        });
        logger.fine("Cleanup complete for player " + uuid);
    }

    public void emergencySave() {
        logger.info("Emergency save - saving all online players...");
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                var snapshot = plugin.getStateSerializer().takeSnapshot(player);
                var json = plugin.getStateSerializer().serializeAsync(snapshot).join();

                // Zapisz do Redis
                try (Jedis jedis = redisService.getResource()) {
                    jedis.setex(SNAPSHOT_KEY + player.getUniqueId(), 300, json);
                }
            } catch (Exception e) {
                logger.severe("Failed to save player " + player.getName() + ": " + e.getMessage());
            }
        }
        logger.info("Emergency save complete");
    }
}