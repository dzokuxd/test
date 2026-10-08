package pl.discord; // ZMIEŃ NA SWÓJ PAKIET, np. pl.discord

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Random;

public class DiscordCommand implements CommandExecutor {

    private final SectorSystemPlugin plugin;

    public DiscordCommand(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cTa komenda jest dostępna tylko dla graczy w grze!");
            return true;
        }

        Player player = (Player) sender;
        String uuid = player.getUniqueId().toString();

        // 1. Generuj losowy kod (6 znaków: litery i cyfry)
        String code = generateCode(6);
        long expiresAt = System.currentTimeMillis() + (5 * 60 * 1000L); // Ważny przez 5 minut

        try (Connection conn = plugin.getDiscordDatabase().getConnection()) {

            // 2. Usuń stary kod gracza, jeśli jakiś istnieje
            try (PreparedStatement delPs = conn.prepareStatement("DELETE FROM discord_verifications WHERE uuid = ?")) {
                delPs.setString(1, uuid);
                delPs.executeUpdate();
            }

            // 3. Zapisz nowy kod do bazy danych
            try (PreparedStatement insPs = conn.prepareStatement(
                    "INSERT INTO discord_verifications (uuid, code, expires_at) VALUES (?, ?, ?)")) {
                insPs.setString(1, uuid);
                insPs.setString(2, code);
                insPs.setLong(3, expiresAt);
                insPs.executeUpdate();
            }

            // 4. Wyślij wiadomość do gracza
            long verifyChannelId = plugin.getConfig().getLong("discord.channels.verify", 0);
            String channelMention = verifyChannelId > 0 ? "<#" + verifyChannelId + ">" : "#weryfikacja";

            player.sendMessage("");
            player.sendMessage("§a§l✔ Twój kod weryfikacyjny: §e§l" + code);
            player.sendMessage("§7Wejdź na Discordzie na kanał " + channelMention + " i wpisz ten kod.");
            player.sendMessage("§7Kod wygaśnie za §e5 minut§7.");
            player.sendMessage("");

        } catch (Exception e) {
            player.sendMessage("§cWystąpił błąd podczas generowania kodu! Skontaktuj się z administracją.");
            plugin.getLogger().severe("Błąd generowania kodu Discord dla gracza " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Generuje losowy ciąg znaków (A-Z, 0-9)
     */
    private String generateCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}