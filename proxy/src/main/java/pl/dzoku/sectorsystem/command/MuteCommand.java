package pl.dzoku.sectorsystem.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.TimeUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

public class MuteCommand implements SimpleCommand {
    private final javax.sql.DataSource dataSource;

    public MuteCommand(javax.sql.DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length < 3) {
            source.sendMessage(Component.text("Użycie: /mute <nick> <czas> <powód>", NamedTextColor.RED));
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

        UUID targetUuid = getUuid(targetName); // Użyj swojego CacheManagera
        if (targetUuid == null) {
            source.sendMessage(Component.text("Nie znaleziono gracza: " + targetName, NamedTextColor.RED));
            return;
        }

        long expiresAt = durationMs == 0 ? 0 : System.currentTimeMillis() + durationMs;
        String timeFormatted = TimeUtil.formatTime(durationMs);

        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO discord_mutes (uuid, mc_nick, reason, muted_by, muted_at, expires_at, unmuted) " +
                    "VALUES (?, ?, ?, ?, ?, ?, FALSE) " +
                    "ON DUPLICATE KEY UPDATE reason=VALUES(reason), muted_by=VALUES(muted_by), " +
                    "muted_at=VALUES(muted_at), expires_at=VALUES(expires_at), unmuted=FALSE";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, targetUuid.toString());
                ps.setString(2, targetName);
                ps.setString(3, reason);
                ps.setString(4, executorName);
                ps.setLong(5, System.currentTimeMillis());
                ps.setLong(6, expiresAt);
                ps.executeUpdate();
            }

            source.sendMessage(Component.text("✅ Gracz " + targetName + " został wyciszony na " + timeFormatted + ". Powód: " + reason, NamedTextColor.GREEN));

        } catch (Exception e) {
            source.sendMessage(Component.text("Błąd bazy danych: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private UUID getUuid(String name) {
        // ZASTĄP TO: return pl.dzoku.sectorsystem.proxy.cache.PlayerCache.getUuid(name);
        return null;
    }
}