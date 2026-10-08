package pl.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * System ticketów Discord - gracze mogą tworzyć prywatne kanały pomocy.
 */
public class DiscordTicketSystem {
    private final SectorSystemPlugin plugin;

    public DiscordTicketSystem(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Obsługuje kliknięcie przycisku "Utwórz ticket".
     */
    public void handleCreateTicket(ButtonInteractionEvent event) {
        String discordId = event.getUser().getId();

        // Sprawdź czy gracz jest zweryfikowany
        if (!plugin.getDiscordManager().isVerified(discordId)) {
            event.reply("❌ Musisz być zweryfikowany aby utworzyć ticket!\nUżyj komendy `/discord` w grze.")
                    .setEphemeral(true).queue();
            return;
        }

        // Sprawdź czy gracz nie ma już otwartego ticketa
        if (hasOpenTicket(discordId)) {
            event.reply("❌ Masz już otwarty ticket! Zamknij go przed utworzeniem nowego.")
                    .setEphemeral(true).queue();
            return;
        }

        // Otwórz modal z tematem ticketa
        Modal modal = Modal.create("ticket_create_" + discordId, "Utwórz ticket")
                .addActionRow(TextInput.create("ticket_topic", "Temat ticketa", TextInputStyle.SHORT)
                        .setPlaceholder("Np. Problem z gildią, Bug, Pytanie...")
                        .setRequiredRange(5, 100)
                        .build())
                .addActionRow(TextInput.create("ticket_description", "Opis problemu", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Opisz szczegółowo swój problem...")
                        .setRequiredRange(20, 1000)
                        .build())
                .build();

        event.replyModal(modal).queue();
    }

    /**
     * Obsługuje utworzenie ticketa z modalu.
     */
    public void handleTicketCreateModal(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        if (!modalId.startsWith("ticket_create_")) return;

        String discordId = modalId.substring("ticket_create_".length());
        String topic = event.getValue("ticket_topic").getAsString();
        String description = event.getValue("ticket_description").getAsString();

        event.deferReply(true).queue();

        try {
            Guild guild = plugin.getDiscordManager().getGuild();
            if (guild == null) {
                event.getHook().editOriginal("❌ Błąd: Nie znaleziono serwera Discord.").queue();
                return;
            }

            long categoryId = plugin.getConfig().getLong("discord.channels.tickets-category");
            long staffRoleId = plugin.getConfig().getLong("discord.roles.staff");

            // Pobierz nick Minecraft
            String mcNick = getMcNick(discordId);
            String baseName = mcNick != null ? mcNick.toLowerCase() : event.getUser().getName().toLowerCase();
            String channelName = "ticket-" + baseName.replaceAll("[^a-z0-9-]", "-");
            if (channelName.length() > 30) {
                channelName = channelName.substring(0, 30);
            }

            // Utwórz kanał - UŻYJ setParent() zamiast setCategory()
            // UWAGA: W JDA 5.x NIE MA Permission.MESSAGE_READ - tylko VIEW_CHANNEL
            guild.createTextChannel(channelName)
                    .setParent(guild.getCategoryById(categoryId))
                    .setTopic("Ticket: " + topic + " | " + event.getUser().getAsTag())
                    .addPermissionOverride(event.getMember(),
                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
                                    Permission.MESSAGE_HISTORY, Permission.MESSAGE_ADD_REACTION),
                            EnumSet.noneOf(Permission.class))
                    .addPermissionOverride(guild.getRoleById(staffRoleId),
                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
                                    Permission.MESSAGE_HISTORY, Permission.MESSAGE_MANAGE,
                                    Permission.MESSAGE_ADD_REACTION),
                            EnumSet.noneOf(Permission.class))
                    .addPermissionOverride(guild.getPublicRole(),
                            EnumSet.noneOf(Permission.class),
                            EnumSet.of(Permission.VIEW_CHANNEL))
                    .queue(channel -> {
                        // Zapisz ticket w bazie
                        saveTicket(channel.getId(), discordId, topic, description);

                        // Wyślij wiadomość powitalną
                        EmbedBuilder embed = new EmbedBuilder()
                                .setTitle("🎫 Ticket utworzony")
                                .setDescription("**Temat:** " + topic + "\n\n" +
                                        "**Opis:** " + description + "\n\n" +
                                        "Witaj <@" + discordId + ">! Staff odpisze tak szybko jak to możliwe.")
                                .addField("Informacje",
                                        "• Zamknij ticket przyciskiem 🔒\n" +
                                                "• Nie spamuj\n" +
                                                "• Bądź cierpliwy", false)
                                .setColor(Color.BLUE)
                                .setFooter("ID: " + channel.getId())
                                .setTimestamp(Instant.now());

                        Button closeButton = Button.danger("ticket_close_" + discordId, "🔒 Zamknij ticket");

                        channel.sendMessageEmbeds(embed.build())
                                .addActionRow(closeButton)
                                .mention(event.getMember())
                                .queue();

                        // Potwierdzenie dla użytkownika
                        event.getHook().editOriginal("✅ Ticket utworzony: " + channel.getAsMention()).queue();

                        // Powiadomienie w konsoli
                        plugin.getLogger().info("Nowy ticket: " + channel.getName() +
                                " | Użytkownik: " + event.getUser().getAsTag() +
                                " | MC: " + (mcNick != null ? mcNick : "N/A") +
                                " | Temat: " + topic);
                    }, error -> {
                        event.getHook().editOriginal("❌ Nie udało się utworzyć ticketa. Spróbuj ponownie.").queue();
                        plugin.getLogger().severe("Błąd tworzenia ticketa: " + error.getMessage());
                    });

        } catch (Exception e) {
            plugin.getLogger().severe("Błąd tworzenia ticketa: " + e.getMessage());
            e.printStackTrace();
            event.getHook().editOriginal("❌ Wystąpił błąd podczas tworzenia ticketa.").queue();
        }
    }

