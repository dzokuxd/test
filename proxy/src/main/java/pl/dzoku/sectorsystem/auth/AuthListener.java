package pl.dzoku.sectorsystem.auth;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.QueueManager;
import pl.dzoku.sectorsystem.RestartOrchestrator;
import pl.dzoku.sectorsystem.SectorProxyPlugin;

import java.util.Optional;

public class AuthListener {
    private final SectorProxyPlugin plugin;

    public AuthListener(SectorProxyPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        
        AuthManager.isPremiumAsync(player.getUsername()).thenAccept(isPremium -> {
            AuthManager.createSession(player, isPremium);
            AuthManager.AuthSession session = AuthManager.getSession(player.getUsername());
            
            if (!session.isLoggedIn) {
                // Sprawdź czy IP jest zapamiętane
                if (AuthManager.isIPRemembered(player)) {
                    session.isLoggedIn = true;
                    // Ustaw skin gracza
                SkinManager.applySkin(player, session.isPremium);
                plugin.getQueueManager().enqueueAfterAuth(player);
                } else {
                    // Wyślij do limbo
                    Optional<RegisteredServer> limbo = plugin.getServer().getServer(RestartOrchestrator.LIMBO);
                    limbo.ifPresent(server -> event.setInitialServer(server));
                    
                    player.sendMessage(Component.text("=== Wymagana autoryzacja ===", NamedTextColor.GOLD));
                    if (AuthManager.isRegistered(player.getUsername())) {
                        player.sendMessage(Component.text("Użyj /login <hasło>", NamedTextColor.YELLOW));
                    } else {
                        player.sendMessage(Component.text("Użyj /register <hasło>", NamedTextColor.YELLOW));
                    }
                }
            } else {
                // Premium lub zapamiętane IP - od razu do kolejki
                // Ustaw skin gracza
                SkinManager.applySkin(player, session.isPremium);
                plugin.getQueueManager().enqueueAfterAuth(player);
            }
        });
    }

    @Subscribe
    public void onPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        AuthManager.AuthSession session = AuthManager.getSession(player.getUsername());
        
        if (session == null || !session.isLoggedIn) {
            String target = event.getOriginalServer().getServerInfo().getName();
            if (!target.equals(RestartOrchestrator.LIMBO)) {
                Optional<RegisteredServer> limbo = plugin.getServer().getServer(RestartOrchestrator.LIMBO);
                limbo.ifPresent(server -> {
                    event.setResult(ServerPreConnectEvent.ServerResult.allowed(server));
                    player.sendMessage(Component.text("Musisz się zalogować lub zarejestrować!", NamedTextColor.RED));
                });
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        AuthManager.removeSession(event.getPlayer().getUsername());
    }
}
