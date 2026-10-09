package pl.dzoku.sectorsystem.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import pl.gildie.util.TimeUtil;
import pl.sectorsystem.common.mysql.MySQLService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Listener blokujący czat zmutowanym graczom na serwerach Paper.
 */
public class MuteChatListener implements Listener {
    private final SectorSystemPlugin plugin;

    public MuteChatListener(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();

        MySQLService mysql = plugin.getMysql();
        if (mysql == null || !mysql.isEnabled()) return;

        try (Connection conn = mysql.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT reason, expires_at, unmuted FROM discord_mutes WHERE uuid = ? ORDER BY muted_at DESC LIMIT 1");
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                boolean unmuted = rs.getBoolean("unmuted");
                long expiresAt = rs.getLong("expires_at");
                String reason = rs.getString("reason");

                // Jeśli mute jest aktywny
                if (!unmuted && (expiresAt == 0 || expiresAt > System.currentTimeMillis())) {
                    event.setCancelled(true); // Zablokuj wiadomość

                    String timeStr = expiresAt == 0 ? "permanentnie" : TimeUtil.formatTime(expiresAt - System.currentTimeMillis());

                    // Wyślij informację do gracza (musi być na głównym wątku)
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§cJesteś wyciszony!");
                        player.sendMessage("§7Powód: §e" + reason);
                        player.sendMessage("§7Czas: §e" + timeStr);
                    });
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd sprawdzania mute: " + e.getMessage());
        }
    }
}