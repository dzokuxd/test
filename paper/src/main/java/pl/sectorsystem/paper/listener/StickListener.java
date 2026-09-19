package pl.sectorsystem.paper.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.sectorsystem.paper.SectorPaperPlugin;

import java.util.List;

public class StickListener implements Listener {
    private final SectorPaperPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public StickListener(SectorPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.STICK) return;
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        // Sprawdzamy czy to nasz specjalny patyk (po nazwie)
        Component displayName = meta.displayName();
        if (displayName == null) return;

        String name = legacy.serialize(displayName);
        if (!name.contains("Patyk Teleportu") && !name.contains("Teleportu na Spawn")) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Tylko z sektora gildii
        if (!plugin.getSectorManager().isGuildSector()) {
            player.sendMessage("§cMożesz użyć tego itemu tylko na sektorze gildii!");
            return;
        }

        if (plugin.getTransferManager().hasPending(player)) {
            player.sendMessage("§cMasz już aktywne odliczanie!");
            return;
        }

        int delay = plugin.getConfig().getInt("spawn-stick.delay-seconds", 30);
        plugin.getTransferManager().startDelayedTransfer(player, "spawn", "STICK", delay);
    }
}
