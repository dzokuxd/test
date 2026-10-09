package pl.dzoku.sectorsystem.managers;

import pl.dzoku.sectorsystem.SectorProxyPlugin;
import pl.sectorsystem.common.mysql.MySQLService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.TimeUnit;

/**
 * Task który co 5 minut czyści wygasłe bany i muty z bazy danych.
 * Ustawia unbanned=TRUE / unmuted=TRUE dla kar których czas minął.
 */
public class ExpiredPunishmentsCleaner {
    private final SectorProxyPlugin plugin;
    private volatile boolean running = true;

    public ExpiredPunishmentsCleaner(SectorProxyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().buildTask(plugin, this::cleanExpiredPunishments)
                .repeat(2, TimeUnit.MINUTES)
                .delay(1, TimeUnit.MINUTES)
                .schedule();
        plugin.getLogger().info("Uruchomiono task czyszczący wygasłe kary (co 2 minut)");
    }

    public void stop() {
        running = false;
    }

    private void cleanExpiredPunishments() {
        if (!running) return;
        MySQLService mysql = plugin.getMysql();
        if (mysql == null || !mysql.isEnabled()) return;

        long now = System.currentTimeMillis();

        try (Connection conn = mysql.getConnection()) {
            // Czyść wygasłe bany
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE discord_bans SET unbanned = TRUE WHERE unbanned = FALSE AND expires_at > 0 AND expires_at <= ?")) {
                ps.setLong(1, now);
                int updated = ps.executeUpdate();
                if (updated > 0) {
                    plugin.getLogger().info("Automatycznie odbanowano " + updated + " graczy (wygasły czas bana)");
                }
            }

            // Czyść wygasłe muty
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE discord_mutes SET unmuted = TRUE WHERE unmuted = FALSE AND expires_at > 0 AND expires_at <= ?")) {
                ps.setLong(1, now);
                int updated = ps.executeUpdate();
                if (updated > 0) {
                    plugin.getLogger().info("Automatycznie odciszono " + updated + " graczy (wygasł czas mute)");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd czyszczenia wygasłych kar: " + e.getMessage());
        }
    }
}