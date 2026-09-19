package pl.dzoku.sectorsystem.auth;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.SectorProxyPlugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LoginTask {
    private final SectorProxyPlugin plugin;
    private final ScheduledExecutorService scheduler;
    private final int timeoutSeconds;

    public LoginTask(SectorProxyPlugin plugin, int timeoutSeconds) {
        this.plugin = plugin;
        this.timeoutSeconds = timeoutSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::check, 10, 10, TimeUnit.SECONDS);
    }

    private void check() {
        for (Player player : plugin.getServer().getAllPlayers()) {
            AuthManager.AuthSession session = AuthManager.getSession(player.getUsername());
            if (session != null && !session.isLoggedIn) {
                long elapsed = (System.currentTimeMillis() - session.loginTime) / 1000;
                if (elapsed >= timeoutSeconds) {
                    player.sendMessage(Component.text("Przekroczono czas na autoryzację. Rozłączanie...", NamedTextColor.RED));
                    player.disconnect(Component.text("Nie zalogowałeś się w ciągu " + timeoutSeconds + " sekund."));
                }
            }
        }
    }

    public void stop() {
        scheduler.shutdown();
    }
}
