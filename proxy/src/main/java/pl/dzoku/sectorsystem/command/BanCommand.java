package pl.dzoku.sectorsystem.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.TimeUtil;
import pl.sectorsystem.common.mysql.MySQLService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class BanCommand implements SimpleCommand {
    private final MySQLService mysql;
    private final ProxyServer proxy;

    public BanCommand(MySQLService mysql, ProxyServer proxy) {
        this.mysql = mysql;
        this.proxy = proxy;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length < 3) {
            source.sendMessage(Component.text("Użycie: /ban <nick> <czas> <powód>", NamedTextColor.RED));
            source.sendMessage(Component.text("Przykład: /ban dzokv 7d Cheating", NamedTextColor.GRAY));
            return;
        }

        String targetName = args[0];
        String timeStr = args[1];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        String executorName = source instanceof Player ? ((Player) source).getUsername() : "Konsola";

        long durationMs;
        try {
            durationMs = TimeUtil.parseTime(timeStr);
        } catch (IllegalArgumentException e) {
            source.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return;
        }

        UUID targetUuid = getUuidFromDatabase(targetName);
        if (targetUuid == null) {
            source.sendMessage(Component.text("Nie znaleziono gracza: " + targetName, NamedTextColor.RED));
            return;
        }

        // ✅ POBIERZ DISCORD_ID z tabeli discord_linked
        String discordId = getDiscordId(targetUuid.toString());

        long expiresAt = durationMs == 0 ? 0 : System.currentTimeMillis() + durationMs;
        String timeFormatted = TimeUtil.formatTime(durationMs);

        try (Connection conn = mysql.getConnection()) {
            String sql = "INSERT INTO discord_bans (uuid, discord_id, mc_nick, reason, banned_by, banned_at, expires_at, unbanned) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, FALSE) " +
                    "ON DUPLICATE KEY UPDATE reason=VALUES(reason), banned_by=VALUES(banned_by), " +
                    "banned_at=VALUES(banned_at), expires_at=VALUES(expires_at), unbanned=FALSE";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, targetUuid.toString());
                ps.setString(2, discordId); // ✅ ZAPISZ DISCORD_ID
                ps.setString(3, targetName);
                ps.setString(4, reason);
                ps.setString(5, executorName);
                ps.setLong(6, System.currentTimeMillis());
                ps.setLong(7, expiresAt);
                ps.executeUpdate();
            }

            String msg = durationMs == 0
                    ? "Gracz " + targetName + " został zbanowany permanentnie. Powód: " + reason
                    : "Gracz " + targetName + " został zbanowany na " + timeFormatted + ". Powód: " + reason;

            source.sendMessage(Component.text("✅ " + msg, NamedTextColor.GREEN));

            // Wyrzuć gracza jeśli online
            proxy.getPlayer(targetUuid).ifPresent(player -> {
                player.disconnect(Component.text()
                        .append(Component.text("Zostałeś zbanowany!\n", NamedTextColor.RED))
                        .append(Component.text("Powód: ", NamedTextColor.GRAY)).append(Component.text(reason, NamedTextColor.WHITE)).append(Component.newline())
                        .append(Component.text("Czas: ", NamedTextColor.GRAY)).append(Component.text(timeFormatted, NamedTextColor.WHITE)).append(Component.newline())
                        .append(Component.text("Zbanował: ", NamedTextColor.GRAY)).append(Component.text(executorName, NamedTextColor.WHITE))
                        .build());
            });

        } catch (Exception e) {
            source.sendMessage(Component.text("Błąd bazy danych: " + e.getMessage(), NamedTextColor.RED));
            e.printStackTrace();
        }
    }

    private UUID getUuidFromDatabase(String username) {
        if (!mysql.isEnabled()) return null;
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM auth_players WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString("uuid"));
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ✅ NOWA METODA: pobiera Discord ID z tabeli discord_linked
    private String getDiscordId(String uuid) {
        if (!mysql.isEnabled()) return null;
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT discord_id FROM discord_linked WHERE uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("discord_id");
            }
        } catch (Exception ignored) {}
        return null;
    }
}