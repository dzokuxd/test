package pl.dzoku.sectorsystem.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

public class SectorBorderListener implements Listener {
    private final SectorSystemPlugin plugin;

    public SectorBorderListener(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY()) {
            return;
        }

        if (plugin.getTransferStateMachine().isInTransfer(event.getPlayer().getUniqueId())) {
            return;
        }

        if (!plugin.getConfigManager().isInBounds(
                event.getTo().getX(), event.getTo().getY(), event.getTo().getZ())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar("You cannot leave this sector!");
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfigManager().isInBounds(
                event.getTo().getX(), event.getTo().getY(), event.getTo().getZ())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar("Teleport outside sector is blocked!");
        }
    }
}