    /**
     * Obsługuje zamknięcie ticketa.
     */
    public void handleCloseTicket(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith("ticket_close_")) return;

        String discordId = buttonId.substring("ticket_close_".length());
        TextChannel channel = event.getChannel().asTextChannel();

        // Potwierdzenie
        EmbedBuilder confirmEmbed = new EmbedBuilder()
                .setTitle("🔒 Zamknąć ticket?")
                .setDescription("Czy na pewno chcesz zamknąć ten ticket?")
                .setColor(Color.ORANGE)
                .setFooter("Masz 30 sekund na potwierdzenie")
                .setTimestamp(Instant.now());

        Button confirmButton = Button.danger("ticket_confirm_close_" + discordId, "✅ Tak, zamknij");
        Button cancelButton = Button.secondary("ticket_cancel_close", "❌ Anuluj");

        event.replyEmbeds(confirmEmbed.build())
                .addActionRow(confirmButton, cancelButton)
                .setEphemeral(true)
                .queue();
    }

    /**
     * Potwierdza zamknięcie ticketa.
     */
    public void handleConfirmClose(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith("ticket_confirm_close_")) return;

        TextChannel channel = event.getChannel().asTextChannel();

        // Zamknij kanał
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔒 Ticket zamknięty")
                .setDescription("Ten ticket został zamknięty.")
                .setColor(Color.RED)
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();

        // Usuń kanał po 5 sekundach
        channel.delete().queueAfter(5, TimeUnit.SECONDS);

        // Usuń z bazy
        deleteTicket(channel.getId());

        event.reply("✅ Ticket został zamknięty.").setEphemeral(true).queue();
    }

    /**
     * Anuluje zamknięcie ticketa.
     */
    public void handleCancelClose(ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        event.reply("🚫 Zamknięcie ticketa zostało anulowane.").setEphemeral(true).queue();
    }

    // === BAZA DANYCH ===

    private void saveTicket(String channelId, String discordId, String topic, String description) {
        try (Connection conn = plugin.getDiscordDatabase().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO discord_tickets (channel_id, discord_id, topic, description, created_at, status) VALUES (?, ?, ?, ?, ?, ?)");
            ps.setString(1, channelId);
            ps.setString(2, discordId);
            ps.setString(3, topic);
            ps.setString(4, description);
            ps.setLong(5, System.currentTimeMillis());
            ps.setString(6, "open");
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd zapisu ticketa: " + e.getMessage());
        }
    }

    private void deleteTicket(String channelId) {
        try (Connection conn = plugin.getDiscordDatabase().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE discord_tickets SET status = ?, closed_at = ? WHERE channel_id = ?");
            ps.setString(1, "closed");
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, channelId);
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd usuwania ticketa: " + e.getMessage());
        }
    }

    private boolean hasOpenTicket(String discordId) {
        try (Connection conn = plugin.getDiscordDatabase().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM discord_tickets WHERE discord_id = ? AND status = ?");
            ps.setString(1, discordId);
            ps.setString(2, "open");
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String getMcNick(String discordId) {
        try (Connection conn = plugin.getDiscordDatabase().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT mc_nick FROM discord_linked WHERE discord_id = ?");
            ps.setString(1, discordId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("mc_nick") : null;
        } catch (Exception e) {
            return null;
        }
    }
}