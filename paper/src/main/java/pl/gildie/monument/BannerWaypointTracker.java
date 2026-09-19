package pl.gildie.monument;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.Const;
import pl.gildie.monument.util.MonumentBannerItem;
import pl.gildie.util.WaypointHook;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BannerWaypointTracker {
    private static final Map<UUID, UUID> carrierWaypoints = new HashMap<>();

    public static void init(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!Const.MONUMENT_BANNER_CARRIER_WP) { purgeAll(); return; }
            boolean global = Const.MONUMENT_BANNER_GLOBAL_WP;
            String tpl = "§6Sztandar Monumentu [%guild%]";
            int color = Const.MONUMENT_BANNER_WP_COLOR;

            for (UUID pid : new HashMap<>(carrierWaypoints).keySet()) {
                if (Bukkit.getPlayer(pid) == null) WaypointHook.removeWaypoint(carrierWaypoints.remove(pid));
            }

            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack helm = p.getInventory().getHelmet();
                boolean carrying = MonumentBannerItem.isBanner(helm);
                UUID ex = carrierWaypoints.get(p.getUniqueId());
                if (carrying) {
                    String g = MonumentBannerItem.getOwnerGuild(helm);
                    String name = tpl.replace("%guild%", g == null ? "?" : g);
                    if (ex != null) WaypointHook.removeWaypoint(ex);
                    UUID wp = global
                            ? WaypointHook.addGlobalWaypoint(name, p.getLocation(), color)
                            : WaypointHook.addGuildWaypoint(g, name, p.getLocation(), color).orElse(null);
                    if (wp != null) carrierWaypoints.put(p.getUniqueId(), wp);
                    else carrierWaypoints.remove(p.getUniqueId());
                } else if (ex != null) {
                    WaypointHook.removeWaypoint(ex);
                    carrierWaypoints.remove(p.getUniqueId());
                }
            }
        }, 60L, 60L);
    }

    private static void purgeAll() {
        for (UUID wp : carrierWaypoints.values()) WaypointHook.removeWaypoint(wp);
        carrierWaypoints.clear();
    }
}
