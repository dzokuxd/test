package pl.gildie.monument.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

public class MonumentBannerItem {
    public static NamespacedKey BANNER_KEY;
    public static NamespacedKey TYPE_KEY;
    public static NamespacedKey GUILD_KEY;
    public static NamespacedKey CORNER_KEY;
    public static NamespacedKey CAPTURE_TIME_KEY;
    public static NamespacedKey WAYPOINT_KEY;
    public static NamespacedKey DROP_ID_KEY;

    public static void init(JavaPlugin plugin) {
        BANNER_KEY = new NamespacedKey(plugin, "monument_banner");
        TYPE_KEY = new NamespacedKey(plugin, "monument_type");
        GUILD_KEY = new NamespacedKey(plugin, "monument_guild");
        CORNER_KEY = new NamespacedKey(plugin, "monument_corner");
        CAPTURE_TIME_KEY = new NamespacedKey(plugin, "monument_capture_time");
        WAYPOINT_KEY = new NamespacedKey(plugin, "monument_waypoint");
        DROP_ID_KEY = new NamespacedKey(plugin, "monument_drop_id");
    }

    public static ItemStack createBanner(String type, String guildTag, int cornerId, long captureTime) {
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta = banner.getItemMeta();
        if ("CENTER".equals(type)) meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Sztandar Monumentu (KORONA)");
        else meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Sztandar Monumentu (Rog)");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Zanies do jajka swojej gildii!",
                ChatColor.GRAY + "Gildia: " + ChatColor.WHITE + guildTag
        ));
        meta.getPersistentDataContainer().set(BANNER_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(TYPE_KEY, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(GUILD_KEY, PersistentDataType.STRING, guildTag);
        meta.getPersistentDataContainer().set(CORNER_KEY, PersistentDataType.INTEGER, cornerId);
        meta.getPersistentDataContainer().set(CAPTURE_TIME_KEY, PersistentDataType.LONG, captureTime);
        banner.setItemMeta(meta);
        return banner;
    }

    public static boolean isBanner(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(BANNER_KEY, PersistentDataType.BYTE);
    }

    public static String getType(ItemStack item) {
        if (!isBanner(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(TYPE_KEY, PersistentDataType.STRING);
    }

    public static String getOwnerGuild(ItemStack item) {
        if (!isBanner(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(GUILD_KEY, PersistentDataType.STRING);
    }

    public static int getCornerId(ItemStack item) {
        if (!isBanner(item)) return -1;
        Integer id = item.getItemMeta().getPersistentDataContainer().get(CORNER_KEY, PersistentDataType.INTEGER);
        return id == null ? -1 : id;
    }

    public static long getCaptureTime(ItemStack item) {
        if (!isBanner(item)) return 0L;
        Long t = item.getItemMeta().getPersistentDataContainer().get(CAPTURE_TIME_KEY, PersistentDataType.LONG);
        return t == null ? 0L : t;
    }
}
