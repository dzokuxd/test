package pl.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import pl.gildie.model.Guild;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DiscordListener extends ListenerAdapter {
    private final SectorSystemPlugin plugin;

    public DiscordListener(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getGuild() == null) return;

        long proposalsChannel = plugin.getConfig().getLong("discord.channels.proposals");

        if (event.getChannel().getIdLong() == proposalsChannel) {
            handleProposal(event);
        }
    }

    private void handleProposal(MessageReceivedEvent event) {
        if (!plugin.getDiscordManager().isVerified(event.getAuthor().getId())) {
            event.getMessage().reply("❌ Musisz być zweryfikowany, aby zgłaszać propozycje.")
                    .queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS));
            event.getMessage().delete().queue(
                    success -> { },
                    error -> { }
            );
            return;
        }

        String content = event.getMessage().getContentRaw();
        if (content.length() < 10) {
            event.getMessage().reply("❌ Propozycja musi mieć co najmniej 10 znaków.")
                    .queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS));
            event.getMessage().delete().queue(
                    success -> { },
                    error -> { }
            );
            return;
        }

        new DiscordProposalSystem(plugin).createProposal(
                event.getAuthor().getId(),
                event.getAuthor().getName(),
                content,
                event.getChannel().getIdLong()
        );
        event.getMessage().delete().queue(
                success -> { },
                error -> { }
        );
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        // Obsługa przycisku weryfikacji
        if (buttonId.equals("verify_open_modal")) {
            handleVerifyButton(event);
            return;
        }

        // Obsługa przycisków głosowania
        if (buttonId.startsWith("vote_")) {
            String[] parts = buttonId.split("_");
            if (parts.length == 3) {
                new DiscordProposalSystem(plugin).castVote(parts[1], event.getUser().getId(), parts[2], event);
            }
            return;
        }

        // Obsługa przycisku odwołania od bana
        if (buttonId.equals("appeal_ban")) {
            new DiscordBanSystem(plugin).createAppealModal(event);
        }
        // Obsługa przycisku utworzenia ticketa
        if (buttonId.equals("create_ticket")) {
            new DiscordTicketSystem(plugin).handleCreateTicket(event);
            return;
        }

