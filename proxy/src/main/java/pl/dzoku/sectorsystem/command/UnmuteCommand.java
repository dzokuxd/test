package pl.dzoku.sectorsystem.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class UnmuteCommand implements SimpleCommand {
    private final javax.sql.DataSource dataSource;

    public UnmuteCommand(javax.sql.DataSource dataSource) {
        this.dataSource = dataSource;
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

        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE discord_mutes SET unmuted = TRUE WHERE mc_nick = ? AND unmuted = FALSE";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, targetName);
                int rows = ps.executeUpdate();

                if (rows > 0) {
                    source.sendMessage(Component.text("✅ Gracz " + targetName + " został odciszony.", NamedTextColor.GREEN));
                } else {
                    source.sendMessage(Component.text("Gracz " + targetName + " nie jest wyciszony lub nie istnieje.", NamedTextColor.YELLOW));
                }
            }
        } catch (Exception e) {
            source.sendMessage(Component.text("Błąd bazy danych: " + e.getMessage(), NamedTextColor.RED));
        }
    }
}