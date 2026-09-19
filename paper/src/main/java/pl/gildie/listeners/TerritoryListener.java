package pl.gildie.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import pl.gildie.managers.RegenManager;
import pl.gildie.managers.TerritoryBarManager;

public class TerritoryListener implements Listener {
    private final TerritoryBarManager territoryBarManager;
    private final RegenManager regenManager;

    public TerritoryListener(TerritoryBarManager territoryBarManager, RegenManager regenManager) {
        this.territoryBarManager = territoryBarManager;
        this.regenManager = regenManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { territoryBarManager.update(event.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        territoryBarManager.hide(event.getPlayer());
        regenManager.clearBar(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) { territoryBarManager.update(event.getPlayer()); }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) { territoryBarManager.update(event.getPlayer()); }
}
