package pl.dzoku.sectorsystem.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import net.kyori.adventure.text.Component;
import pl.dzoku.sectorsystem.SectorProxyPlugin;
import pl.sectorsystem.common.mysql.MySQLService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class WhitelistLoginListener {
    private final SectorProxyPlugin plugin;

    public WhitelistLoginListener(SectorProxyPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        MySQLService mysql = plugin.getMysql();

        if (mysql == null || !mysql.isEnabled() || !mysql.isWhitelistEnabled()) {
            return;
        }

        String username = event.getUsername();

        try (Connection c = mysql.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM auth_whitelist WHERE username=?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd sprawdzania whitelisty: " + e.getMessage());
            return;
        }

        String reason = mysql.getWhitelistReason();
        if (reason == null || reason.isBlank()) {
            reason = "Serwer jest obecnie na whitelist.\nSkontaktuj się z administracją.";
        }

        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                Component.text("§c§l[WHITELIST]\n\n§7" + reason)
        ));
    }
}