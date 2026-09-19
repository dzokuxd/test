package pl.gildie.monument.listeners;

import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import pl.gildie.monument.MonumentManager;

public class MonumentProtectionListener implements Listener {
    private final MonumentManager monumentManager;

    public MonumentProtectionListener(MonumentManager mm) { this.monumentManager = mm; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (monumentManager.isProtected(e.getBlock().getLocation())) {
            if (!e.getPlayer().hasPermission("gildie.admin")) {
                e.setCancelled(true);
                e.getPlayer().sendMessage("§cNie mozesz niszczyc w strefie monumentu!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (monumentManager.isProtected(e.getBlock().getLocation())) {
            if (!e.getPlayer().hasPermission("gildie.admin")) {
                e.setCancelled(true);
                e.getPlayer().sendMessage("§cNie mozesz budowac w strefie monumentu!");
            }
        }
    }

    // Perly nie dzialaja NA monumencie: blokada rzutu
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlThrow(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof EnderPearl)) return;
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        if (monumentManager.isProtected(p.getLocation())) {
            e.setCancelled(true);
            p.sendMessage("§cPerly nie dzialaja na terenie monumentu!");
        }
    }

    // ...i blokada teleportu perla DO strefy
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        if (e.getTo() != null && monumentManager.isProtected(e.getTo())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cPerly nie dzialaja na terenie monumentu!");
        }
    }
}
