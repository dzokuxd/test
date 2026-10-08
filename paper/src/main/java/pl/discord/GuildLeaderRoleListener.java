package pl.discord; // ZMIEŃ NA SWÓJ PAKIET

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import pl.gildie.model.Guild;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Listener sprawdzający rangę Lidera przy każdym wejściu gracza na serwer.
 * Nadaje rangę jeśli gracz spełnia wymagania, zabiera jeśli nie.
 */
public class GuildLeaderRoleListener implements Listener {

    private final SectorSystemPlugin plugin;

    public GuildLeaderRoleListener(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();

        // 1. Sprawdź czy gracz jest zweryfikowany (ma Discord ID)
        String discordId = getLinkedDiscordId(uuid);
        if (discordId == null) {
            return; // Gracz niezweryfikowany - pomijamy
        }

        // 2. Pobierz konfigurację
        int requirement = plugin.getConfig().getInt("discord.guilds.leader-member-requirement", 10);
        long leaderRoleId = plugin.getConfig().getLong("discord.roles.leader");

        // 3. Sprawdź status gildii
        boolean shouldHaveRole = false;
        int memberCount = 0;
        boolean isLeader = false;

        try {
            var gildieModule = plugin.getGildieModule();
            if (gildieModule != null && gildieModule.getGuildManager() != null) {
                Guild guild = gildieModule.getGuildManager().getGuildByPlayer(player.getUniqueId());

                if (guild != null) {
                    isLeader = guild.getOwner() != null && guild.getOwner().equals(player.getUniqueId());
                    memberCount = guild.getMembers() != null ? guild.getMembers().size() : 0;

                    if (isLeader && memberCount >= requirement) {
                        shouldHaveRole = true;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd sprawdzania gildii dla gracza " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }

        // 4. Nadaj lub zabierz rangę
        boolean hasRole = plugin.getDiscordManager().hasRole(discordId, leaderRoleId);

        if (shouldHaveRole && !hasRole) {
            // Nadaj rangę
            plugin.getDiscordManager().giveRole(discordId, leaderRoleId);
            player.sendMessage("§a§l✔ Otrzymałeś rangę §6Lider §ana Discordzie!");
            plugin.getLogger().info("Nadano rangę Lider dla gracza " + player.getName() + " (Discord: " + discordId + ")");
        } else if (!shouldHaveRole && hasRole) {
            // Zabierz rangę
            plugin.getDiscordManager().removeRole(discordId, leaderRoleId);
            player.sendMessage("§c§l✘ Utracono rangę §6Lider §cna Discordzie!");
            plugin.getLogger().info("Zabrano rangę Lider dla gracza " + player.getName() + " (Discord: " + discordId + ")");
        } else if (shouldHaveRole && hasRole) {
            // Gracz już ma rangę i powinien ją mieć - nic nie rób
            plugin.getLogger().fine("Gracz " + player.getName() + " już ma rangę Lider (gildia: " + memberCount + " członków)");
        } else {
            // Gracz nie ma rangi i nie powinien jej mieć - nic nie rób
            plugin.getLogger().fine("Gracz " + player.getName() + " nie ma rangi Lider (isLeader: " + isLeader + ", members: " + memberCount + ")");
        }
    }

    /**
     * Pobiera Discord ID gracza z bazy danych.
     */
    private String getLinkedDiscordId(String uuid) {
        try (Connection conn = plugin.getDiscordDatabase().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT discord_id FROM discord_linked WHERE uuid = ?");
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("discord_id") : null;
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd pobierania Discord ID dla UUID " + uuid + ": " + e.getMessage());
            return null;
        }
    }
}