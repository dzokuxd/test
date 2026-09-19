package pl.sectorsystem.paper.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import pl.sectorsystem.common.messaging.SectorMessage;
import pl.sectorsystem.paper.SectorPaperPlugin;

/**
 * Odbiera globalne wiadomości chatu z NATS i wyświetla na tym sektorze.
 */
public class GlobalChatHandler {

    private final SectorPaperPlugin plugin;

    public GlobalChatHandler(SectorPaperPlugin plugin) {
        this.plugin = plugin;
        plugin.getNats().subscribe("global_chat", this::handle);
    }

    private void handle(SectorMessage msg) {
        if (msg.getType() != SectorMessage.Type.GLOBAL_CHAT) return;

        String format = msg.getFormat() != null ? msg.getFormat() : "§f" + msg.getPlayerName() + "§7: §f";
        String full = format + (msg.getMessage() != null ? msg.getMessage() : "");

        // Wyświetl na głównym wątku
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(Component.text(full)));
        });
    }
}
