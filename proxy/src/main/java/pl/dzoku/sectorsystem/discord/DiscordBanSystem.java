package pl.dzoku.sectorsystem.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import pl.dzoku.sectorsystem.SectorProxyPlugin;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.EnumSet;

public class DiscordBanSystem {
    private final SectorProxyPlugin plugin;
    private final DiscordDatabase db;

    public DiscordBanSystem(SectorProxyPlugin plugin) {
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
        String screenshot = event.getValue("appeal_screenshot") != null ?
                event.getValue("appeal_screenshot").getAsString() : null;

        try (Connection conn = db.getConnection()) {
            String mcNick = getMcNick(discordId);
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, mc_nick FROM discord_bans WHERE (discord_id = ? OR mc_nick = ?) AND unbanned = FALSE ORDER BY banned_at DESC LIMIT 1");
            ps.setString(1, discordId);
            ps.setString(2, mcNick != null ? mcNick : "");
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                event.reply("❌ Nie znaleziono aktywnego bana.").setEphemeral(true).queue();
                return;
            }

            String banUuid = rs.getString("uuid");
            String finalMcNick = rs.getString("mc_nick");

            // Zapisz odwołanie w bazie
            PreparedStatement appealPs = conn.prepareStatement(
                    "INSERT INTO discord_ban_appeals (ban_uuid, discord_id, message, screenshot_url, submitted_at) VALUES (?, ?, ?, ?, ?)");
            appealPs.setString(1, banUuid);
            appealPs.setString(2, discordId);
            appealPs.setString(3, reason);
            appealPs.setString(4, screenshot);
            appealPs.setLong(5, System.currentTimeMillis());
            appealPs.executeUpdate();

            // Utwórz kanał ticket z przyciskami
            createAppealTicket(event, discordId, finalMcNick, reason, screenshot);

            plugin.getLogger().info("Nowe odwołanie od bana od: " + event.getUser().getAsTag() +
                    " (MC: " + (finalMcNick != null ? finalMcNick : "Nieznany") + ") | Powód: " + reason);

            event.reply("✅ Odwołanie złożone. Poczekaj na odpowiedź administracji.\n" +
                    "Staff skontaktuje się z Tobą na nowo utworzonym kanale ticket.").setEphemeral(true).queue();

        } catch (Exception e) {
            plugin.getLogger().severe("Błąd składania odwołania: " + e.getMessage());
            event.reply("❌ Błąd podczas składania odwołania.").setEphemeral(true).queue();
        }
    }

    /**
     * Tworzy kanał ticket dla odwołania od bana z przyciskami Zaakceptuj/Anuluj
     */
    private void createAppealTicket(ModalInteractionEvent event, String discordId, String mcNick, String reason, String screenshot) {
        try {
            Guild guild = plugin.getDiscordManager().getGuild();
            if (guild == null) {
                plugin.getLogger().severe("Nie znaleziono serwera Discord!");
                return;
            }

            long ticketsCategoryId = plugin.getCfg().getDiscordTicketsCategory();
            if (ticketsCategoryId == 0) {
                plugin.getLogger().warning("Nie skonfigurowano kategorii ticketów w configu!");
                return;
            }

            Category category = guild.getCategoryById(ticketsCategoryId);
            if (category == null) {
                plugin.getLogger().severe("Nie znaleziono kategorii ticketów o ID: " + ticketsCategoryId);
                return;
            }

            String safeNick = mcNick != null ? mcNick : event.getUser().getName();
            String channelName = "odwolanie-" + safeNick.toLowerCase().replaceAll("[^a-z0-9-]", "-");
            if (channelName.length() > 30) {
                channelName = channelName.substring(0, 30);
            }

            // Sprawdź czy kanał już istnieje
            TextChannel existingChannel = guild.getTextChannelsByName(channelName, true).stream()
                    .filter(ch -> ch.getParentCategory() != null && ch.getParentCategory().getIdLong() == ticketsCategoryId)
                    .findFirst().orElse(null);

            if (existingChannel != null) {
                plugin.getLogger().info("Kanał odwołania już istnieje: " + channelName);
                existingChannel.sendMessage("⚠️ Użytkownik <@" + discordId + "> złożył kolejne odwołanie od bana.").queue();
                return;
            }

            long staffRoleId = plugin.getCfg().getDiscordStaffRoleId();

            guild.createTextChannel(channelName)
                    .setParent(category)
                    .setTopic("Odwołanie od bana: " + safeNick)
                    .addPermissionOverride(event.getMember(),
                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
                                    Permission.MESSAGE_HISTORY, Permission.MESSAGE_ADD_REACTION),
                            EnumSet.noneOf(Permission.class))
                    .addPermissionOverride(guild.getPublicRole(),
                            EnumSet.noneOf(Permission.class),
                            EnumSet.of(Permission.VIEW_CHANNEL))
                    .queue(channel -> {
                        // Embed z informacjami o odwołaniu
                        EmbedBuilder embed = new EmbedBuilder()
                                .setTitle(" Odwołanie od bana")
                                .setDescription("**Gracz:** " + safeNick + " (<@" + discordId + ">)\n" +
                                        "**Discord:** " + event.getUser().getAsTag())
                                .addField("📝 Treść odwołania", reason, false)
                                .setColor(Color.ORANGE)
                                .setFooter("ID odwołania: " + System.currentTimeMillis())
                                .setTimestamp(Instant.now());

                        if (screenshot != null && !screenshot.trim().isEmpty()) {
                            embed.addField("📷 Screenshot", screenshot, false);
                        }

                        // ✅ PRZYCISKI DLA STAFFU
                        Button acceptButton = Button.success("appeal_accept_" + discordId, "✅ Zaakceptuj (Odbanuj)");
                        Button denyButton = Button.danger("appeal_deny_" + discordId, " Anuluj (Odrzuć)");

                        channel.sendMessageEmbeds(embed.build())
                                .addActionRow(acceptButton, denyButton)
                                .queue();

                        // Ping staff jeśli skonfigurowany
                        if (staffRoleId > 0) {
                            channel.sendMessage("<@&" + staffRoleId + "> Nowe odwołanie do rozpatrzenia.").queue();
                        }

                        plugin.getLogger().info("Utworzono kanał odwołania: " + channel.getName() +
                                " dla gracza " + safeNick);
                    }, error -> {
                        plugin.getLogger().severe("Błąd tworzenia kanału odwołania: " + error.getMessage());
                    });

        } catch (Exception e) {
            plugin.getLogger().severe("Błąd tworzenia ticketa odwołania: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Obsługa przycisku "Zaakceptuj" - odbanowuje gracza i usuwa kanał
     */
    public void handleAppealAccept(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith("appeal_accept_")) return;

        String discordId = buttonId.substring("appeal_accept_".length());
        TextChannel channel = event.getChannel().asTextChannel();

        event.deferReply(true).queue();

        try (Connection conn = db.getConnection()) {
            // 1. Znajdź bana po discord_id LUB mc_nick
            String mcNick = getMcNick(discordId);
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, mc_nick FROM discord_bans WHERE (discord_id = ? OR mc_nick = ?) AND unbanned = FALSE ORDER BY banned_at DESC LIMIT 1");
            ps.setString(1, discordId);
            ps.setString(2, mcNick != null ? mcNick : "");
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                event.getHook().editOriginal("❌ Nie znaleziono aktywnego bana do odbanowania.").queue();
                return;
            }

            String banUuid = rs.getString("uuid");
            String finalMcNick = rs.getString("mc_nick");

            // 2. Odbanuj gracza (ustaw unbanned = TRUE)
            PreparedStatement unbanPs = conn.prepareStatement(
                    "UPDATE discord_bans SET unbanned = TRUE WHERE uuid = ?");
            unbanPs.setString(1, banUuid);
            unbanPs.executeUpdate();

            plugin.getLogger().info("✅ Gracz " + (finalMcNick != null ? finalMcNick : "nieznany") +
                    " (UUID: " + banUuid + ") został odbanowany przez " + event.getUser().getAsTag());

            // 3. Wyślij DM do gracza o zaakceptowaniu
            sendDmToUser(discordId, true, finalMcNick);

            // 4. Wyślij potwierdzenie na kanale
            EmbedBuilder confirmEmbed = new EmbedBuilder()
                    .setTitle("✅ Odwołanie zaakceptowane")
                    .setDescription("Gracz **" + (finalMcNick != null ? finalMcNick : "nieznany") + "** został odbanowany.\n" +
                            "Zaakceptował: " + event.getUser().getAsMention())
                    .setColor(Color.GREEN)
                    .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(confirmEmbed.build()).queue();

            // 5. Usuń kanał po 5 sekundach
            channel.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS);

            event.getHook().editOriginal("✅ Gracz został odbanowany. Kanał zostanie usunięty za 5 sekund.").queue();

        } catch (Exception e) {
            plugin.getLogger().severe("Błąd odbanowywania: " + e.getMessage());
            event.getHook().editOriginal("❌ Wystąpił błąd podczas odbanowywania.").queue();
        }
    }

    /**
     * Obsługa przycisku "Anuluj" - odrzuca odwołanie i usuwa kanał
     */
    public void handleAppealDeny(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith("appeal_deny_")) return;

        String discordId = buttonId.substring("appeal_deny_".length());
        TextChannel channel = event.getChannel().asTextChannel();

        event.deferReply(true).queue();

        try {
            String mcNick = getMcNick(discordId);

            // 1. Wyślij DM do gracza o odrzuceniu
            sendDmToUser(discordId, false, mcNick);

            // 2. Wyślij potwierdzenie na kanale
            EmbedBuilder denyEmbed = new EmbedBuilder()
                    .setTitle(" Odwołanie odrzucone")
                    .setDescription("Odwołanie gracza **" + (mcNick != null ? mcNick : "nieznany") + "** zostało odrzucone.\n" +
                            "Odrzucił: " + event.getUser().getAsMention())
                    .setColor(Color.RED)
                    .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(denyEmbed.build()).queue();

            // 3. Usuń kanał po 5 sekundach
            channel.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS);

            plugin.getLogger().info("❌ Odwołanie odrzucone dla gracza " + (mcNick != null ? mcNick : "nieznany") +
                    " przez " + event.getUser().getAsTag());

            event.getHook().editOriginal(" Odwołanie odrzucone. Gracz został powiadomiony przez DM. Kanał zostanie usunięty za 5 sekund.").queue();

        } catch (Exception e) {
            plugin.getLogger().severe("Błąd odrzucania odwołania: " + e.getMessage());
            event.getHook().editOriginal("❌ Wystąpił błąd podczas odrzucania odwołania.").queue();
        }
    }

    /**
     * Wysyła DM do gracza z informacją o wyniku odwołania
     */
    private void sendDmToUser(String discordId, boolean accepted, String mcNick) {
        try {
            Guild guild = plugin.getDiscordManager().getGuild();
            if (guild == null) return;

            User user = guild.getJDA().retrieveUserById(discordId).complete();
            if (user == null) {
                plugin.getLogger().warning("Nie udało się pobrać użytkownika Discord: " + discordId);
                return;
            }

            EmbedBuilder dmEmbed = new EmbedBuilder();

            if (accepted) {
                dmEmbed.setTitle("✅ Twoje odwołanie od bana zostało zaakceptowane!")
                        .setDescription("Gratulacje! Twój ban na serwerze Minecraft został zdjęty.\n\n" +
                                "**Nick Minecraft:** " + (mcNick != null ? mcNick : "Nieznany") + "\n" +
                                "**Status:** Odbanowany\n\n" +
                                "Możesz teraz dołączyć do serwera. Miłej gry! 🎮")
                        .setColor(Color.GREEN)
                        .setFooter("EasyAge.pl")
                        .setTimestamp(Instant.now());

                user.openPrivateChannel().queue(dmChannel ->
                        dmChannel.sendMessageEmbeds(dmEmbed.build()).queue(
                                success -> plugin.getLogger().info("Wysłano DM o zaakceptowaniu do: " + user.getAsTag()),
                                error -> plugin.getLogger().warning("Nie udało się wysłać DM do: " + user.getAsTag() + " (może mieć zablokowane DM)")
                        ));
            } else {
                dmEmbed.setTitle("❌ Twoje odwołanie od bana zostało odrzucone")
                        .setDescription("Niestety, Twoje odwołanie od bana nie zostało zaakceptowane przez administrację.\n\n" +
                                "**Nick Minecraft:** " + (mcNick != null ? mcNick : "Nieznany") + "\n" +
                                "**Status:** Ban pozostaje aktywny\n\n" +
                                "Jeśli uważasz, że to pomyłka, możesz spróbować ponownie za jakiś czas lub skontaktować się z administracją.")
                        .setColor(Color.RED)
                        .setFooter("EasyAge.pl")
                        .setTimestamp(Instant.now());

                user.openPrivateChannel().queue(dmChannel ->
                        dmChannel.sendMessageEmbeds(dmEmbed.build()).queue(
                                success -> plugin.getLogger().info("Wysłano DM o odrzuceniu do: " + user.getAsTag()),
                                error -> plugin.getLogger().warning("Nie udało się wysłać DM do: " + user.getAsTag() + " (może mieć zablokowane DM)")
                        ));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd wysyłania DM: " + e.getMessage());
        }
    }

    public boolean isBanned(String discordId) {
        try (Connection conn = db.getConnection()) {
            String mcNick = getMcNick(discordId);
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT unbanned, expires_at FROM discord_bans WHERE (discord_id = ? OR mc_nick = ?) ORDER BY banned_at DESC LIMIT 1");
            ps.setString(1, discordId);
            ps.setString(2, mcNick != null ? mcNick : "");
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                boolean unbanned = rs.getBoolean("unbanned");
                long expiresAt = rs.getLong("expires_at");
                return !unbanned && (expiresAt == 0 || expiresAt > System.currentTimeMillis());
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String getMcNick(String discordId) {
        try (Connection conn = db.getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT mc_nick FROM discord_linked WHERE discord_id = ?");
            ps.setString(1, discordId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("mc_nick") : null;
        } catch (Exception e) {
            return null;
        }
    }
}