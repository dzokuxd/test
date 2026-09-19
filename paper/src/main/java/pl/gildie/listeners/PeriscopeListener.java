package pl.gildie.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import pl.gildie.managers.PeriscopeManager;

public class PeriscopeListener implements Listener {
    private final PeriscopeManager periscopeManager;

    public PeriscopeListener(PeriscopeManager periscopeManager) { this.periscopeManager = periscopeManager; }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking() && periscopeManager.isInPeriscope(e.getPlayer())) {
            periscopeManager.stop(e.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (periscopeManager.isInPeriscope(e.getPlayer())) periscopeManager.stop(e.getPlayer());
    }
}
