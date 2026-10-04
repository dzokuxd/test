package pl.gildie.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.gildie.GildieModule;
import pl.gildie.managers.RatingManager;
import pl.gildie.model.Guild;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI z top 9 gildii ocenionych przez graczy i administrację.
 * Otwierane komendą /g oceny
 */
public class RatingGui {

    private static final int TOP_SIZE = 9;

    /**
     * Otwiera GUI z ocenami gildii
     */
    public static void open(Player player, GildieModule module) {
        RatingManager rm = module.getRatingManager();

        if (!rm.isRatingActive()) {
            player.sendMessage("§cSystem oceniania jest obecnie wyłączony!");
            return;
        }

        var inv = Bukkit.createInventory(null, 54, "§6§lOcenianie Gildii");

        List<RatingManager.GuildRating> topPlayers = rm.getTopPlayerRatings(TOP_SIZE);
        List<RatingManager.GuildRating> topAdmin = rm.getTopAdminRatings(TOP_SIZE);

        Guild pg = module.getGuildManager().getGuildByPlayer(player.getUniqueId());
        String pTag = pg != null ? pg.getTag() : null;

        // ── GÓRNA CZĘŚĆ: Top 9 Graczy (sloty 0-8) ──
        for (int i = 0; i < TOP_SIZE; i++) {
            if (i < topPlayers.size()) {
                RatingManager.GuildRating rating = topPlayers.get(i);
                boolean isMyGuild = pTag != null && pTag.equals(rating.guildTag);
                inv.setItem(i, createHead(rating.guildTag, rating.score, i + 1, "Gracze", isMyGuild));
            } else {
                inv.setItem(i, createEmpty("§7Brak danych"));
            }
        }

        // ── ŚRODEK: Informacja o własnej gildii (slot 22) ──
        inv.setItem(22, pg != null ? createMyGuildInfo(pg, rm) : createEmpty("§cNie jesteś w gildii"));

        // ── DOLNA CZĘŚĆ: Top 9 Administracji (sloty 36-44) ──
        for (int i = 0; i < TOP_SIZE; i++) {
            if (i < topAdmin.size()) {
                RatingManager.GuildRating rating = topAdmin.get(i);
                boolean isMyGuild = pTag != null && pTag.equals(rating.guildTag);
                inv.setItem(36 + i, createHead(rating.guildTag, rating.score, i + 1, "Administracja", isMyGuild));
            } else {
                inv.setItem(36 + i, createEmpty("§7Brak danych"));
            }
        }

        // ── WYPEŁNIACZE ──
        ItemStack filler = createFiller();
        for (int i = 9; i < 36; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }

        player.openInventory(inv);
    }

    /**
     * Tworzy głowę gracza z informacjami o gildii w rankingu
     */
    private static ItemStack createHead(String tag, double score, int pos, String cat, boolean isMy) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();

        // Wyróżnienie własnej gildii
        meta.setDisplayName(isMy
                ? "§6§l★ #" + pos + " §e§l" + tag + " §6§l★"
                : "§6#" + pos + " §e" + tag);

        List<String> lore = new ArrayList<>();
        lore.add("§7Kategoria: §f" + cat);
        lore.add("");

        if (cat.equals("Gracze")) {
            lore.add("§7Średnia ocen: §a" + String.format("%.2f", score));
            lore.add("§7(od innych gildii)");
        } else {
            lore.add("§7Suma ocen: §a" + (int) score);
            lore.add("§7(od administracji)");
        }

        lore.add("");
        lore.add("§8Pozycja: #" + pos);

        if (isMy) {
            lore.add("");
            lore.add("§a§l★ TO TWOJA GILDIA ★");
        }

        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    /**
     * Tworzy informację o własnej gildii (środkowy slot)
     */
    private static ItemStack createMyGuildInfo(Guild g, RatingManager rm) {
        ItemStack item = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a§lTwoja Gildia: §e" + g.getTag());

        List<String> lore = new ArrayList<>();
        lore.add("§7─────────────────────");

        // Pozycja w rankingu graczy (średnia z otrzymanych ocen)
        List<RatingManager.GuildRating> pr = rm.getTopPlayerRatings(100);
        int pp = -1;
        double ps = 0;
        for (int i = 0; i < pr.size(); i++) {
            if (pr.get(i).guildTag.equals(g.getTag())) {
                pp = i + 1;
                ps = pr.get(i).score;
                break;
            }
        }

        if (pp > 0) {
            lore.add("§7Pozycja (Gracze): §a#" + pp);
            lore.add("§7Średnia ocen: §a" + String.format("%.2f", ps));
            lore.add("§7Otrzymanych głosów: §f" + g.getReceivedPlayerRatingCount());
        } else {
            lore.add("§7Pozycja (Gracze): §cNie w rankingu");
            lore.add("§7Otrzymanych głosów: §f" + g.getReceivedPlayerRatingCount());
        }

        lore.add("");

        // Pozycja w rankingu administracji (suma ocen)
        List<RatingManager.GuildRating> ar = rm.getTopAdminRatings(100);
        int ap = -1;
        double as = 0;
        for (int i = 0; i < ar.size(); i++) {
            if (ar.get(i).guildTag.equals(g.getTag())) {
                ap = i + 1;
                as = ar.get(i).score;
                break;
            }
        }

        if (ap > 0) {
            lore.add("§7Pozycja (Admin): §a#" + ap);
            lore.add("§7Suma ocen: §a" + (int) as);
        } else {
            lore.add("§7Pozycja (Admin): §cNie w rankingu");
        }

        lore.add("§7─────────────────────");
        lore.add("");
        lore.add("§7Członków: §f" + g.getMembers().size());

        // Informacja o tym, kogo gildia ocenia
        String rt = g.getRatedGuildTag();
        if (rt != null) {
            lore.add("");
            lore.add("§7Oceniacie: §e" + rt);
            lore.add("§7Głosów w gildii: §f" + g.getPlayerVotes().size() + "/" + g.getMembers().size());
        } else {
            lore.add("");
            lore.add("§cJeszcze nikogo nie oceniliście");
            lore.add("§7Użyj: §e/g ocena <tag> <1-5>");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Tworzy pusty slot
     */
    private static ItemStack createEmpty(String text) {
        ItemStack i = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(text);
        m.setLore(List.of("§7Brak danych"));
        i.setItemMeta(m);
        return i;
    }

    /**
     * Tworzy wypełniacz (dekoracyjny)
     */
    private static ItemStack createFiller() {
        ItemStack i = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(" ");
        i.setItemMeta(m);
        return i;
    }
}