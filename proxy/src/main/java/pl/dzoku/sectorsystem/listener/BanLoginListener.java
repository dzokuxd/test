package pl.dzoku.sectorsystem.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.SectorProxyPlugin;
import pl.dzoku.sectorsystem.TimeUtil;
import pl.sectorsystem.common.mysql.MySQLService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Listener sprawdzający czy gracz jest zbanowany PRZED dołączeniem do serwera.
 * Działa na poziomie Proxy - blokuje połączenie zanim gracz w ogóle się połączy.
 */
public class BanLoginListener {
    private final SectorProxyPlugin plugin;

    public BanLoginListener(SectorProxyPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        MySQLService mysql = plugin.getMysql();
        if (mysql == null || !mysql.isEnabled()) return;

        String username = event.getUsername();

        try (Connection conn = mysql.getConnection()) {
            // Sprawdź czy gracz jest zbanowany (po nicku LUB po UUID jeśli mamy)
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT reason, expires_at, unbanned FROM discord_bans WHERE mc_nick = ? ORDER BY banned_at DESC LIMIT 1");
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                boolean unbanned = rs.getBoolean("unbanned");
                long expiresAt = rs.getLong("expires_at");
                String reason = rs.getString("reason");

                // Jeśli ban jest aktywny (nieodwołany I nie wygasł)
                if (!unbanned && (expiresAt == 0 || expiresAt > System.currentTimeMillis())) {
                    String timeStr = expiresAt == 0 ? "permanentnie" : TimeUtil.formatTime(expiresAt - System.currentTimeMillis());

                    // Zablokuj logowanie
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                            Component.text()
                                    .append(Component.text("Zostałeś zbanowany!\n", NamedTextColor.RED))
                                    .append(Component.text("Powód: ", NamedTextColor.GRAY)).append(Component.text(reason, NamedTextColor.WHITE)).append(Component.newline())
                                    .append(Component.text("Czas: ", NamedTextColor.GRAY)).append(Component.text(timeStr, NamedTextColor.WHITE)).append(Component.newline())
                                    .append(Component.text("Odwołanie: ", NamedTextColor.GRAY)).append(Component.text("discord.gg/twojserwer", NamedTextColor.WHITE))
                                    .build()
                    ));

                    plugin.getLogger().info("Zablokowano logowanie zbanowanego gracza: " + username);
                    return;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd sprawdzania bana przy logowaniu: " + e.getMessage());
        }
    }
}