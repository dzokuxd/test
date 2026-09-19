package pl.gildie.service;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import pl.gildie.GildieModule;

public class UserSyncListener implements Listener {
    private final GildieModule module;
    public UserSyncListener(GildieModule module) { this.module = module; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        var p = e.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(module.plugin(), () ->
                module.getUserRepository().upsertIdentity(p.getUniqueId(), p.getName()));
    }
}
