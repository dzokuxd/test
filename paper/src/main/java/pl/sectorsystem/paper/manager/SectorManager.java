package pl.sectorsystem.paper.manager;

import pl.sectorsystem.common.SectorDefinition;
import pl.sectorsystem.common.SectorType;
import pl.sectorsystem.paper.SectorPaperPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SectorManager {
    private final SectorPaperPlugin plugin;
    private final Map<String, Integer> maxPlayers = new HashMap<>();

    public SectorManager(SectorPaperPlugin plugin) {
        this.plugin = plugin;
        // Domyślne limity (0 = bez limitu)
        maxPlayers.put("guild", plugin.getConfig().getInt("limits.guild", 100));
        maxPlayers.put("spawn", plugin.getConfig().getInt("limits.spawn", 50));
        maxPlayers.put("afk", plugin.getConfig().getInt("limits.afk", 30));
    }

    public SectorDefinition getSector(String id) {
        return plugin.getSystemConfig().getSectors().get(id);
    }

    public SectorDefinition getCurrentSector() {
        return getSector(plugin.getCurrentSectorId());
    }

    public boolean isGuildSector() {
        SectorDefinition def = getCurrentSector();
        return def != null && def.getType() == SectorType.GUILD;
    }

    public boolean isSpawnSector() {
        SectorDefinition def = getCurrentSector();
        return def != null && def.getType() == SectorType.SPAWN;
    }

    public boolean isAfkSector() {
        SectorDefinition def = getCurrentSector();
        return def != null && def.getType() == SectorType.AFK;
    }

    public Collection<SectorDefinition> getAllSectors() {
        return plugin.getSystemConfig().getSectors().values();
    }

    public boolean sectorExists(String id) {
        return plugin.getSystemConfig().getSectors().containsKey(id);
    }

    public int getMaxPlayers(String sectorId) {
        return maxPlayers.getOrDefault(sectorId, 0);
    }

    public void setMaxPlayers(String sectorId, int max) {
        maxPlayers.put(sectorId, max);
    }

    public void reloadLimits() {
        maxPlayers.put("guild", plugin.getConfig().getInt("limits.guild", 100));
        maxPlayers.put("spawn", plugin.getConfig().getInt("limits.spawn", 50));
        maxPlayers.put("afk", plugin.getConfig().getInt("limits.afk", 30));
    }
}
