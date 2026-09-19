package pl.dzoku.sectorsystem.listener;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

import java.util.UUID;

/**
 * Domyka transfer z pluginu Gildie: po wejsciu gracza na sektor docelowy
 * teleportuje go na zapisany cel (/g dom, /g bw) i kasuje klucz z Redis.
 */
public class PendingTeleportListener implements Listener {
    private static final String PENDING_KEY = "gildie-pending-tp:";

    private final SectorSystemPlugin plugin;

    public PendingTeleportListener(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // 40 tickow zapasu, zeby snapshot transferu zdazyl sie odtworzyc
        Bukkit.getScheduler().runTaskLater(plugin, () -> tryTeleport(uuid), 40L);
    }

    private void tryTeleport(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        plugin.getRedisService().getAsync(PENDING_KEY + uuid).thenAccept(json -> {
            if (json == null || json.isBlank()) return;
            try {
                JsonObject loc = JsonParser.parseString(json).getAsJsonObject();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) return;
                    World world = Bukkit.getWorld(loc.get("world").getAsString());
                    if (world == null) return;
                    p.teleport(new Location(world,
                            loc.get("x").getAsDouble(),
                            loc.get("y").getAsDouble(),
                            loc.get("z").getAsDouble(),
                            loc.get("yaw").getAsFloat(),
                            loc.get("pitch").getAsFloat()));
                    p.sendMessage("§aPrzeteleportowano na cel gildii.");
                    plugin.getRedisService().deleteAsync(PENDING_KEY + uuid);
                });
            } catch (Exception e) {
                plugin.getLogger().warning("PendingTeleport parse error: " + e.getMessage());
                plugin.getRedisService().deleteAsync(PENDING_KEY + uuid);
            }
        });
    }
}
