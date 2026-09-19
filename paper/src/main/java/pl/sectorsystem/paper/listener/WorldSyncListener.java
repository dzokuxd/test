package pl.sectorsystem.paper.listener;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Listener;
import pl.sectorsystem.paper.SectorPaperPlugin;

/**
 * Synchronizacja czasu i pogody – master sektor publikuje, reszta nasłuchuje.
 */
public class WorldSyncListener implements Listener {
    private final SectorPaperPlugin plugin;
    private final boolean isMaster;

    public WorldSyncListener(SectorPaperPlugin plugin) {
        this.plugin = plugin;
        String master = plugin.getConfig().getString("world-sync-master", "guild");
        this.isMaster = plugin.getCurrentSectorId().equalsIgnoreCase(master);

        if (isMaster) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::publishTime, 20L * 30, 20L * 30);
        } else {
            plugin.getNats().subscribeRaw("world_sync", this::onSync);
        }
    }

    private void publishTime() {
        if (Bukkit.getWorlds().isEmpty()) return;
        World w = Bukkit.getWorlds().get(0);
        String payload = w.getTime() + ";" + w.hasStorm() + ";" + w.isThundering();
        try {
            plugin.getNats().publishRaw("world_sync", payload);
        } catch (Exception ignored) {}
    }

    private void onSync(String payload) {
        if (payload == null || payload.isBlank()) return;
        String[] p = payload.split(";");
        if (p.length < 3) return;
        try {
            long time = Long.parseLong(p[0]);
            boolean storm = Boolean.parseBoolean(p[1]);
            boolean thunder = Boolean.parseBoolean(p[2]);
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (World w : Bukkit.getWorlds()) {
                    w.setTime(time);
                    w.setStorm(storm);
                    w.setThundering(thunder);
                }
            });
        } catch (Exception ignored) {}
    }
}
