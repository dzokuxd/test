package pl.gildie.sector;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.Const;

public final class SectorProvider {
    private static String cached;

    public static String currentSector(JavaPlugin plugin) {
        if (cached != null) return cached;

        String prop = System.getProperty("gildie.sector");
        if (prop != null && !prop.isBlank()) {
            cached = prop.trim();
            return cached;
        }

        try {
            Plugin ss = Bukkit.getPluginManager().getPlugin("SectorSystem");
            if (ss != null) {
                Object cm = ss.getClass().getMethod("getConfigManager").invoke(ss);
                Object sector = cm.getClass().getMethod("getCurrentSector").invoke(cm);
                if (sector != null) {
                    cached = sector.toString();
                    return cached;
                }
            }
        } catch (Exception ignored) { }

        plugin.getLogger().warning("Nie udalo sie ustalic sektora - przyjmuje " + Const.GUILD_SECTOR);
        cached = Const.GUILD_SECTOR;
        return cached;
    }

    public static boolean isGuildSector(JavaPlugin plugin) {
        return currentSector(plugin).equalsIgnoreCase(Const.GUILD_SECTOR);
    }

    private SectorProvider() {}
}
