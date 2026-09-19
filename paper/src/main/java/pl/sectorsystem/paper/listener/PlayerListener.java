package pl.sectorsystem.paper.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.sectorsystem.paper.SectorPaperPlugin;

public class PlayerListener implements Listener {
    private final SectorPaperPlugin plugin;

    public PlayerListener(SectorPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            if (plugin.getTransferManager().hasPending(event.getPlayer())) {
                plugin.getTransferManager().cancelPending(event.getPlayer());
                event.getPlayer().sendMessage("§cTeleport anulowany – ruszyłeś się!");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getTransferManager().cancelPending(event.getPlayer());
        if (plugin.getScoreboard() != null) plugin.getScoreboard().remove(event.getPlayer());
        if (plugin.getTablist() != null) plugin.getTablist().onQuit(event.getPlayer());

        // Online count po quit (async, size-1 bo gracz jeszcze w liście)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int count = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);
                plugin.getRedis().setSectorPlayerCount(plugin.getCurrentSectorId(), count);
            } catch (Exception ignored) {}
        });
    }
}
