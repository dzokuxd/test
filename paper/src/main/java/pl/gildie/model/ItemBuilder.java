package pl.gildie.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class ItemBuilder {
    public static ItemStack create(Material mat, String name, String... lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(name);
        if (lore.length > 0) m.setLore(Arrays.asList(lore));
        i.setItemMeta(m);
        return i;
    }
}
