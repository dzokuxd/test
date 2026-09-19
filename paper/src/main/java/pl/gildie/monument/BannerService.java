package pl.gildie.monument;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.Const;
import pl.gildie.monument.util.MonumentBannerItem;
import pl.gildie.util.WaypointHook;

import java.util.UUID;

public class BannerService {
    private static JavaPlugin plugin;

    public static void init(JavaPlugin p) { plugin = p; }

    public static boolean hasBanner(Player p) { return MonumentBannerItem.isBanner(p.getInventory().getHelmet()); }

    public static void giveBanner(Player p, ItemStack banner) {
        if (hasBanner(p)) return;
        ItemStack old = p.getInventory().getHelmet();
        if (old != null && old.getType() != Material.AIR) p.getWorld().dropItemNaturally(p.getLocation(), old.clone());
        p.getInventory().setHelmet(banner.clone());
    }

    public static void dropHelmetBanner(Player p) {
        ItemStack h = p.getInventory().getHelmet();
        if (!MonumentBannerItem.isBanner(h)) return;
        p.getInventory().setHelmet(null);
        dropBannerItem(p.getLocation(), h);
    }

    public static Item dropBannerItem(Location loc, ItemStack banner) {
        UUID dropId = UUID.randomUUID();
        Item item = loc.getWorld().dropItem(loc, banner.clone());
        item.setPickupDelay(20);
        item.setUnlimitedLifetime(true);
        item.getPersistentDataContainer().set(MonumentBannerItem.DROP_ID_KEY, PersistentDataType.STRING, dropId.toString());
        addWaypoint(item);
        return item;
    }

    public static void addWaypoint(Item item) {
        if (!MonumentBannerItem.isBanner(item.getItemStack())) return;
        if (item.getPersistentDataContainer().has(MonumentBannerItem.WAYPOINT_KEY, PersistentDataType.STRING)) return;
        String guild = MonumentBannerItem.getOwnerGuild(item.getItemStack());
        String name = "§6Sztandar Monumentu";
        int color = Const.MONUMENT_BANNER_WP_COLOR;
        UUID wp = (Const.MONUMENT_BANNER_GLOBAL_WP || guild == null)
                ? WaypointHook.addGlobalWaypoint(name, item.getLocation(), color)
                : WaypointHook.addGuildWaypoint(guild, name, item.getLocation(), color).orElse(null);
        if (wp != null) item.getPersistentDataContainer().set(MonumentBannerItem.WAYPOINT_KEY, PersistentDataType.STRING, wp.toString());
    }

    public static void removeDroppedBannerData(Item item) {
        if (!MonumentBannerItem.isBanner(item.getItemStack())) return;
        String wr = item.getPersistentDataContainer().get(MonumentBannerItem.WAYPOINT_KEY, PersistentDataType.STRING);
        if (wr != null) { try { WaypointHook.removeWaypoint(UUID.fromString(wr)); } catch (Exception ignored) {} }
        item.getPersistentDataContainer().remove(MonumentBannerItem.DROP_ID_KEY);
        item.getPersistentDataContainer().remove(MonumentBannerItem.WAYPOINT_KEY);
    }

    public static void removeBannerItemEntities() {
        for (org.bukkit.World w : Bukkit.getWorlds())
            for (Entity e : w.getEntities())
                if (e instanceof Item it && MonumentBannerItem.isBanner(it.getItemStack())) e.remove();
    }
}
