package pl.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DiscordCommands extends ListenerAdapter {
    private final SectorSystemPlugin plugin;
    private final DiscordDatabase db;

    public DiscordCommands(SectorSystemPlugin plugin) {
        this.plugin = plugin;
        this.db = plugin.getDiscordDatabase();
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

        // 1. NATYCHMIASTOWA ODPOWIEDŹ (zapobiega błędowi 3 sekund)
        event.deferReply().setEphemeral(true).queue();

        String nick = event.getOption("nick").getAsString();

        // 2. Wykonaj ciężkie operacje (Bukkit + DB) asynchronicznie, aby nie blokować JDA
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(nick);

            if (!player.hasPlayedBefore() && !player.isOnline()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        event.getHook().editOriginal("❌ Nie znaleziono gracza: " + nick).queue()
                );
                return;
            }

            try (Connection conn = db.getConnection()) {
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM discord_linked WHERE uuid = ?");
                ps.setString(1, player.getUniqueId().toString());
                ResultSet rs = ps.executeQuery();
                String discordId = rs.next() ? rs.getString("discord_id") : "Niezweryfikowany";

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("📊 Statystyki gracza: " + nick)
                        .setThumbnail("https://minotar.net/helm/" + player.getUniqueId() + "/100.png")
                        .setColor(player.isOnline() ? java.awt.Color.GREEN : java.awt.Color.GRAY)
                        .addField("📡 Status", player.isOnline() ? "🟢 Online" : "🔴 Offline", true)
                        .addField("🔗 Discord", discordId.equals("Niezweryfikowany") ? "Niezweryfikowany" : "<@" + discordId + ">", true)
                        .addField("📅 Pierwsze logowanie", new java.text.SimpleDateFormat("dd.MM.yyyy").format(new java.util.Date(player.getFirstPlayed())), false)
                        .addField("📅 Ostatnie logowanie", new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(player.getLastPlayed())), false);

                // Integracja z GildieModule
                try {
                    var gildieModule = plugin.getGildieModule();
                    if (gildieModule != null && player.isOnline()) {
                        pl.gildie.model.Guild guild = gildieModule.getGuildManager().getGuildByPlayer(player.getUniqueId());
                        if (guild != null) {
                            boolean isLeader = guild.getOwner() != null && guild.getOwner().equals(player.getUniqueId());
                            int members = guild.getMembers() != null ? guild.getMembers().size() : 0;
                            embed.addField("🏰 Gildia", (isLeader ? "👑 Lider" : "Członek") + " (" + members + " osób)", true);
                        } else {
                            embed.addField("🏰 Gildia", "Brak", true);
                        }
                    }
                } catch (Exception e) {
                    embed.addField("🏰 Gildia", "Błąd pobierania", true);
                }

                // 3. Wyślij odpowiedź z powrotem do głównego wątku (bezpieczne dla JDA/Bukkit)
                EmbedBuilder finalEmbed = embed;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    event.getHook().editOriginalEmbeds(finalEmbed.build()).queue();
                });

            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    event.getHook().editOriginal("❌ Błąd podczas pobierania danych z bazy.").queue();
                });
                e.printStackTrace();
            }
        });
    }
}
