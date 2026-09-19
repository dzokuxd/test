package pl.gildie.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;
import pl.gildie.util.WaypointHook;

public class JoinListener implements Listener {
    private final JavaPlugin plugin;
    private final GuildManager guildManager;

    public JoinListener(JavaPlugin plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
            if (g == null) return;
            if (g.getGuildWaypointId() != null) {
                Location c = g.getCenterAtY(70);
                if (c != null) WaypointHook.addGuildWaypoint(player, "Gildia " + g.getTag(), c, 0x55FF55);
            }
            if (g.hasActiveRaidBase()) {
                Location r = g.getRaidBase();
                if (r != null) WaypointHook.addGuildWaypoint(player, "Baza wypadowa", r, 0xFF5555);
            }
        }, 40L);
    }
}
