package pl.dzoku.sectorsystem.listener;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import pl.sectorsystem.common.redis.RedisService;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import redis.clients.jedis.Jedis;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PendingTeleportListener implements Listener {
    private final SectorSystemPlugin plugin;
    private static final String PENDING_KEY = "sector:pending_teleport:";

    public PendingTeleportListener(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Sprawdź po 2 sekundach czy jest pending teleport
        Bukkit.getScheduler().runTaskLater(plugin, () -> tryTeleport(uuid), 40L);
    }

    private void tryTeleport(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        // Użyj CompletableFuture do async operacji na Redis
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = plugin.getRedisService().getResource()) {
                return jedis.get(PENDING_KEY + uuid);
            } catch (Exception e) {
                plugin.getLogger().warning("Redis getAsync failed: " + e.getMessage());
                return null;
            }
        });

        future.thenAccept(json -> {
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

                    // Poprawna deserializacja kolorów legacy (§)
                    Component msg = LegacyComponentSerializer.legacySection().deserialize(
                            "§aPrzeteleportowano na cel gildii."
                    );
                    p.sendMessage(msg);

                    // Usuń klucz async
                    CompletableFuture.runAsync(() -> {
                        try (Jedis jedis = plugin.getRedisService().getResource()) {
                            jedis.del(PENDING_KEY + uuid);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Redis deleteAsync failed: " + e.getMessage());
                        }
                    });
                });
            } catch (Exception e) {
                plugin.getLogger().warning("PendingTeleport parse error: " + e.getMessage());
                // Usuń klucz async
                CompletableFuture.runAsync(() -> {
                    try (Jedis jedis = plugin.getRedisService().getResource()) {
                        jedis.del(PENDING_KEY + uuid);
                    } catch (Exception ignored) {}
                });
            }
        });
    }
}