// Obsługa przycisku zamknięcia ticketa
        if (buttonId.startsWith("ticket_close_")) {
            new DiscordTicketSystem(plugin).handleCloseTicket(event);
            return;
        }

        if (buttonId.startsWith("ticket_confirm_close_")) {
            new DiscordTicketSystem(plugin).handleConfirmClose(event);
            return;
        }

        if (buttonId.equals("ticket_cancel_close")) {
            new DiscordTicketSystem(plugin).handleCancelClose(event);
            return;
        }
    }

    /**
     * Obsługuje kliknięcie przycisku "Wpisz kod weryfikacyjny"
     */
    private void handleVerifyButton(ButtonInteractionEvent event) {
        Modal modal = Modal.create("verify_code_modal", "Weryfikacja konta Minecraft")
                .addActionRow(TextInput.create("verify_code", "Kod weryfikacyjny", TextInputStyle.SHORT)
                        .setPlaceholder("Wpisz kod z gry (np. ABC123)")
                        .setRequiredRange(6, 10)
                        .setRequired(true)
                        .build())
                .build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();

        // Obsługa modalu weryfikacji
        if (modalId.equals("verify_code_modal")) {
            handleVerifyModal(event);
            return;
        }

        // Obsługa modalu odwołania od bana
        if (modalId.startsWith("ban_appeal_")) {
            new DiscordBanSystem(plugin).handleAppealSubmit(event);
        }
        if (modalId.startsWith("ticket_create_")) {
            new DiscordTicketSystem(plugin).handleTicketCreateModal(event);
            return;
        }
    }

    /**
     * Obsługuje wpisanie kodu w modalu weryfikacyjnym
     */
    private void handleVerifyModal(ModalInteractionEvent event) {
        String code = event.getValue("verify_code").getAsString().trim().toUpperCase();
        String discordId = event.getUser().getId();

        event.deferReply(true).queue();

        try (Connection conn = plugin.getDiscordDatabase().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT uuid, expires_at FROM discord_verifications WHERE code = ?");
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                event.getHook().editOriginal("❌ **Nieprawidłowy kod!**\n\nSprawdź czy kod jest poprawny i czy nie wygasł.\nWpisz `/discord` w grze aby wygenerować nowy kod.").queue();
                return;
            }

            long expiresAt = rs.getLong("expires_at");
            if (System.currentTimeMillis() > expiresAt) {
                try (PreparedStatement delPs = conn.prepareStatement("DELETE FROM discord_verifications WHERE code = ?")) {
                    delPs.setString(1, code);
                    delPs.executeUpdate();
                }
                event.getHook().editOriginal("⏰ **Kod wygasł!**\n\nTen kod nie jest już ważny. Wpisz `/discord` w grze aby wygenerować nowy kod.").queue();
                return;
            }

            String uuid = rs.getString("uuid");

            // Sprawdź czy ten Discord ID jest już powiązany z innym kontem
            try (PreparedStatement checkPs = conn.prepareStatement("SELECT uuid FROM discord_linked WHERE discord_id = ?")) {
                checkPs.setString(1, discordId);
                ResultSet checkRs = checkPs.executeQuery();
                if (checkRs.next()) {
                    event.getHook().editOriginal("⚠️ **To konto Discord jest już powiązane z innym kontem Minecraft!**\n\nJeśli chcesz zmienić powiązanie, skontaktuj się z administracją.").queue();
                    return;
                }
            }

            // Pobierz nick gracza
            OfflinePlayer mcPlayer = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            String nick = mcPlayer.getName() != null ? mcPlayer.getName() : "Nieznany";

            // Zapisz powiązanie
            try (PreparedStatement linkPs = conn.prepareStatement(
                    "REPLACE INTO discord_linked (uuid, discord_id, mc_nick, linked_at) VALUES (?, ?, ?, ?)")) {
                linkPs.setString(1, uuid);
                linkPs.setString(2, discordId);
                linkPs.setString(3, nick);
                linkPs.setLong(4, System.currentTimeMillis());
                linkPs.executeUpdate();
            }

            // Usuń kod
            try (PreparedStatement delPs = conn.prepareStatement("DELETE FROM discord_verifications WHERE code = ?")) {
                delPs.setString(1, code);
                delPs.executeUpdate();
            }

            // Nadaj rolę Zweryfikowany
            plugin.getDiscordManager().giveRole(discordId, plugin.getConfig().getLong("discord.roles.verified"));

            // Sprawdź rangę Lidera
            if (mcPlayer.isOnline()) {
                checkGuildLeaderRole(mcPlayer.getPlayer(), discordId);
            }

            // Wyślij potwierdzenie
            EmbedBuilder successEmbed = new EmbedBuilder()
                    .setTitle("✅ Weryfikacja zakończona pomyślnie!")
                    .setDescription("**Konto Minecraft:** " + nick + "\n" +
                            "**Konto Discord:** <@" + discordId + ">\n\n")
                    .setColor(Color.GREEN)
                    .setThumbnail("https://minotar.net/helm/" + uuid + "/100.png")
                    .setTimestamp(Instant.now());

            event.getHook().editOriginalEmbeds(successEmbed.build()).queue();

            // Wyślij wiadomość na kanale (opcjonalnie)
            event.getChannel().sendMessage("🎉 <@" + discordId + "> zweryfikował konto jako **" + nick + "**!")
                    .queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS));

        } catch (Exception e) {
            plugin.getLogger().severe("Błąd weryfikacji: " + e.getMessage());
            e.printStackTrace();
            event.getHook().editOriginal("❌ Wystąpił błąd podczas weryfikacji. Spróbuj ponownie za chwilę.").queue();
        }
    }

    /**
     * Sprawdza i nadaje/zabiera rangę Lidera na podstawie GildieModule.
     */
    public void checkGuildLeaderRole(Player player, String discordId) {
        try {
            var gildieModule = plugin.getGildieModule();
            if (gildieModule == null || gildieModule.getGuildManager() == null) return;

            Guild guild = gildieModule.getGuildManager().getGuildByPlayer(player.getUniqueId());
            int requirement = plugin.getConfig().getInt("discord.guilds.leader-member-requirement", 10);
            long leaderRoleId = plugin.getConfig().getLong("discord.roles.leader");

            if (guild != null) {
                boolean isLeader = guild.getOwner() != null && guild.getOwner().equals(player.getUniqueId());
                int memberCount = guild.getMembers() != null ? guild.getMembers().size() : 0;

                if (isLeader && memberCount >= requirement) {
                    plugin.getDiscordManager().giveRole(discordId, leaderRoleId);
                } else {
                    plugin.getDiscordManager().removeRole(discordId, leaderRoleId);
                }
            } else {
                plugin.getDiscordManager().removeRole(discordId, leaderRoleId);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd sprawdzania rangi lidera gildii: " + e.getMessage());
        }
    }
}