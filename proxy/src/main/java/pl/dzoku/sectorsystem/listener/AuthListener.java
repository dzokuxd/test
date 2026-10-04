package pl.dzoku.sectorsystem.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.RestartOrchestrator;
import pl.dzoku.sectorsystem.SectorProxyPlugin;
import pl.dzoku.sectorsystem.config.AuthManager;
import pl.dzoku.sectorsystem.config.SkinManager;
import pl.sectorsystem.common.mysql.MySQLService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AuthListener {
    private final SectorProxyPlugin plugin;

    public AuthListener(SectorProxyPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();

        // Sprawdź whitelistę PRZED wszystkim
        if (!checkWhitelist(player)) {
            return;
        }

        AuthManager.isPremiumAsync(player.getUsername()).thenAccept(isPremium -> {
            AuthManager.createSession(player, isPremium);
            AuthManager.ensurePlayerRow(player, isPremium);
            AuthManager.AuthSession session = AuthManager.getSession(player.getUsername());

            if (!session.isLoggedIn) {
                if (AuthManager.isIPRemembered(player)) {
                    session.isLoggedIn = true;
                    sendToQueueAfterSkin(player, session.isPremium);
                } else {
                    sendToLimbo(event, player);
                }
            } else {
                sendToQueueAfterSkin(player, session.isPremium);
            }
        });
    }

    private boolean checkWhitelist(Player player) {
        MySQLService mysql = plugin.getMysql();
        if (mysql == null || !mysql.isEnabled() || !mysql.isWhitelistEnabled()) {
            return true;
        }

        // Premium omijają whitelist
        if (player.isOnlineMode()) {
            return true;
        }

        try (Connection c = mysql.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM auth_whitelist WHERE username=?")) {
            ps.setString(1, player.getUsername());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd sprawdzania whitelisty: " + e.getMessage());
            return true;
        }

        String reason = mysql.getWhitelistReason();
        if (reason == null || reason.isBlank()) {
            reason = "Serwer jest obecnie na whitelist.\nSkontaktuj się z administracją.";
        }
        player.disconnect(Component.text("§c§l[WHITELIST]\n\n§7" + reason));
        return false;
    }

    private void sendToLimbo(PlayerChooseInitialServerEvent event, Player player) {
        Optional<RegisteredServer> limbo = plugin.getServer().getServer(RestartOrchestrator.LIMBO);
        limbo.ifPresent(server -> event.setInitialServer(server));

        player.sendMessage(Component.text("=== Wymagana autoryzacja ===", NamedTextColor.GOLD));
        if (AuthManager.isRegistered(player.getUsername())) {
            player.sendMessage(Component.text("Użyj /login <hasło>", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Użyj /register <hasło>", NamedTextColor.YELLOW));
        }

        MySQLService mysql = plugin.getMysql();
        if (mysql != null && mysql.isEnabled()) {
            String motd = mysql.getMotd();
            if (motd != null && !motd.isBlank()) {
                player.sendMessage(Component.text(""));
                player.sendMessage(Component.text("§7§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GRAY));
                player.sendMessage(Component.text(motd, NamedTextColor.YELLOW));
                player.sendMessage(Component.text("§7§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GRAY));
            }
        }
    }

    private void sendToQueueAfterSkin(Player player, boolean isPremium) {
        CompletableFuture<Void> skinFuture = CompletableFuture.runAsync(() -> {
            SkinManager.applySkin(player, isPremium);
        });

        skinFuture.thenRun(() -> {
            CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(() -> {
                plugin.getQueueManager().enqueueAfterAuth(player);
            });
        });
    }
}