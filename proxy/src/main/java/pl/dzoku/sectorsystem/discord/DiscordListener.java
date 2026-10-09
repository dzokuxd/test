package pl.dzoku.sectorsystem.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import pl.dzoku.sectorsystem.SectorProxyPlugin;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DiscordListener extends ListenerAdapter {
    private final SectorProxyPlugin plugin;

    public DiscordListener(SectorProxyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getGuild() == null) return;
        long proposalsChannel = plugin.getCfg().getDiscordProposalsChannel();
        if (event.getChannel().getIdLong() == proposalsChannel) {
            handleProposal(event);
        }
    }

    private void handleProposal(MessageReceivedEvent event) {
        if (!plugin.getDiscordManager().isVerified(event.getAuthor().getId())) {
            event.getMessage().reply("❌ Musisz być zweryfikowany, aby zgłaszać propozycje.")
                    .queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS));
            event.getMessage().delete().queue(success -> {}, error -> {});
            return;
        }
        String content = event.getMessage().getContentRaw();
        if (content.length() < 10) {
            event.getMessage().reply("❌ Propozycja musi mieć co najmniej 10 znaków.")
                    .queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS));
            event.getMessage().delete().queue(success -> {}, error -> {});
            return;
        }
        new DiscordProposalSystem(plugin).createProposal(
                event.getAuthor().getId(),
                event.getAuthor().getName(),
                content,
                event.getChannel().getIdLong()
        );
        event.getMessage().delete().queue(success -> {}, error -> {});
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (buttonId.startsWith("appeal_accept_")) {
            new DiscordBanSystem(plugin).handleAppealAccept(event);
            return;
        }

        if (buttonId.startsWith("appeal_deny_")) {
            new DiscordBanSystem(plugin).handleAppealDeny(event);
            return;
        }
        if (buttonId.equals("verify_open_modal")) {
            handleVerifyButton(event);
            return;
        }
        if (buttonId.startsWith("vote_")) {
            String[] parts = buttonId.split("_");
            if (parts.length == 3) {
                new DiscordProposalSystem(plugin).castVote(parts[1], event.getUser().getId(), parts[2], event);
            }
            return;
        }
        if (buttonId.equals("appeal_ban")) {
            new DiscordBanSystem(plugin).createAppealModal(event);
            return;
        }
        if (buttonId.equals("create_ticket")) {
            new DiscordTicketSystem(plugin).handleCreateTicket(event);
            return;
        }
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

        if (modalId.equals("verify_code_modal")) {
            handleVerifyModal(event);
            return;
        }

        if (modalId.startsWith("ban_appeal_")) {
            new DiscordBanSystem(plugin).handleAppealSubmit(event);
            return;
        }

        if (modalId.startsWith("ticket_create_")) {
            new DiscordTicketSystem(plugin).handleTicketCreateModal(event);
            return;
        }
    }

    private void handleVerifyModal(ModalInteractionEvent event) {
        String code = event.getValue("verify_code").getAsString().trim().toUpperCase();
        String discordId = event.getUser().getId();
        event.deferReply(true).queue();

        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getMysql().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("SELECT uuid, expires_at FROM discord_verifications WHERE code = ?");
                ps.setString(1, code);
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    event.getHook().editOriginal("❌ **Nieprawidłowy kod!**\n\nWpisz `/discord` w grze aby wygenerować nowy kod.").queue();
                    return;
                }

                long expiresAt = rs.getLong("expires_at");
                if (System.currentTimeMillis() > expiresAt) {
                    try (PreparedStatement delPs = conn.prepareStatement("DELETE FROM discord_verifications WHERE code = ?")) {
                        delPs.setString(1, code);
                        delPs.executeUpdate();
                    }
                    event.getHook().editOriginal("⏰ **Kod wygasł!**\n\nWpisz `/discord` w grze aby wygenerować nowy kod.").queue();
                    return;
                }

                String uuid = rs.getString("uuid");

                // Sprawdź czy Discord ID już powiązany
                try (PreparedStatement checkPs = conn.prepareStatement("SELECT mc_nick FROM discord_linked WHERE discord_id = ?")) {
                    checkPs.setString(1, discordId);
                    ResultSet checkRs = checkPs.executeQuery();
                    if (checkRs.next()) {
                        event.getHook().editOriginal("⚠️ **To konto Discord jest już powiązane z kontem Minecraft: " + checkRs.getString("mc_nick") + "**\n\nNie możesz się ponownie zweryfikować.").queue();
                        return;
                    }
                }

                // Pobierz nick z auth_players
                String nick = "Nieznany";
                try (PreparedStatement nickPs = conn.prepareStatement("SELECT username FROM auth_players WHERE uuid = ?")) {
                    nickPs.setString(1, uuid);
                    ResultSet nickRs = nickPs.executeQuery();
                    if (nickRs.next()) nick = nickRs.getString("username");
                }

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
                plugin.getDiscordManager().giveRole(discordId, plugin.getDiscordManager().getVerifiedRoleId());

                // Zmień nick na DC
                changeDiscordNickname(discordId, nick);

                // Sprawdź rangę Lidera
                checkGuildLeaderRole(uuid, discordId, conn);

                EmbedBuilder successEmbed = new EmbedBuilder()
                        .setTitle("✅ Weryfikacja zakończona pomyślnie!")
                        .setDescription("**Konto Minecraft:** " + nick + "\n**Konto Discord:** <@" + discordId + ">\n\nTwój nick na Discordzie został zmieniony na: **" + nick + "**")
                        .setColor(Color.GREEN)
                        .setThumbnail("https://minotar.net/helm/" + uuid + "/100.png")
                        .setTimestamp(Instant.now());
                event.getHook().editOriginalEmbeds(successEmbed.build()).queue();

                event.getChannel().sendMessage("🎉 <@" + discordId + "> zweryfikował konto jako **" + nick + "**!")
                        .queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS));
            } catch (Exception e) {
                plugin.getLogger().severe("Błąd weryfikacji: " + e.getMessage());
                event.getHook().editOriginal("❌ Wystąpił błąd podczas weryfikacji.").queue();
            }
        });
    }

    private void changeDiscordNickname(String discordId, String newNick) {
        try {
            Guild guild = plugin.getDiscordManager().getGuild();
            if (guild == null) return;
            Member member = guild.getMemberById(discordId);
            if (member == null) return;
            String finalNick = newNick.length() > 32 ? newNick.substring(0, 32) : newNick;
            member.modifyNickname(finalNick).queue(
                    success -> plugin.getLogger().info("Zmieniono nick Discord " + discordId + " na: " + finalNick),
                    error -> plugin.getLogger().warning("Nie udało się zmienić nicku Discord: " + error.getMessage())
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd zmiany nicku Discord: " + e.getMessage());
        }
    }

    private void checkGuildLeaderRole(String uuid, String discordId, Connection conn) {
        try {
            int requirement = plugin.getCfg().getDiscordLeaderRequirement();
            long leaderRoleId = plugin.getDiscordManager().getLeaderRoleId();
            if (leaderRoleId == 0) return;

            PreparedStatement ps = conn.prepareStatement("SELECT owner_uuid, members FROM guilds WHERE JSON_CONTAINS(members, ?)");
            ps.setString(1, "\"" + uuid + "\"");
            ResultSet rs = ps.executeQuery();

            boolean shouldHaveRole = false;
            if (rs.next()) {
                boolean isLeader = rs.getString("owner_uuid").equals(uuid);
                int memberCount = 0;
                String membersJson = rs.getString("members");
                if (membersJson != null && !membersJson.isEmpty()) {
                    try {
                        com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(membersJson).getAsJsonArray();
                        memberCount = arr.size();
                    } catch (Exception ignored) {}
                }
                if (isLeader && memberCount >= requirement) shouldHaveRole = true;
            }

            boolean hasRole = plugin.getDiscordManager().hasRole(discordId, leaderRoleId);
            if (shouldHaveRole && !hasRole) {
                plugin.getDiscordManager().giveRole(discordId, leaderRoleId);
            } else if (!shouldHaveRole && hasRole) {
                plugin.getDiscordManager().removeRole(discordId, leaderRoleId);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd sprawdzania rangi lidera: " + e.getMessage());
        }
    }
}