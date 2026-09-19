package pl.dzoku.sectorsystem.auth;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.SectorProxyPlugin;
import pl.sectorsystem.common.mysql.MySQLService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
public class AuthCommand implements SimpleCommand {
    private final SectorProxyPlugin plugin;

    public AuthCommand(SectorProxyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Ta komenda jest tylko dla graczy!", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("sectorsystem.auth")) {
            player.sendMessage(Component.text("Brak uprawnień!", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            showHelp(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "whitelist" -> handleWhitelist(player, args);
            case "slot" -> handleSlot(player, args);
            case "motd" -> handleMotd(player, args);
            case "reload" -> handleReload(player);
            case "status" -> handleStatus(player);
            default -> showHelp(player);
        }
    }

    private void handleWhitelist(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Użycie: /auth whitelist <add/remove/on/off/reason> [gracz/tekst]", NamedTextColor.YELLOW));
            return;
        }

        MySQLService mysql = plugin.getMysql();
        if (mysql == null || !mysql.isEnabled()) {
            player.sendMessage(Component.text("MySQL nie jest dostępne!", NamedTextColor.RED));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Użycie: /auth whitelist add <gracz>", NamedTextColor.YELLOW));
                    return;
                }
                String target = args[2];
                try (Connection c = mysql.getConnection(); // getConnection() musi być public
                     PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO auth_whitelist (username) VALUES (?)")) {
                    ps.setString(1, target);
                    ps.executeUpdate();
                    player.sendMessage(Component.text("Dodano " + target + " do whitelisty!", NamedTextColor.GREEN));
                } catch (Exception e) {
                    player.sendMessage(Component.text("Błąd: " + e.getMessage(), NamedTextColor.RED));
                }
            }
            case "remove" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Użycie: /auth whitelist remove <gracz>", NamedTextColor.YELLOW));
                    return;
                }
                String target = args[2];
                try (Connection c = mysql.getConnection(); // getConnection() musi być public
                     PreparedStatement ps = c.prepareStatement("DELETE FROM auth_whitelist WHERE username=?")) {
                    ps.setString(1, target);
                    ps.executeUpdate();
                    player.sendMessage(Component.text("Usunięto " + target + " z whitelisty!", NamedTextColor.GREEN));
                } catch (Exception e) {
                    player.sendMessage(Component.text("Błąd: " + e.getMessage(), NamedTextColor.RED));
                }
            }
            case "on" -> {
                plugin.getSystemConfig().getMysql().setWhitelistEnabled(true);
                player.sendMessage(Component.text("Whitelist włączona!", NamedTextColor.GREEN));
            }
            case "off" -> {
                plugin.getSystemConfig().getMysql().setWhitelistEnabled(false);
                player.sendMessage(Component.text("Whitelist wyłączona!", NamedTextColor.GREEN));
            }
            case "reason" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Użycie: /auth whitelist reason <tekst>", NamedTextColor.YELLOW));
                    return;
                }
                String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                plugin.getSystemConfig().getMysql().setWhitelistReason(reason);
                player.sendMessage(Component.text("Powód whitelisty ustawiony: " + reason, NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text("Użycie: /auth whitelist <add/remove/on/off/reason>", NamedTextColor.YELLOW));
        }
    }

    private void handleSlot(Player player, String[] args) {
        if (args.length < 4 || !args[1].equalsIgnoreCase("sektor") || !args[2].equalsIgnoreCase("set")) {
            player.sendMessage(Component.text("Użycie: /auth slot sektor set <liczba>", NamedTextColor.YELLOW));
            return;
        }

        try {
            int slotCount = Integer.parseInt(args[3]);
            if (slotCount < 0) {
                player.sendMessage(Component.text("Liczba slotów nie może być ujemna!", NamedTextColor.RED));
                return;
            }
            plugin.getSystemConfig().getMysql().setMaxSlots(slotCount);
            player.sendMessage(Component.text("Ustawiono " + slotCount + " slotów!", NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Nieprawidłowa liczba!", NamedTextColor.RED));
        }
    }

    private void handleMotd(Player player, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("set")) {
            player.sendMessage(Component.text("Użycie: /auth motd set <tekst>", NamedTextColor.YELLOW));
            return;
        }

        String motd = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        plugin.getSystemConfig().getMysql().setMotd(motd);
        player.sendMessage(Component.text("MOTD ustawione: " + motd, NamedTextColor.GREEN));
    }

    private void handleReload(Player player) {
        plugin.reloadConfig();
        player.sendMessage(Component.text("Konfiguracja przeładowana!", NamedTextColor.GREEN));
    }

    private void handleStatus(Player player) {
        MySQLService mysql = plugin.getMysql();
        if (mysql == null || !mysql.isEnabled()) {
            player.sendMessage(Component.text("MySQL nie jest dostępne!", NamedTextColor.RED));
            return;
        }

        try (Connection c = mysql.getConnection(); // getConnection() musi być public
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) as total, SUM(CASE WHEN premium=1 THEN 1 ELSE 0 END) as premium FROM auth_players");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int total = rs.getInt("total");
                int premium = rs.getInt("premium");
                int cracked = total - premium;

                player.sendMessage(Component.text("=== Status Systemu Auth ===", NamedTextColor.GOLD));
                player.sendMessage(Component.text("Łącznie kont: " + total, NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Premium: " + premium, NamedTextColor.GREEN));
                player.sendMessage(Component.text("Cracked: " + cracked, NamedTextColor.RED));
                player.sendMessage(Component.text("Whitelist: " + (plugin.getSystemConfig().getMysql().isWhitelistEnabled() ? "WŁĄCZONA" : "WYŁĄCZONA"), NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Sloty: " + plugin.getSystemConfig().getMysql().getMaxSlots(), NamedTextColor.YELLOW));
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("Błąd pobierania statusu: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text("=== Komendy Auth ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/auth whitelist add <gracz>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/auth whitelist remove <gracz>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/auth whitelist on/off", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/auth whitelist reason <tekst>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/auth slot sektor set <liczba>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/auth motd set <tekst>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/auth reload", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/auth status", NamedTextColor.YELLOW));
    }
}
