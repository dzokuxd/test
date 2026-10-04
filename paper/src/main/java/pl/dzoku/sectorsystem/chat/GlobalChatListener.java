package pl.dzoku.sectorsystem.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import pl.sectorsystem.common.messaging.SectorMessage;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

/**
 * Przechwytuje chat i wysyła go globalnie przez NATS.
 */
public class GlobalChatListener implements Listener {
    private final SectorSystemPlugin plugin;

    public GlobalChatListener(SectorSystemPlugin plugin) {
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
            plugin.getNatsService().publish(msg);
        } catch (Exception e) {
            // Fallback – pokaż tylko lokalnie z poprawnymi kolorami
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cGlobalny chat niedostępny. Wiadomość lokalna:"));

            Component fallbackComponent = LegacyComponentSerializer.legacySection().deserialize(
                    "§7[§e" + plugin.getCurrentSectorId() + "§7] §f" + player.getName() + "§7: §f" + plain
            );
            plugin.getServer().broadcast(fallbackComponent);
        }
    }
}