package pl.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

/**
 * Menadżer wszystkich embedów Discord w systemie.
 * Tworzy embedy: weryfikacyjny, ticketowy, odwołań od banów.
 */
public class DiscordEmbedManager {
    private final SectorSystemPlugin plugin;

    public DiscordEmbedManager(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Tworzy WSZYSTKIE embedy na odpowiednich kanałach.
     * Wywołuj przy starcie bota lub komendą /setup-embeds.
     */
    public void createAllEmbeds() {
        createVerifyEmbed();
        createTicketPanel();
        createBanAppealPanel();
    }

    /**
     * 1. EMBED WERYFIKACYJNY
     * Tworzy embed "Zweryfikuj się" z przyciskiem na kanale weryfikacyjnym.
     * Jeśli embed już istnieje, nie robi nic.
     */
    public void createVerifyEmbed() {
        Guild guild = plugin.getDiscordManager().getGuild();
        if (guild == null) return;

        long verifyChannelId = plugin.getConfig().getLong("discord.channels.verify");
        TextChannel channel = guild.getTextChannelById(verifyChannelId);
        if (channel == null) {
            plugin.getLogger().severe("Nie znaleziono kanału weryfikacyjnego o ID: " + verifyChannelId);
            return;
        }
        if (embedExists(channel, "Weryfikacja konta Minecraft")) {
            plugin.getLogger().info("✓ Embed weryfikacyjny już istnieje - pomijam");
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔐 Weryfikacja konta Minecraft")
                .setDescription("Aby zweryfikować swoje konto Minecraft z Discordem:\n\n" +
                        "1️⃣ Wejdź na serwer Minecraft\n" +
                        "2️⃣ Wpisz komendę `/discord`\n" +
                        "3️⃣ Skopiuj wygenerowany kod\n" +
                        "4️⃣ Kliknij przycisk poniżej i wklej kod\n\n")
                .setColor(Color.BLUE)
                .setFooter("Kod jest ważny przez 5 minut • EasyAge.pl")
                .setTimestamp(Instant.now());

        Button verifyButton = Button.primary("verify_open_modal", "🔑 Wpisz kod weryfikacyjny");

        channel.sendMessageEmbeds(embed.build())
                .addActionRow(verifyButton)
                .queue(
                        success -> plugin.getLogger().info("✓ Embed weryfikacyjny utworzony na #" + channel.getName()),
                        error -> plugin.getLogger().severe("Błąd tworzenia embedu weryfikacyjnego: " + error.getMessage())
                );
    }

    /**
     * 2. EMBED TICKETÓW
     * Tworzy panel z przyciskiem "Utwórz ticket" na kanale ticketów.
     */
    public void createTicketPanel() {
        Guild guild = plugin.getDiscordManager().getGuild();
        if (guild == null) return;

        // Użyj kanału z kategorii ticketów (lub osobnego kanału jeśli skonfigurowany)
        long ticketsCategoryId = plugin.getConfig().getLong("discord.channels.tickets-category");
        var category = guild.getCategoryById(ticketsCategoryId);
        if (category == null) {
            plugin.getLogger().severe("Nie znaleziono kategorii ticketów o ID: " + ticketsCategoryId);
            return;
        }

        // Sprawdź pierwszy kanał tekstowy w kategorii (lub utwórz nowy)
        TextChannel channel = category.getTextChannels().isEmpty() ? null : category.getTextChannels().get(0);
        if (channel == null) {
            plugin.getLogger().severe("Brak kanału tekstowego w kategorii ticketów!");
            return;
        }

        // Sprawdź czy panel już istnieje
        if (embedExists(channel, "System Ticketów")) {
            plugin.getLogger().info("✓ Panel ticketów już istnieje - pomijam");
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎫 System Ticketów")
                .setDescription("Potrzebujesz pomocy? Utwórz ticket!\n\n" +
                        "Kliknij przycisk poniżej aby utworzyć **prywatny kanał pomocy**.\n" +
                        "Staff odpisze tak szybko jak to możliwe.\n\n" +
                        "**Zasady:**\n" +
                        "• Tylko jeden otwarty ticket na raz\n" +
                        "• Nie spamuj i bądź cierpliwy\n" +
                        "• Opisz dokładnie swój problem\n" +
                        "• Możesz załączyć screenshoty")
                .addField("📋 Rodzaje ticketów",
                        "• 🐛 Bug report\n" +
                                "•  Pytanie do administracji\n" +
                                "• 🛡️ Zgłoszenie gracza\n" +
                                "• 💡 Inne sprawy", false)
                .setColor(Color.CYAN)
                .setFooter("EasyAge.pl • Ticket System")
                .setTimestamp(Instant.now());

        Button createButton = Button.primary("create_ticket", " Utwórz ticket");

        channel.sendMessageEmbeds(embed.build())
                .addActionRow(createButton)
                .queue(
                        success -> plugin.getLogger().info("✓ Panel ticketów utworzony na #" + channel.getName()),
                        error -> plugin.getLogger().severe("Błąd tworzenia panelu ticketów: " + error.getMessage())
                );
    }

    /**
     * 3. EMBED ODWOŁAŃ OD BANÓW
     * Tworzy panel z przyciskiem "Złóż odwołanie" na kanale odwołań.
     */
    public void createBanAppealPanel() {
        Guild guild = plugin.getDiscordManager().getGuild();
        if (guild == null) return;

        // Użyj kanału propozycji jako tymczasowego (lub dodaj osobny kanał w configu)
        long appealChannelId = plugin.getConfig().getLong("discord.channels.proposals", 0);
        if (appealChannelId == 0) {
            plugin.getLogger().warning("Nie skonfigurowano kanału odwołań - pomijam");
            return;
        }

        TextChannel channel = guild.getTextChannelById(appealChannelId);
        if (channel == null) {
            plugin.getLogger().severe("Nie znaleziono kanału odwołań o ID: " + appealChannelId);
            return;
        }

        // Sprawdź czy panel już istnieje
        if (embedExists(channel, "Odwołanie od bana")) {
            plugin.getLogger().info("✓ Panel odwołań już istnieje - pomijam");
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚖️ Odwołanie od bana")
                .setDescription("Zostałeś zbanowany na serwerze Minecraft lub Discord?\n\n" +
                        "Kliknij przycisk poniżej aby złożyć odwołanie.\n" +
                        "Administracja rozpatrzy je tak szybko jak to możliwe.\n\n" +
                        "**Wskazówki:**\n" +
                        "• Bądź szczery i konkretny\n" +
                        "• Załącz screenshoty jeśli masz\n" +
                        "• Nie wysyłaj wielu odwołań\n" +
                        "• Czekaj cierpliwie na odpowiedź")
                .addField("⏱️ Czas rozpatrzenia", "Zazwyczaj 24-48 godzin", false)
                .setColor(Color.ORANGE)
                .setFooter("EasyAge.pl • Ban Appeal System")
                .setTimestamp(Instant.now());

        Button appealButton = Button.primary("appeal_ban", "📝 Złóż odwołanie");

        channel.sendMessageEmbeds(embed.build())
                .addActionRow(appealButton)
                .queue(
                        success -> plugin.getLogger().info("✓ Panel odwołań utworzony na #" + channel.getName()),
                        error -> plugin.getLogger().severe("Błąd tworzenia panelu odwołań: " + error.getMessage())
                );
    }

    /**
     * Sprawdza czy embed o podanym tytule już istnieje na kanale.
     */
    private boolean embedExists(TextChannel channel, String titleContains) {
        try {
            List<Message> messages = channel.getHistory().retrievePast(20).complete();
            return messages.stream()
                    .anyMatch(msg -> msg.getAuthor().isBot() &&
                            msg.getEmbeds().stream()
                                    .anyMatch(embed -> embed.getTitle() != null &&
                                            embed.getTitle().contains(titleContains)));
        } catch (Exception e) {
            return false;
        }
    }
}