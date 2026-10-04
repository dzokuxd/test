package pl.dzoku.sectorsystem.auth;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.SimpleCommand.Invocation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.SectorProxyPlugin;
import pl.sectorsystem.common.mysql.MySQLService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;

public class AuthCommand implements SimpleCommand {
    private final SectorProxyPlugin plugin;

    public AuthCommand(SectorProxyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        // Konsola ma wszystkie uprawnienia, więc to zadziała dla gracza i konsoli
        if (!source.hasPermission("sectorsystem.auth")) {
            source.sendMessage(Component.text("Brak uprawnień!", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            showHelp(source);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "whitelist" -> handleWhitelist(source, args);
            case "slot" -> handleSlot(source, args);
            case "motd" -> handleMotd(source, args);
            case "reload" -> handleReload(source);
            case "status" -> handleStatus(source);
            default -> showHelp(source);
        }
    }

    private void handleWhitelist(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Użycie: /auth whitelist <add/remove/on/off/reason> [gracz/tekst]", NamedTextColor.YELLOW));
            return;
        }

        MySQLService mysql = plugin.getMysql();
        if (mysql == null || !mysql.isEnabled()) {
            source.sendMessage(Component.text("MySQL nie jest dostępne!", NamedTextColor.RED));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length < 3) {
                    source.sendMessage(Component.text("Użycie: /auth whitelist add <gracz>", NamedTextColor.YELLOW));
                    return;
                }
                String target = args[2];
                try (Connection c = mysql.getConnection();
                     PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO auth_whitelist (username) VALUES (?)")) {
                    ps.setString(1, target);
                    ps.executeUpdate();
                    source.sendMessage(Component.text("Dodano " + target + " do whitelisty!", NamedTextColor.GREEN));
                } catch (Exception e) {
                    source.sendMessage(Component.text("Błąd: " + e.getMessage(), NamedTextColor.RED));
                }
            }
            case "remove" -> {
                if (args.length < 3) {
                    source.sendMessage(Component.text("Użycie: /auth whitelist remove <gracz>", NamedTextColor.YELLOW));
                    return;
                }
                String target = args[2];
                try (Connection c = mysql.getConnection();
                     PreparedStatement ps = c.prepareStatement("DELETE FROM auth_whitelist WHERE username=?")) {
                    ps.setString(1, target);
                    ps.executeUpdate();
                    source.sendMessage(Component.text("Usunięto " + target + " z whitelisty!", NamedTextColor.GREEN));
                } catch (Exception e) {
                    source.sendMessage(Component.text("Błąd: " + e.getMessage(), NamedTextColor.RED));
                }
            }
            case "on" -> {
                plugin.getMysql().setWhitelistEnabled(true);
                source.sendMessage(Component.text("Whitelist włączona!", NamedTextColor.GREEN));
            }
            case "off" -> {
                plugin.getSystemConfig().getMysql().setWhitelistEnabled(false);
                source.sendMessage(Component.text("Whitelist wyłączona!", NamedTextColor.GREEN));
            }
            case "reason" -> {
                if (args.length < 3) {
                    source.sendMessage(Component.text("Użycie: /auth whitelist reason <tekst>", NamedTextColor.YELLOW));
                    return;
                }
                String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.getMysql().setWhitelistReason(reason);
                source.sendMessage(Component.text("Powód whitelisty ustawiony: " + reason, NamedTextColor.GREEN));
            }
            default -> source.sendMessage(Component.text("Użycie: /auth whitelist <add/remove/on/off/reason>", NamedTextColor.YELLOW));
        }
    }

    private void handleSlot(CommandSource source, String[] args) {
        if (args.length < 4) {
            source.sendMessage(Component.text("Użycie: /auth slot <sektor> set <liczba>", NamedTextColor.YELLOW));
            source.sendMessage(Component.text("Przykład: /auth slot guild set 100", NamedTextColor.GRAY));

            // Pokaż dostępne sektory
            source.sendMessage(Component.text("§7Dostępne sektory:", NamedTextColor.GRAY));
            plugin.getServer().getAllServers().forEach(server -> {
                String status = plugin.getHealthChecker().isSectorOnline(server.getServerInfo().getName())
                        ? "§aONLINE" : "§cOFFLINE";
                source.sendMessage(Component.text("  §e- " + server.getServerInfo().getName() + " §7(" + status + ")", NamedTextColor.GRAY));
            });
            return;
        }

        String sector = args[1].toLowerCase();
        if (!args[2].equalsIgnoreCase("set")) {
            source.sendMessage(Component.text("Użycie: /auth slot <sektor> set <liczba>", NamedTextColor.YELLOW));
            return;
        }

        // Sprawdź czy sektor istnieje w Velocity
        if (!plugin.getServer().getServer(sector).isPresent()) {
            source.sendMessage(Component.text("§cBłąd: Sektor §e'" + sector + "' §cnie istnieje w konfiguracji Velocity!", NamedTextColor.RED));
            return;
        }

        try {
            int slotCount = Integer.parseInt(args[3]);
            if (slotCount < 0) {
                source.sendMessage(Component.text("Liczba slotów nie może być ujemna!", NamedTextColor.RED));
                return;
            }

            MySQLService mysql = plugin.getMysql();
            if (mysql == null || !mysql.isEnabled()) {
                source.sendMessage(Component.text("MySQL nie jest dostępne!", NamedTextColor.RED));
                return;
            }

            mysql.setSectorSlots(sector, slotCount);
            source.sendMessage(Component.text("§aPomyślnie ustawiono §e" + slotCount + " §aslotów dla sektora: §e" + sector, NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            source.sendMessage(Component.text("Nieprawidłowa liczba!", NamedTextColor.RED));
        }
    }

    private void handleMotd(CommandSource source, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("set")) {
            source.sendMessage(Component.text("Użycie: /auth motd set <tekst>", NamedTextColor.YELLOW));
            return;
        }

        String motd = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        plugin.getMysql().setMotd(motd);
        source.sendMessage(Component.text("MOTD ustawione: " + motd, NamedTextColor.GREEN));
    }

    private void handleReload(CommandSource source) {
        plugin.reloadConfig();
        source.sendMessage(Component.text("Konfiguracja przeładowana!", NamedTextColor.GREEN));
    }

    private void handleStatus(CommandSource source) {
        MySQLService mysql = plugin.getMysql();
        if (mysql == null || !mysql.isEnabled()) {
            source.sendMessage(Component.text("MySQL nie jest dostępne!", NamedTextColor.RED));
            return;
        }

        try (Connection c = mysql.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) as total, SUM(CASE WHEN premium=1 THEN 1 ELSE 0 END) as premium FROM auth_players");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int total = rs.getInt("total");
                int premium = rs.getInt("premium");
                int cracked = total - premium;

                source.sendMessage(Component.text("=== Status Systemu Auth ===", NamedTextColor.GOLD));
                source.sendMessage(Component.text("Łącznie kont: " + total, NamedTextColor.YELLOW));
                source.sendMessage(Component.text("Premium: " + premium, NamedTextColor.GREEN));
                source.sendMessage(Component.text("Cracked: " + cracked, NamedTextColor.RED));
                source.sendMessage(Component.text("Whitelist: " + (plugin.getSystemConfig().getMysql().isWhitelistEnabled() ? "WŁĄCZONA" : "WYŁĄCZONA"), NamedTextColor.YELLOW));
                source.sendMessage(Component.text("Sloty: " + plugin.getSystemConfig().getMysql().getMaxSlots(), NamedTextColor.YELLOW));
            }
        } catch (Exception e) {
            source.sendMessage(Component.text("Błąd pobierania statusu: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void showHelp(CommandSource source) {
        source.sendMessage(Component.text("=== Komendy Auth ===", NamedTextColor.GOLD));
        source.sendMessage(Component.text("/auth whitelist add <gracz>", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/auth whitelist remove <gracz>", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/auth whitelist on/off", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/auth whitelist reason <tekst>", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/auth slot sektor set <liczba>", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/auth motd set <tekst>", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/auth reload", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/auth status", NamedTextColor.YELLOW));
    }
}