package pl.sectorsystem.paper.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import pl.sectorsystem.paper.SectorPaperPlugin;

public class DeathListener implements Listener {
    private final SectorPaperPlugin plugin;

    public DeathListener(SectorPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Tylko na sektorze gildii śmierć przenosi na spawn
        if (plugin.getSectorManager().isGuildSector()) {
            // Anulujemy standardowy respawn w tym świecie – przenosimy na spawn
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    if (plugin.getMysql() != null && plugin.getMysql().isEnabled()) {
                        plugin.getMysql().incrementDeaths(player.getUniqueId());
                    }
                    plugin.getTransferManager().forceTransfer(player, "spawn", "DEATH");
                }
            }, 5L); // małe opóźnienie żeby śmierć się dokończyła
        }
    }
}
