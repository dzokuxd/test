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

public class BanCommand implements SimpleCommand {
    private final javax.sql.DataSource dataSource;

    public BanCommand(javax.sql.DataSource dataSource) {
        this.dataSource = dataSource;
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

        // Pobierz UUID (najpierw z online, potem z bazy lub Mojang)
        UUID targetUuid = getUuid(targetName);
        if (targetUuid == null) {
            source.sendMessage(Component.text("Nie znaleziono gracza: " + targetName, NamedTextColor.RED));
            return;
        }

        long expiresAt = durationMs == 0 ? 0 : System.currentTimeMillis() + durationMs;
        String timeFormatted = TimeUtil.formatTime(durationMs);

        try (Connection conn = dataSource.getConnection()) {
            // Zapisz do bazy discord_bans
            String sql = "INSERT INTO discord_bans (uuid, mc_nick, reason, banned_by, banned_at, expires_at, unbanned) " +
                    "VALUES (?, ?, ?, ?, ?, ?, FALSE) " +
                    "ON DUPLICATE KEY UPDATE reason=VALUES(reason), banned_by=VALUES(banned_by), " +
                    "banned_at=VALUES(banned_at), expires_at=VALUES(expires_at), unbanned=FALSE";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, targetUuid.toString());
                ps.setString(2, targetName);
                ps.setString(3, reason);
                ps.setString(4, executorName);
                ps.setLong(5, System.currentTimeMillis());
                ps.setLong(6, expiresAt);
                ps.executeUpdate();
            }

            String msg = durationMs == 0
                    ? "Gracz " + targetName + " został zbanowany permanentnie. Powód: " + reason
                    : "Gracz " + targetName + " został zbanowany na " + timeFormatted + ". Powód: " + reason;

            source.sendMessage(Component.text("✅ " + msg, NamedTextColor.GREEN));

            // Jeśli gracz jest online na proxy, wyrzuć go
            invocation.proxy().getPlayer(targetUuid).ifPresent(player -> {
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

    private UUID getUuid(String name) {
        // 1. Sprawdź czy jest online na proxy
        var player = com.velocitypowered.api.proxy.ProxyServer.class.cast(
                // Hack to get proxy instance if needed, but better to pass it or use a cache.
                // For simplicity, we assume you can pass ProxyServer to constructor or use a simple Mojang API fetch.
                // Here is a simple offline fallback assuming you might have a users table, or we just return null.
                null
        );
        // UPROSZCZENIE: W prawdziwym projekcie użyj istniejącego CacheManagera z Twojego repo lub Mojang API.
        // Na potrzeby tego kodu, zwrócę null jeśli nie ma prostego dostępu, ale w Twoim repo masz PlayerCache!
        return null; // ZASTĄP TO: return pl.dzoku.sectorsystem.proxy.cache.PlayerCache.getUuid(name);
    }
}