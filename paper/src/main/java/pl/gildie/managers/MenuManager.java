package pl.gildie.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.gildie.model.ItemBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MenuManager {
    private static final Map<UUID, Integer> digY = new HashMap<>();
    private static final Map<UUID, Integer> digR = new HashMap<>();
    private static final Map<UUID, Integer> wallR = new HashMap<>();
    private static final Map<UUID, Integer> wallH = new HashMap<>();

    public static void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "\u00a76\u00a7lGIGA FOS MENU");
        inv.setItem(13, ItemBuilder.create(Material.DIAMOND_SHOVEL, "\u00a7c\u00a7lKOPANIE"));
        inv.setItem(11, ItemBuilder.create(Material.SAND, "\u00a7e\u00a7lSCIANY Z PIASKU", "\u00a77Kliknij aby ustawic i zbudowac"));
        inv.setItem(15, ItemBuilder.create(Material.OBSIDIAN, "\u00a7f\u00a7lSCIANY Z OBSYDIANU", "\u00a77Kliknij aby ustawic i zbudowac"));
        fill(inv);
        p.openInventory(inv);
    }

    public static void openSettingsMenu(Player p) {
        UUID id = p.getUniqueId();
        digY.putIfAbsent(id, 70);
        digR.putIfAbsent(id, 15);
        int Y = digY.get(id);
        int R = digR.get(id);
        Inventory inv = Bukkit.createInventory(null, 45, "\u00a78\u00a7lUSTAWIENIA FOSY");
        inv.setItem(13, ItemBuilder.create(Material.DIAMOND_BLOCK, "\u00a7eY: " + Y));
        inv.setItem(12, ItemBuilder.create(Material.RED_STAINED_GLASS_PANE, "\u00a7c-5"));
        inv.setItem(14, ItemBuilder.create(Material.GREEN_STAINED_GLASS_PANE, "\u00a7a+5"));
        inv.setItem(22, ItemBuilder.create(Material.GOLD_BLOCK, "\u00a7eR: " + R));
        inv.setItem(21, ItemBuilder.create(Material.RED_STAINED_GLASS_PANE, "\u00a7c-1"));
        inv.setItem(23, ItemBuilder.create(Material.GREEN_STAINED_GLASS_PANE, "\u00a7a+1"));
        inv.setItem(31, ItemBuilder.create(Material.LIME_WOOL, "\u00a7a\u00a7lSTART"));
        fill(inv);
        p.openInventory(inv);
    }

    public static void handleSettingsClick(Player p, int slot, DigManager dig) {
        UUID id = p.getUniqueId();
        int Y = digY.getOrDefault(id, 70);
        int R = digR.getOrDefault(id, 15);
        switch (slot) {
            case 12: Y = Math.max(5, Y - 5); break;
            case 14: Y = Math.min(200, Y + 5); break;
            case 21: R = Math.max(15, R - 1); break;
            case 23: R = Math.min(41, R + 1); break;
            case 31:
                p.closeInventory();
                dig.start(p, R, Y);
                return;
        }
        digY.put(id, Y);
        digR.put(id, R);
        openSettingsMenu(p);
    }

    public static void openWallsSettingsMenu(Player p, Material material, int digRadius) {
        UUID id = p.getUniqueId();
        int minWall = digRadius + 1;
        int maxWall = digRadius + 9;
        wallR.putIfAbsent(id, minWall);
        int currentR = wallR.get(id);
        if (currentR < minWall) currentR = minWall;
        if (currentR > maxWall) currentR = maxWall;
        wallR.put(id, currentR);
        wallH.putIfAbsent(id, 80);
        int H = wallH.get(id);

        Inventory inv = Bukkit.createInventory(null, 45, "\u00a78\u00a7lUSTAWIENIA SCIAN");
        inv.setItem(13, ItemBuilder.create(material, "\u00a7eMaterial: " + material.name()));
        inv.setItem(22, ItemBuilder.create(Material.GOLD_BLOCK, "\u00a7ePromien sciany: " + currentR,
                "\u00a77Min: " + minWall + " \u00a78| \u00a77Max: " + maxWall));
        inv.setItem(21, ItemBuilder.create(Material.RED_STAINED_GLASS_PANE, "\u00a7c-1"));
        inv.setItem(23, ItemBuilder.create(Material.GREEN_STAINED_GLASS_PANE, "\u00a7a+1"));
        inv.setItem(31, ItemBuilder.create(Material.DIAMOND_BLOCK, "\u00a7eWysokosc: " + H, "\u00a77Max: 80"));
        inv.setItem(30, ItemBuilder.create(Material.RED_STAINED_GLASS_PANE, "\u00a7c-5"));
        inv.setItem(32, ItemBuilder.create(Material.GREEN_STAINED_GLASS_PANE, "\u00a7a+5"));
        inv.setItem(40, ItemBuilder.create(Material.LIME_WOOL, "\u00a7a\u00a7lZACZNIJ BUDOWAC"));
        fill(inv);
        p.openInventory(inv);
    }

    public static void handleWallsSettingsClick(Player p, int slot, DigManager dig, Material material, int digRadius) {
        UUID id = p.getUniqueId();
        int minWall = digRadius + 1;
        int maxWall = digRadius + 9;
        int R = wallR.getOrDefault(id, minWall);
        int H = wallH.getOrDefault(id, 80);
        switch (slot) {
            case 21: R = Math.max(minWall, R - 1); break;
            case 23: R = Math.min(maxWall, R + 1); break;
            case 30: H = Math.max(5, H - 5); break;
            case 32: H = Math.min(80, H + 5); break;
            case 40:
                p.closeInventory();
                dig.buildWalls(p, material, R, H);
                return;
        }
        wallR.put(id, R);
        wallH.put(id, H);
        openWallsSettingsMenu(p, material, digRadius);
    }

    private static void fill(Inventory inv) {
        ItemStack glass = ItemBuilder.create(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }
}
