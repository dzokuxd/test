package pl.dzoku.sectorsystem.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.sectorsystem.common.mysql.MySQLService;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class UnmuteCommand implements SimpleCommand {
    private final MySQLService mysql;

    public UnmuteCommand(MySQLService mysql) {
        this.mysql = mysql;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length != 1) {
            source.sendMessage(Component.text("Użycie: /unmute <nick>", NamedTextColor.RED));
            return;
        }

        String targetName = args[0];
        String executorName = source instanceof Player ? ((Player) source).getUsername() : "Konsola";

        try (Connection conn = mysql.getConnection()) {
            // Najpierw sprawdź czy w ogóle istnieje aktywny mute
            try (PreparedStatement checkPs = conn.prepareStatement(
                    "SELECT mc_nick FROM discord_mutes WHERE mc_nick = ? AND unmuted = FALSE")) {
                checkPs.setString(1, targetName);
                if (!checkPs.executeQuery().next()) {
                    source.sendMessage(Component.text("Gracz " + targetName + " nie jest obecnie wyciszony.", NamedTextColor.YELLOW));
                    return;
                }
            }

            // Ustaw unmuted = TRUE
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE discord_mutes SET unmuted = TRUE WHERE mc_nick = ? AND unmuted = FALSE")) {
                ps.setString(1, targetName);
                int rows = ps.executeUpdate();

                if (rows > 0) {
                    source.sendMessage(Component.text("✅ Gracz " + targetName + " został odciszony przez " + executorName + ".", NamedTextColor.GREEN));
                } else {
                    source.sendMessage(Component.text("Nie udało się odciszyć gracza " + targetName + ".", NamedTextColor.RED));
                }
            }
        } catch (Exception e) {
            source.sendMessage(Component.text("Błąd bazy danych: " + e.getMessage(), NamedTextColor.RED));
            e.printStackTrace();
        }
    }
}