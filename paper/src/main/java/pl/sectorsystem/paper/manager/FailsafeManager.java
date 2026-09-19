package pl.sectorsystem.paper.manager;

import org.bukkit.Bukkit;
import pl.sectorsystem.common.messaging.SectorMessage;
import pl.sectorsystem.paper.SectorPaperPlugin;

/**
 * Fail-safe: gdy Redis lub NATS padnie – ostrzeżenie + opcjonalne kickowanie / shutdown.
 */
public class FailsafeManager {

    private final SectorPaperPlugin plugin;
    private int failCount = 0;
    private static final int MAX_FAILS = 3;

    public FailsafeManager(SectorPaperPlugin plugin) {
        this.plugin = plugin;

        // Sprawdzaj co 10 sekund
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::check, 20L * 10, 20L * 10);

        // Nasłuchuj FAILSAFE_SHUTDOWN z Common Service
        plugin.getNats().subscribe("failsafe_shutdown", msg -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().severe("Otrzymano FAILSAFE_SHUTDOWN: " + msg.getReason());
                Bukkit.getOnlinePlayers().forEach(p ->
                        p.kick(net.kyori.adventure.text.Component.text("§cSektor wyłączony awaryjnie. Spróbuj za chwilę."))
                );
                Bukkit.shutdown();
            });
        });
    }

    private void check() {
        boolean redisOk = false;
        boolean natsOk = false;

        try {
            redisOk = plugin.getRedis().isHealthy();
            if (!redisOk) {
                try {
                    plugin.getRedis().reconnect();
                    redisOk = plugin.getRedis().isHealthy();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        try {
            natsOk = plugin.getNats().isHealthy();
            if (!natsOk) {
                try {
                    plugin.getNats().reconnect();
                    natsOk = plugin.getNats().isHealthy();
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
                Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                        "§c§l[FAILSAFE] Problemy z infrastrukturą! Za chwilę kick."
                ));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Bukkit.getOnlinePlayers().forEach(p ->
                            p.kick(net.kyori.adventure.text.Component.text("§cAwaria systemu sektorów. Wejdź ponownie za chwilę."))
                    );
                }, 20L * 5);
            });
        }
    }
}
