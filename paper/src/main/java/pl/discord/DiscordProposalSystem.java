package pl.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Random;

public class DiscordProposalSystem {
    private final SectorSystemPlugin plugin;

    public DiscordProposalSystem(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    public void createProposal(String discordId, String discordName, String content, long channelId) {
        String mcNick = getMcNick(discordId);
        String proposalId = "PROP-" + (1000 + new Random().nextInt(9000));

        try (Connection conn = plugin.getDiscordDatabase().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("INSERT INTO discord_proposals (id, author_discord_id, author_mc_nick, content, status, channel_id, message_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setString(1, proposalId);
            ps.setString(2, discordId);
            ps.setString(3, mcNick);
            ps.setString(4, content);
            ps.setString(5, "Nowa");
            ps.setString(6, String.valueOf(channelId));
            ps.setString(7, "");
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        EmbedBuilder embed = createEmbed(proposalId, mcNick, discordName, content, "Nowa", 0, 0);
        plugin.getDiscordManager().getJda().getTextChannelById(channelId).sendMessageEmbeds(embed.build())
                .addActionRow(Button.success("vote_" + proposalId + "_za", "✅ Za"), Button.danger("vote_" + proposalId + "_przeciw", "❌ Przeciw"))
                .queue(message -> {
                    try (Connection conn = plugin.getDiscordDatabase().getConnection()) {
                        PreparedStatement ps = conn.prepareStatement("UPDATE discord_proposals SET message_id = ? WHERE id = ?");
                        ps.setString(1, message.getId());
                        ps.setString(2, proposalId);
                        ps.executeUpdate();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    private EmbedBuilder createEmbed(String proposalId, String mcNick, String discordName, String content, String status, int za, int przeciw) {
        int total = za + przeciw;
        double zaPercent = total > 0 ? (za * 100.0 / total) : 0;
        int threshold = plugin.getConfig().getInt("discord.proposals.vote-threshold", 10);
        int approval = plugin.getConfig().getInt("discord.proposals.approval-percentage", 50);

        Color color = switch (status) {
            case "Nowa" -> Color.CYAN;
            case "W rozpatrzeniu" -> Color.ORANGE;
            case "Zaakceptowana" -> Color.GREEN;
            case "Odrzucona" -> Color.RED;
            default -> Color.GRAY;
        };

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) bar.append(i < (zaPercent / 10) ? "█" : "░");

        return new EmbedBuilder()
                .setTitle("📋 Nowa propozycja")
                .setDescription(content)
                .addField("Autor", "<@" + getAuthorId(proposalId) + "> • " + (mcNick != null ? mcNick : discordName), true)
                .addField("Status", "🔵 " + status, true)
                .addField("ID", proposalId, true)
                .addBlankField(false)
                .addField("Głosy", "Za: **" + za + "** | Przeciw: **" + przeciw + "** | Razem: **" + total + "**", false)
                .addField("Poparcie", String.format("%.0f%% | Próg: %d głosów i %d%% za", zaPercent, threshold, approval), false)
                .addField("Postęp", "```" + bar + "```", false)
                .setColor(color)
                .setTimestamp(Instant.now());
    }

    public void castVote(String proposalId, String discordUserId, String voteType, ButtonInteractionEvent event) {
        try (Connection conn = plugin.getDiscordDatabase().getConnection()) {
            PreparedStatement votePs = conn.prepareStatement("REPLACE INTO discord_proposal_votes (proposal_id, discord_user_id, vote_type) VALUES (?, ?, ?)");
            votePs.setString(1, proposalId);
            votePs.setString(2, discordUserId);
            votePs.setString(3, voteType);
            votePs.executeUpdate();

            int za = getVoteCount(conn, proposalId, "za");
            int przeciw = getVoteCount(conn, proposalId, "przeciw");

            PreparedStatement propPs = conn.prepareStatement("SELECT * FROM discord_proposals WHERE id = ?");
            propPs.setString(1, proposalId);
            ResultSet rs = propPs.executeQuery();
            if (rs.next()) {
                EmbedBuilder newEmbed = createEmbed(proposalId, rs.getString("author_mc_nick"), "", rs.getString("content"), rs.getString("status"), za, przeciw);
                plugin.getDiscordManager().getJda().getTextChannelById(rs.getString("channel_id"))
                        .editMessageEmbedsById(rs.getString("message_id"), newEmbed.build()).queue();
            }
            event.reply("✅ Twój głos (" + voteType.toUpperCase() + ") został zapisany!").setEphemeral(true).queue();
        } catch (Exception e) {
            event.reply("❌ Błąd podczas głosowania.").setEphemeral(true).queue();
        }
    }

    private int getVoteCount(Connection conn, String proposalId, String type) throws Exception {
        PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM discord_proposal_votes WHERE proposal_id = ? AND vote_type = ?");
        ps.setString(1, proposalId);
        ps.setString(2, type);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt(1) : 0;
    }

    private String getMcNick(String discordId) {
        try (Connection conn = plugin.getDiscordDatabase().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT mc_nick FROM discord_linked WHERE discord_id = ?");
            ps.setString(1, discordId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("mc_nick") : null;
        } catch (Exception e) { return null; }
    }

    private String getAuthorId(String proposalId) {
        try (Connection conn = plugin.getDiscordDatabase().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT author_discord_id FROM discord_proposals WHERE id = ?");
            ps.setString(1, proposalId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("author_discord_id") : "";
        } catch (Exception e) { return ""; }
    }
}