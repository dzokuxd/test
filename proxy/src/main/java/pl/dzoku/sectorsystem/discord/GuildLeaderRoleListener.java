package pl.dzoku.sectorsystem.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.SectorProxyPlugin;
import pl.sectorsystem.common.mysql.MySQLService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Listener sprawdzający rangę Lidera przy każdym wejściu gracza na proxy.
 * Nadaje rangę jeśli gracz spełnia wymagania, zabiera jeśli nie.
 *
 * NAPRAWA: Wiadomość wysyłana TYLKO gdy stan się zmienił (nadano/zabrano rolę).
 * Jeśli gracz już ma/nie ma roli i stan jest poprawny - nic się nie dzieje.
 */
public class GuildLeaderRoleListener {

    private final SectorProxyPlugin plugin;

    public GuildLeaderRoleListener(SectorProxyPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();

        // 1. Pobierz Discord ID gracza z bazy (sprawdź czy jest zweryfikowany)
        String discordId = getLinkedDiscordId(uuid);
        if (discordId == null) {
            return; // Gracz niezweryfikowany - pomijamy
        }

        // 2. Pobierz konfigurację
        int requirement = plugin.getCfg().getDiscordLeaderRequirement();
        long leaderRoleId = plugin.getDiscordManager().getLeaderRoleId();
        if (leaderRoleId == 0) {
            return; // Rola Lider nie skonfigurowana
        }

        // 3. Sprawdź status gildii w bazie danych
        boolean shouldHaveRole = false;
        int memberCount = 0;
        boolean isLeader = false;

        MySQLService mysql = plugin.getMysql();
        if (mysql != null && mysql.isEnabled()) {
            try (Connection conn = mysql.getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT owner_uuid, members FROM guilds WHERE JSON_CONTAINS(members, ?)");
                ps.setString(1, "\"" + uuid + "\"");
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    isLeader = rs.getString("owner_uuid").equals(uuid);
                    memberCount = 0;
                    String membersJson = rs.getString("members");
                    if (membersJson != null && !membersJson.isEmpty()) {
                        try {
                            JsonArray arr = JsonParser.parseString(membersJson).getAsJsonArray();
                            memberCount = arr.size();
                        } catch (Exception ignored) {
                            plugin.getLogger().warning("Błąd parsowania JSON członków gildii dla: " + player.getUsername());
                        }
                    }
                    if (isLeader && memberCount >= requirement) {
                        shouldHaveRole = true;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Błąd sprawdzania gildii dla gracza " + player.getUsername() + ": " + e.getMessage());
                return;
            }
        }

        // 4. ✅ KLUCZOWE: Sprawdź czy gracz JUŻ ma/nie ma roli na Discordzie
        DiscordManager dm = plugin.getDiscordManager();
        boolean hasRole = dm.hasRole(discordId, leaderRoleId);

        // 5. Podejmij akcję TYLKO jeśli stan się zmienił
        if (shouldHaveRole && !hasRole) {
            // Gracz spełnia warunki, ale NIE MA roli → nadaj
            dm.giveRole(discordId, leaderRoleId);
            player.sendMessage(Component.text("✔ Otrzymałeś rangę Lider na Discordzie!", NamedTextColor.GREEN));
            plugin.getLogger().info("Nadano rangę Lider dla gracza " + player.getUsername() +
                    " (Discord: " + discordId + ", gildia: " + memberCount + " członków)");
        } else if (!shouldHaveRole && hasRole) {
            // Gracz NIE spełnia warunków, ale MA rolę → zabierz
            dm.removeRole(discordId, leaderRoleId);
            player.sendMessage(Component.text("✘ Utracono rangę Lider na Discordzie!", NamedTextColor.RED));
            plugin.getLogger().info("Zabrano rangę Lider dla gracza " + player.getUsername() +
                    " (Discord: " + discordId + ")");
        } else {
            // ✅ Stan się nie zmienił - NIC NIE RÓB (to naprawia Twój problem!)
            if (shouldHaveRole && hasRole) {
                plugin.getLogger().fine("Gracz " + player.getUsername() +
                        " już ma rangę Lider (gildia: " + memberCount + " członków) - pomijam");
            } else {
                plugin.getLogger().fine("Gracz " + player.getUsername() +
                        " nie ma rangi Lider (isLeader: " + isLeader + ", members: " + memberCount + ") - pomijam");
            }
        }
    }

    /**
     * Pobiera Discord ID gracza z bazy danych.
     */
    private String getLinkedDiscordId(String uuid) {
        MySQLService mysql = plugin.getMysql();
        if (mysql == null || !mysql.isEnabled()) return null;

        try (Connection conn = mysql.getConnection()) {
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