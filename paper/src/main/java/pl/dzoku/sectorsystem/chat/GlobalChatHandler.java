package pl.dzoku.sectorsystem.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import pl.sectorsystem.common.messaging.SectorMessage;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

/**
 * Odbiera globalne wiadomości chatu z NATS i wyświetla na tym sektorze.
 */
public class GlobalChatHandler {
    private final SectorSystemPlugin plugin;

    public GlobalChatHandler(SectorSystemPlugin plugin) {
        this.plugin = plugin;
        plugin.getNatsService().subscribe("global_chat", this::handle);
    }

    private void handle(SectorMessage msg) {
        if (msg.getType() != SectorMessage.Type.GLOBAL_CHAT) return;

        String format = msg.getFormat() != null ? msg.getFormat() : "§f" + msg.getPlayerName() + "§7: §f";
        String full = format + (msg.getMessage() != null ? msg.getMessage() : "");

        // Wyświetl na głównym wątku z poprawną deserializacją kolorów
        Bukkit.getScheduler().runTask(plugin, () -> {
            Component component = LegacyComponentSerializer.legacySection().deserialize(full);
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(component));
        });
    }
}