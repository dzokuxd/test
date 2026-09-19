package pl.sectorsystem.paper.sync;

import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.World;
import pl.sectorsystem.common.messaging.SectorMessage;
import pl.sectorsystem.paper.SectorPaperPlugin;

public class WorldSyncManager {
    private final SectorPaperPlugin plugin;
    private final boolean isMaster;
    private final Gson gson = new Gson();

    public WorldSyncManager(SectorPaperPlugin plugin) {
        this.plugin = plugin;
        this.isMaster = "guild".equals(plugin.getCurrentSectorId());

        if (isMaster) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::publish, 100L, 100L);
        }
        plugin.getNats().subscribeRaw("world_sync", this::onRaw);
    }

    private void publish() {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) return;
        SectorMessage msg = new SectorMessage(SectorMessage.Type.SECTOR_STATUS);
        msg.setFromSector(plugin.getCurrentSectorId());
        msg.setReason("WORLD_SYNC");
        msg.setPayload(world.getTime() + ";" + world.hasStorm() + ";" + world.isThundering() + ";" + world.getWeatherDuration());
        try {
            plugin.getNats().publishRaw("world_sync", gson.toJson(msg));
        } catch (Exception ignored) {}
    }

    private void onRaw(String json) {
        if (isMaster) return;
        try {
            SectorMessage msg = gson.fromJson(json, SectorMessage.class);
            if (msg.getPayload() == null) return;
            String[] parts = msg.getPayload().split(";");
            if (parts.length < 4) return;
            long time = Long.parseLong(parts[0]);
            boolean storm = Boolean.parseBoolean(parts[1]);
            boolean thunder = Boolean.parseBoolean(parts[2]);
            int duration = Integer.parseInt(parts[3]);
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (World w : Bukkit.getWorlds()) {
                    w.setTime(time);
                    w.setStorm(storm);
                    w.setThundering(thunder);
                    w.setWeatherDuration(duration);
                }
            });
        } catch (Exception ignored) {}
    }
}
