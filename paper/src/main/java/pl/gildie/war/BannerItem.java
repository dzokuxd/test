package pl.gildie.war;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.UUID;

public final class BannerItem {
    private static NamespacedKey KEY_WAR_ID;
    private static NamespacedKey KEY_BANNER_ID;
    private static NamespacedKey KEY_FROM_GUILD;
    private static NamespacedKey KEY_TO_GUILD;

    private BannerItem() { }

    public static void init(JavaPlugin plugin) {
        KEY_WAR_ID = new NamespacedKey(plugin, "war_id");
        KEY_BANNER_ID = new NamespacedKey(plugin, "banner_id");
        KEY_FROM_GUILD = new NamespacedKey(plugin, "from_guild");
        KEY_TO_GUILD = new NamespacedKey(plugin, "to_guild");
    }

    public static ItemStack create(UUID warId, UUID bannerId, String fromGuildTag, String toGuildTag) {
        ItemStack item = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName("\u00a7c\u00a7lSztandar gildii " + fromGuildTag);
        meta.setLore(Arrays.asList(
                "\u00a77Podbita gildia: \u00a7f" + fromGuildTag,
                "\u00a77Przenies do swojej gildii aby zaliczyc podbicie",
                "\u00a78ID: " + bannerId.toString().substring(0, 8)
        ));
        meta.setUnbreakable(true);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_WAR_ID, PersistentDataType.STRING, warId.toString());
        pdc.set(KEY_BANNER_ID, PersistentDataType.STRING, bannerId.toString());
        pdc.set(KEY_FROM_GUILD, PersistentDataType.STRING, fromGuildTag.toUpperCase());
        pdc.set(KEY_TO_GUILD, PersistentDataType.STRING, toGuildTag.toUpperCase());
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isBanner(ItemStack item) {
        if (item == null || KEY_BANNER_ID == null) return false;
        if (item.getType() != Material.WHITE_BANNER) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY_BANNER_ID, PersistentDataType.STRING);
    }

    public static UUID getWarId(ItemStack item) {
        if (!isBanner(item)) return null;
        return parseUuid(item.getItemMeta().getPersistentDataContainer().get(KEY_WAR_ID, PersistentDataType.STRING));
    }

    public static UUID getBannerId(ItemStack item) {
        if (!isBanner(item)) return null;
        return parseUuid(item.getItemMeta().getPersistentDataContainer().get(KEY_BANNER_ID, PersistentDataType.STRING));
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    public static String getFromGuild(ItemStack item) {
        if (!isBanner(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_FROM_GUILD, PersistentDataType.STRING);
    }

    public static String getToGuild(ItemStack item) {
        if (!isBanner(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_TO_GUILD, PersistentDataType.STRING);
    }
}
