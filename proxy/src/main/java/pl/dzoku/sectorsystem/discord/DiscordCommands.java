package pl.dzoku.sectorsystem.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import pl.dzoku.sectorsystem.SectorProxyPlugin;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DiscordCommands extends ListenerAdapter {
    private final SectorProxyPlugin plugin;

    public DiscordCommands(SectorProxyPlugin plugin) {
        this.plugin = plugin;
        registerCommands();
    }

    private void registerCommands() {
        if (plugin.getDiscordManager().getJda() != null) {
            plugin.getDiscordManager().getJda().updateCommands().addCommands(
                    Commands.slash("gracz", "Pokazuje statystyki gracza")
                            .addOption(OptionType.STRING, "nick", "Nick gracza", true)
            ).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("gracz")) return;
        event.deferReply().setEphemeral(true).queue();
        String nick = event.getOption("nick").getAsString();

        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getMysql().getConnection()) {
                PreparedStatement ps1 = conn.prepareStatement("SELECT uuid, premium FROM auth_players WHERE username = ?");
                ps1.setString(1, nick);
                ResultSet rs1 = ps1.executeQuery();
                if (!rs1.next()) {
                    event.getHook().editOriginal("❌ Nie znaleziono gracza: " + nick).queue();
                    return;
                }
                UUID uuid = UUID.fromString(rs1.getString("uuid"));
                boolean isPremium = rs1.getBoolean("premium");

                PreparedStatement ps2 = conn.prepareStatement("SELECT discord_id FROM discord_linked WHERE uuid = ?");
                ps2.setString(1, uuid.toString());
                ResultSet rs2 = ps2.executeQuery();
                String discordId = rs2.next() ? rs2.getString("discord_id") : "Niezweryfikowany";

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("📊 Statystyki gracza: " + nick)
                        .setThumbnail("https://minotar.net/helm/" + uuid + "/100.png")
                        .setColor(isPremium ? Color.GREEN : Color.GRAY)
                        .addField("📡 Status", isPremium ? "🟢 Premium" : "⚪ Non-Premium", true)
                        .addField(" Discord", discordId.equals("Niezweryfikowany") ? "Niezweryfikowany" : "<@" + discordId + ">", true)
                        .setFooter("SectorSystem Proxy");

                event.getHook().editOriginalEmbeds(embed.build()).queue();
            } catch (Exception e) {
                plugin.getLogger().severe("Błąd zapytania /gracz: " + e.getMessage());
                event.getHook().editOriginal("❌ Błąd podczas pobierania danych z bazy.").queue();
            }
        });
    }
}