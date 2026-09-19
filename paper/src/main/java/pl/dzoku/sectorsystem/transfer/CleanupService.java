package pl.dzoku.sectorsystem.transfer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import pl.dzoku.sectorsystem.service.RedisService;

import java.util.UUID;
import java.util.logging.Logger;

public class CleanupService {
    private static final Logger logger = Logger.getLogger(CleanupService.class.getName());

    private final SectorSystemPlugin plugin;
    private final RedisService redisService;

    public CleanupService(SectorSystemPlugin plugin, RedisService redisService) {
        this.plugin = plugin;
        this.redisService = redisService;
    }

    public void cleanup(UUID uuid) {
        redisService.deletePlayerSnapshot(uuid);
        redisService.saveTransferState(uuid, pl.dzoku.sectorsystem.model.TransferState.IDLE);

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
                var data = plugin.getStateSerializer().serializeAsync(snapshot).join();
                redisService.savePlayerSnapshot(player.getUniqueId(), data.getBytes(java.nio.charset.StandardCharsets.UTF_8)).join();
            } catch (Exception e) {
                logger.severe("Failed to save player " + player.getName() + ": " + e.getMessage());
            }
        }
        logger.info("Emergency save complete");
    }
}
