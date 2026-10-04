package pl.dzoku.sectorsystem.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

/**
 * Fail-safe: gdy Redis lub NATS padnie – ostrzeżenie + opcjonalne kickowanie / shutdown.
 */
public class FailsafeManager {
    private final SectorSystemPlugin plugin;
    private int failCount = 0;
    private static final int MAX_FAILS = 3;

    public FailsafeManager(SectorSystemPlugin plugin) {
        this.plugin = plugin;

        // Sprawdzaj co 10 sekund
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::check, 20L * 10, 20L * 10);

        // Nasłuchuj FAILSAFE_SHUTDOWN z Common Service
        // UWAGA: subscribe() przyjmuje Consumer<SectorMessage>, nie Consumer<String>!
        plugin.getNatsService().subscribe("failsafe_shutdown", msg -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                // msg jest typu SectorMessage, nie String
                String reason = msg.getMessage() != null ? msg.getMessage() : "Nieznany powód";
                plugin.getLogger().severe("Otrzymano FAILSAFE_SHUTDOWN: " + reason);

                // Poprawna deserializacja kolorów legacy (§) dla Paper 1.21.x
                Component kickMsg = LegacyComponentSerializer.legacySection().deserialize(
                        "§cSektor wyłączony awaryjnie. Spróbuj za chwilę."
                );

                Bukkit.getOnlinePlayers().forEach(p -> p.kick(kickMsg));
                Bukkit.shutdown();
            });
        });
    }

    private void check() {
        boolean redisOk = false;
        boolean natsOk = false;

        try {
            redisOk = plugin.getRedisService().isHealthy();
            if (!redisOk) {
                try {
                    plugin.getRedisService().reconnect();
                    redisOk = plugin.getRedisService().isHealthy();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        try {
            natsOk = plugin.getNatsService().isHealthy();
            if (!natsOk) {
                try {
                    plugin.getNatsService().reconnect();
                    natsOk = plugin.getNatsService().isHealthy();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        if (redisOk && natsOk) {
            failCount = 0;
            return;
        }

        failCount++;
        plugin.getLogger().warning("Failsafe check failed (" + failCount + "/" + MAX_FAILS + ") Redis=" + redisOk + " NATS=" + natsOk);

        if (failCount >= MAX_FAILS) {
            plugin.getLogger().severe("FAILSAFE TRIGGERED – Redis/NATS niedostępne!");
            Bukkit.getScheduler().runTask(plugin, () -> {
                Component broadcastMsg = LegacyComponentSerializer.legacySection().deserialize(
                        "§c§l[FAILSAFE] Problemy z infrastrukturą! Za chwilę kick."
                );
                Bukkit.broadcast(broadcastMsg);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Component kickMsg = LegacyComponentSerializer.legacySection().deserialize(
                            "§cAwaria systemu sektorów. Wejdź ponownie za chwilę."
                    );
                    Bukkit.getOnlinePlayers().forEach(p -> p.kick(kickMsg));
                }, 20L * 5);
            });
        }
    }
}