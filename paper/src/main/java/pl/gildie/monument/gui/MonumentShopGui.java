package pl.gildie.monument.gui;

import fr.mrmicky.fastinv.FastInv;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.gildie.monument.PointsManager;
import pl.gildie.monument.util.MonumentMsg;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MonumentShopGui extends FastInv {
    public MonumentShopGui() {
        super(54, "§6Sklep Monumentu");
        setItem(10, createItem(Material.DIAMOND, 1, 10, "§bDiament", "§7Za punkty monumentu"), handler(Material.DIAMOND, 1, 10));
        setItem(11, createItem(Material.TOTEM_OF_UNDYING, 1, 30, "§6Totem", "§7Ratuje zycie"), handler(Material.TOTEM_OF_UNDYING, 1, 30));
        setItem(12, createItem(Material.GOLDEN_APPLE, 1, 15, "§eZlote Jablko", "§7Leczy"), handler(Material.GOLDEN_APPLE, 1, 15));
        setItem(13, createItem(Material.ENDER_PEARL, 1, 20, "§dEnder Pearl", "§7Teleportacja"), handler(Material.ENDER_PEARL, 1, 20));
        setItem(14, createItem(Material.ENCHANTED_GOLDEN_APPLE, 1, 50, "§6Enchanted Apple", "§7Potega"), handler(Material.ENCHANTED_GOLDEN_APPLE, 1, 50));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    private ItemStack createItem(Material m, int amt, int cost, String name, String lore) {
        ItemStack it = new ItemStack(m, amt);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(MonumentMsg.legacy(name));
            List<Component> l = new ArrayList<>();
            l.add(MonumentMsg.legacy(lore));
            l.add(MonumentMsg.desc("Koszt: ").append(MonumentMsg.cmd(cost + " pkt")));
            meta.lore(l);
            it.setItemMeta(meta);
        }
        return it;
    }

    private Consumer<InventoryClickEvent> handler(Material m, int amt, int cost) {
        return ev -> {
            Player p = (Player) ev.getWhoClicked();
            if (PointsManager.getPoints(p.getUniqueId()) >= cost) {
                PointsManager.removePoints(p.getUniqueId(), cost);
                p.getInventory().addItem(new ItemStack(m, amt));
                p.sendMessage(MonumentMsg.cmd("Zakupiono przedmiot!"));
            } else p.sendMessage(MonumentMsg.error("Nie masz wystarczajaco punktow!"));
        };
    }
}
