package pl.dzoku.sectorsystem.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import pl.dzoku.sectorsystem.SectorProxyPlugin;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class DiscordEmbedManager {
    private final SectorProxyPlugin plugin;

    public DiscordEmbedManager(SectorProxyPlugin plugin) { this.plugin = plugin; }

    public void createAllEmbeds() {
        createVerifyEmbed();
        createTicketPanel();
        createBanAppealPanel();
        createProposalInfoEmbed();
    }

    public void createVerifyEmbed() {
        Guild guild = plugin.getDiscordManager().getGuild(); if (guild == null) return;
        long channelId = plugin.getCfg().getDiscordVerifyChannel();
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) { plugin.getLogger().severe("Nie znaleziono kanału weryfikacji: " + channelId); return; }
        if (embedExists(channel, "Weryfikacja konta Minecraft")) { plugin.getLogger().info("✓ Embed weryfikacyjny już istnieje"); return; }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔐 Weryfikacja konta Minecraft")
                .setDescription("Aby zweryfikować swoje konto Minecraft z Discordem:\n\n" +
                        "1️⃣ Wejdź na serwer Minecraft\n" +
                        "2️⃣ Wpisz komendę `/discord`\n" +
                        "3️⃣ Skopiuj wygenerowany kod\n" +
                        "4️ Kliknij przycisk poniżej i wklej kod\n\n" +
                        "Po weryfikacji otrzymasz:\n" +
                        "✅ Dostęp do kanałów dla zweryfikowanych\n" +
                        "✅ System ticketów\n" +
                        "✅ Głosowanie w propozycjach\n" +
                        "✅ Rangę Lidera (gildia 10+ osób)\n" +
                        "✅ Nick na Discordzie taki jak w MC")
                .setColor(Color.BLUE)
                .setFooter("Kod ważny przez 5 minut • EasyAge.pl")
                .setTimestamp(Instant.now());
        Button btn = Button.primary("verify_open_modal", "🔑 Wpisz kod weryfikacyjny");
        channel.sendMessageEmbeds(embed.build()).addActionRow(btn).queue(
                s -> plugin.getLogger().info("✓ Embed weryfikacyjny utworzony"),
                e -> plugin.getLogger().severe("Błąd: " + e.getMessage()));
    }

    public void createTicketPanel() {
        Guild guild = plugin.getDiscordManager().getGuild();
        if (guild == null) return;

        // KANAŁ gdzie ma być embed (np. #tickety) - NIE kategoria!
        long panelChannelId = plugin.getCfg().getDiscordTicketPanelChannel();
        TextChannel channel = guild.getTextChannelById(panelChannelId);
        if (channel == null) {
            plugin.getLogger().severe("Nie znaleziono kanału panelu ticketów: " + panelChannelId);
            return;
        }

        if (embedExists(channel, "System Ticketów")) {
            plugin.getLogger().info("✓ Panel ticketów już istnieje - pomijam");
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎫 System Ticketów")
                .setDescription("Potrzebujesz pomocy? Utwórz ticket!\n\n" +
                        "Kliknij przycisk poniżej aby utworzyć prywatny kanał pomocy.\n" +
                        "Staff odpisze tak szybko jak to możliwe.\n\n" +
                        "**Zasady:**\n" +
                        "• Tylko jeden otwarty ticket na raz\n" +
                        "• Nie spamuj i bądź cierpliwy\n" +
                        "• Opisz dokładnie swój problem")
                .addField("📋 Rodzaje ticketów",
                        "• 🐛 Bug report\n" +
                                "• 💬 Pytanie do administracji\n" +
                                "• 🛡️ Zgłoszenie gracza\n" +
                                "• 💡 Inne sprawy", false)
                .setColor(Color.CYAN)
                .setFooter("EasyAge.pl • Ticket System")
                .setTimestamp(Instant.now());

        Button createButton = Button.primary("create_ticket", "🎫 Utwórz ticket");
        channel.sendMessageEmbeds(embed.build()).addActionRow(createButton).queue(
                success -> plugin.getLogger().info("✓ Panel ticketów utworzony na #" + channel.getName()),
                error -> plugin.getLogger().severe("Błąd tworzenia panelu ticketów: " + error.getMessage()));
    }

    public void createBanAppealPanel() {
        Guild guild = plugin.getDiscordManager().getGuild(); if (guild == null) return;
        long channelId = plugin.getCfg().getDiscordBanAppealChannel();
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) { plugin.getLogger().severe("Nie znaleziono kanału odwołań: " + channelId); return; }
        if (embedExists(channel, "Odwołanie od bana")) { plugin.getLogger().info("✓ Panel odwołań już istnieje"); return; }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚖️ Odwołanie od bana")
                .setDescription("Zostałeś zbanowany na serwerze Minecraft lub Discord?\n\n" +
                        "Kliknij przycisk poniżej aby złożyć odwołanie.\n" +
                        "Administracja rozpatrzy je tak szybko jak to możliwe.\n\n" +
                        "**Wskazówki:**\n" +
                        "• Bądź szczery i konkretny\n" +
                        "• Załącz screenshoty jeśli masz\n" +
                        "• Nie wysyłaj wielu odwołań")
                .addField("⏱️ Czas rozpatrzenia", "Zazwyczaj 24-48 godzin", false)
                .setColor(Color.ORANGE).setFooter("EasyAge.pl • Ban Appeal System").setTimestamp(Instant.now());
        Button btn = Button.primary("appeal_ban", "📝 Złóż odwołanie");
        channel.sendMessageEmbeds(embed.build()).addActionRow(btn).queue(
                s -> plugin.getLogger().info("✓ Panel odwołań utworzony"),
                e -> plugin.getLogger().severe("Błąd: " + e.getMessage()));
    }

    public void createProposalInfoEmbed() {
        Guild guild = plugin.getDiscordManager().getGuild(); if (guild == null) return;
        long channelId = plugin.getCfg().getDiscordProposalsChannel();
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) return;
        if (embedExists(channel, "System Propozycji")) { plugin.getLogger().info("✓ Panel propozycji już istnieje"); return; }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(" System Propozycji")
                .setDescription("Masz pomysł na ulepszenie serwera? Napisz go tutaj!\n\n" +
                        "Twoja propozycja pojawi się jako embed z przyciskami do głosowania.\n\n" +
                        "**Zasady:**\n" +
                        "• Min. 10 znaków\n" +
                        "• Musisz być zweryfikowany\n" +
                        "• Jedna propozycja na temat\n" +
                        "• Próg: 10 głosów i 50% poparcia")
                .setColor(Color.PINK).setFooter("EasyAge.pl • Proposals System").setTimestamp(Instant.now());
        channel.sendMessageEmbeds(embed.build()).queue(
                s -> plugin.getLogger().info("✓ Panel propozycji utworzony"),
                e -> plugin.getLogger().severe("Błąd: " + e.getMessage()));
    }

    private boolean embedExists(TextChannel channel, String titleContains) {
        try {
            List<Message> messages = channel.getHistory().retrievePast(20).complete();
            return messages.stream().anyMatch(msg -> msg.getAuthor().isBot() &&
                    msg.getEmbeds().stream().anyMatch(embed -> embed.getTitle() != null &&
                            embed.getTitle().contains(titleContains)));
        } catch (Exception e) { return false; }
    }
}