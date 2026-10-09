package pl.dzoku.sectorsystem.discord;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.SectorProxyPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class DiscordCommand implements SimpleCommand {
    private final SectorProxyPlugin plugin;

    public DiscordCommand(SectorProxyPlugin plugin) { this.plugin = plugin; }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player)) {
            source.sendMessage(Component.text("Ta komenda jest dostępna tylko dla graczy w grze!", NamedTextColor.RED));
            return;
        }
        Player player = (Player) source;
        String uuid = player.getUniqueId().toString();

        // Sprawdź czy już zweryfikowany
        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getMysql().getConnection()) {
                PreparedStatement checkPs = conn.prepareStatement("SELECT mc_nick FROM discord_linked WHERE uuid = ?");
                checkPs.setString(1, uuid);
                ResultSet rs = checkPs.executeQuery();
                if (rs.next()) {
                    player.sendMessage(Component.text(""));
                    player.sendMessage(Component.text("✔ Jesteś już zweryfikowany jako: ", NamedTextColor.GREEN)
                            .append(Component.text(rs.getString("mc_nick"), NamedTextColor.YELLOW)));
                    player.sendMessage(Component.text("Nie możesz się ponownie zweryfikować.", NamedTextColor.GRAY));
                    player.sendMessage(Component.text(""));
                    return;
                }

                String code = generateCode(6);
                long expiresAt = System.currentTimeMillis() + (5 * 60 * 1000L);

                try (PreparedStatement delPs = conn.prepareStatement("DELETE FROM discord_verifications WHERE uuid = ?")) {
                    delPs.setString(1, uuid); delPs.executeUpdate();
                }
                try (PreparedStatement insPs = conn.prepareStatement(
                        "INSERT INTO discord_verifications (uuid, code, expires_at) VALUES (?, ?, ?)")) {
                    insPs.setString(1, uuid); insPs.setString(2, code); insPs.setLong(3, expiresAt);
                    insPs.executeUpdate();
                }

                player.sendMessage(Component.text(""));
                player.sendMessage(Component.text("✔ Twój kod weryfikacyjny: ", NamedTextColor.GREEN)
                        .append(Component.text(code, NamedTextColor.YELLOW)));
                player.sendMessage(Component.text("Wejdź na kanał #weryfikacja na Discordzie i kliknij przycisk.", NamedTextColor.GRAY));
                player.sendMessage(Component.text("Kod wygaśnie za 5 minut.", NamedTextColor.GRAY));
                player.sendMessage(Component.text(""));
            } catch (Exception e) {
                player.sendMessage(Component.text("Wystąpił błąd podczas generowania kodu!", NamedTextColor.RED));
                plugin.getLogger().severe("Błąd generowania kodu: " + e.getMessage());
            }
        });
    }

    private String generateCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }
}