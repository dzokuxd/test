package pl.dzoku.sectorsystem.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import pl.dzoku.sectorsystem.model.SectorConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ConfigManager {
    private static final Logger logger = Logger.getLogger(ConfigManager.class.getName());

    private final SectorSystemPlugin plugin;
    private final Map<String, SectorConfig> sectors = new HashMap<>();
    private String currentSector;

    public ConfigManager(SectorSystemPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        currentSector = config.getString("current-sector", "guild");
        sectors.clear();

        ConfigurationSection sectorsSection = config.getConfigurationSection("sectors");
        if (sectorsSection != null) {
            for (String key : sectorsSection.getKeys(false)) {
                SectorConfig sc = new SectorConfig();
                sc.setId(key);
                sc.setDisplayName(sectorsSection.getString(key + ".display-name", key));
                sc.setMaxPlayers(sectorsSection.getInt(key + ".max-players", 100));
                sc.setBounded(sectorsSection.getBoolean(key + ".bounded", false));
                sc.setMinX(sectorsSection.getInt(key + ".bounds.min-x", 0));
                sc.setMaxX(sectorsSection.getInt(key + ".bounds.max-x", 0));
                sc.setMinY(sectorsSection.getInt(key + ".bounds.min-y", -64));
                sc.setMaxY(sectorsSection.getInt(key + ".bounds.max-y", 320));
                sc.setMinZ(sectorsSection.getInt(key + ".bounds.min-z", 0));
                sc.setMaxZ(sectorsSection.getInt(key + ".bounds.max-z", 0));
                sc.setFallbackSector(sectorsSection.getString(key + ".fallback", "spawn"));
                sc.setPvpEnabled(sectorsSection.getBoolean(key + ".pvp", false));
                sc.setBuildEnabled(sectorsSection.getBoolean(key + ".build", false));
                sc.setTransferCooldownMs(sectorsSection.getLong(key + ".transfer-cooldown-ms", 60_000));
                sectors.put(key, sc);
            }
        }

        logger.info("Loaded " + sectors.size() + " sector configs. Current: " + currentSector);
    }

    public String getCurrentSector() { return currentSector; }
    public SectorConfig getSectorConfig(String id) { return sectors.get(id); }
    public Map<String, SectorConfig> getAllSectors() { return Collections.unmodifiableMap(sectors); }

    public boolean isInBounds(double x, double y, double z) {
        SectorConfig config = sectors.get(currentSector);
        return config != null && config.isInBounds(x, y, z);
    }
}
