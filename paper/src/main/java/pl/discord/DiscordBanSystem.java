package pl.discord;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DiscordBanSystem {
    private final SectorSystemPlugin plugin;
    private final DiscordDatabase db;

    public DiscordBanSystem(SectorSystemPlugin plugin) {
        this.plugin = plugin;
        this.db = plugin.getDiscordDatabase();
    }

    public void createAppealModal(ButtonInteractionEvent event) {
        String discordId = event.getUser().getId();
        if (!isBanned(discordId)) {
            event.reply("❌ Nie masz aktywnego bana.").setEphemeral(true).queue();
            return;
        }

        Modal modal = Modal.create("ban_appeal_" + discordId, "Odwołanie od bana")
                .addActionRow(TextInput.create("appeal_reason", "Opisz swoją sytuację", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Dlaczego uważasz, że ban powinien zostać zdjęty?")
                        .setRequiredRange(50, 1000).build())
                .addActionRow(TextInput.create("appeal_screenshot", "Link do screenshotów (opcjonalne)", TextInputStyle.SHORT)
                        .setPlaceholder("https://imgur.com/...").setRequired(false).build())
                .build();
        event.replyModal(modal).queue();
    }

    public void handleAppealSubmit(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        if (!modalId.startsWith("ban_appeal_")) return;

        String discordId = modalId.substring("ban_appeal_".length());
        String reason = event.getValue("appeal_reason").getAsString();
        String screenshot = event.getValue("appeal_screenshot") != null ? event.getValue("appeal_screenshot").getAsString() : null;

        try (Connection conn = db.getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM discord_bans WHERE discord_id = ? AND unbanned = FALSE");
            ps.setString(1, discordId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                event.reply("❌ Nie znaleziono aktywnego bana.").setEphemeral(true).queue();
                return;
            }

            String banUuid = rs.getString("uuid");
            PreparedStatement appealPs = conn.prepareStatement("INSERT INTO discord_ban_appeals (ban_uuid, discord_id, message, screenshot_url, submitted_at) VALUES (?, ?, ?, ?, ?)");
            appealPs.setString(1, banUuid);
            appealPs.setString(2, discordId);
            appealPs.setString(3, reason);
            appealPs.setString(4, screenshot);
            appealPs.setLong(5, System.currentTimeMillis());
            appealPs.executeUpdate();

            plugin.getLogger().info("Nowe odwołanie od bana od: " + event.getUser().getAsTag() + " | Powód: " + reason);
            event.reply("✅ Odwołanie złożone. Poczekaj na odpowiedź administracji.").setEphemeral(true).queue();
        } catch (Exception e) {
            event.reply("❌ Błąd podczas składania odwołania.").setEphemeral(true).queue();
        }
    }

    public boolean isBanned(String discordId) {
        try (Connection conn = db.getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT unbanned FROM discord_bans WHERE discord_id = ?");
            ps.setString(1, discordId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && !rs.getBoolean("unbanned");
        } catch (Exception e) { return false; }
    }
}
