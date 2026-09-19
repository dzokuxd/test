package pl.gildie.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import pl.gildie.managers.DigManager;
import pl.gildie.managers.MenuManager;

public class InventoryListener implements Listener {
    private final DigManager dig;

    public InventoryListener(DigManager dig) { this.dig = dig; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle().replace("\u00a7", "");
        int slot = e.getRawSlot();

        if (title.contains("GIGA FOS MENU")) {
            e.setCancelled(true);
            if (slot == 13) MenuManager.openSettingsMenu(p);
            else if (slot == 11) {
                if (dig.getPlayerDigRadius(p) <= 0) { p.sendMessage("\u00a7cNajpierw wykop fose!"); return; }
                MenuManager.openWallsSettingsMenu(p, Material.SAND, dig.getPlayerDigRadius(p));
            } else if (slot == 15) {
                if (dig.getPlayerDigRadius(p) <= 0) { p.sendMessage("\u00a7cNajpierw wykop fose!"); return; }
                MenuManager.openWallsSettingsMenu(p, Material.OBSIDIAN, dig.getPlayerDigRadius(p));
            }
            return;
        }

        if (title.contains("USTAWIENIA FOSY")) {
            e.setCancelled(true);
            MenuManager.handleSettingsClick(p, slot, dig);
            return;
        }

        if (title.contains("USTAWIENIA SCIAN")) {
            e.setCancelled(true);
            Material mat = null;
            if (e.getView().getItem(13) != null) mat = e.getView().getItem(13).getType();
            int digRadius = dig.getPlayerDigRadius(p);
            if (mat != null && (mat == Material.SAND || mat == Material.OBSIDIAN)) {
                MenuManager.handleWallsSettingsClick(p, slot, dig, mat, digRadius);
            }
        }
    }
}
