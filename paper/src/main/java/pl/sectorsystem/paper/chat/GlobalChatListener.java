package pl.sectorsystem.paper.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import pl.sectorsystem.common.messaging.SectorMessage;
import pl.sectorsystem.paper.SectorPaperPlugin;

/**
 * Przechwytuje chat i wysyła go globalnie przez NATS.
 */
public class GlobalChatListener implements Listener {

    private final SectorPaperPlugin plugin;

    public GlobalChatListener(SectorPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // Anulujemy lokalny broadcast – wyślemy sami globalnie
        event.setCancelled(true);

        Player player = event.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());

        SectorMessage msg = new SectorMessage(SectorMessage.Type.GLOBAL_CHAT);
        msg.setPlayerUuid(player.getUniqueId());
        msg.setPlayerName(player.getName());
        msg.setFromSector(plugin.getCurrentSectorId());
        msg.setMessage(plain);
        msg.setFormat("§7[§e" + plugin.getCurrentSectorId() + "§7] §f" + player.getName() + "§7: §f");

        try {
            plugin.getNats().publish(msg);
        } catch (Exception e) {
            // Fallback – pokaż tylko lokalnie
            player.sendMessage("§cGlobalny chat niedostępny. Wiadomość lokalna:");
            plugin.getServer().broadcast(net.kyori.adventure.text.Component.text(
                    "§7[§e" + plugin.getCurrentSectorId() + "§7] §f" + player.getName() + "§7: §f" + plain
            ));
        }
    }
}
