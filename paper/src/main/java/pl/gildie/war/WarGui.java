package pl.gildie.war;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public final class WarGui {
    public static final String TITLE_MAIN = "§c§lWojny gildii";
    public static final String TITLE_CHALLENGE = "§c§lWyzwij gildie";
    public static final String TITLE_DURATION = "§e§lCzas trwania wojny";
    public static final String TITLE_STATS = "§cWojna vs ";
    public static final String TITLE_HISTORY = "§6Historia wojen";
    public static final String TITLE_PICK_STATS = "§e§lWybierz wojne (statystyki)";

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private WarGui() {}

    public static void openMain(Player player, GuildManager guildManager, WarManager warManager) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_MAIN);

        List<String> bookLore = new ArrayList<>();
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own != null) {
            List<War> wars = warManager.getActiveWarsOf(own.getTag());
            if (wars.isEmpty()) bookLore.add("§7Brak aktywnych wojen.");
            else for (War w : wars) {
                bookLore.add("§c⚔ vs §e" + w.getOpponent(own.getTag()) + " §7- koniec za §f" + formatDuration(w.getRemainingMs()));
            }
        } else bookLore.add("§7Nie masz gildii.");
        bookLore.add("");
        bookLore.add("§7Kliknij: zabojstwa, podbicia,");
        bookLore.add("§7deklarujacy, czas końca.");

        inv.setItem(11, named(Material.IRON_SWORD, "§c§lWyzwij gildie",
                "§7Kliknij, aby wybrac gildie", "§7i czas trwania wojny (1-3 h).", "",
                "§eTNT musi byc wlaczone (16:00-21:00)"));
        inv.setItem(13, named(Material.BOOK, "§e§lStatystyki wojny", bookLore.toArray(new String[0])));
        inv.setItem(15, named(Material.CHEST, "§6§lHistoria wojen",
                "§7Wszystkie zakonczone", "§7i aktywne wojny."));

        boolean tnt = TntManager.isTntEnabled();
        inv.setItem(22, named(tnt ? Material.TNT : Material.BARRIER,
                tnt ? "§a§lTNT WŁĄCZONE" : "§c§lTNT WYŁĄCZONE",
                "§7Godziny: §f16:00-21:00", "§7Admin: /tnt"));
        player.openInventory(inv);
    }

    public static void openChallengeList(Player player, GuildManager guildManager, WarManager warManager) {
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null) { player.sendMessage("§cNie nalezysz do zadnej gildii."); return; }
        if (!own.isLeaderOrDeputy(player.getUniqueId())) { player.sendMessage("§cTylko lider lub zastepca."); return; }
        if (!TntManager.isTntEnabled()) { player.sendMessage("§cWojne mozna wypowiedziec tylko gdy TNT jest wlaczone."); return; }
        if (warManager.getActiveWarCount(own.getTag()) >= 2) { player.sendMessage("§cTwoja gildia ma juz 2 wojny."); return; }

        List<Guild> candidates = guildManager.getAll().stream()
                .filter(g -> !g.getTag().equalsIgnoreCase(own.getTag()))
                .filter(g -> !own.isAlliedWith(g.getTag()))
                .filter(g -> warManager.getActiveWarBetween(own.getTag(), g.getTag()).isEmpty())
                .filter(g -> warManager.getActiveWarCount(g.getTag()) < 2)
                .collect(Collectors.toList());

        int size = Math.min(54, Math.max(9, ((candidates.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(null, size, TITLE_CHALLENGE);
        int i = 0;
        for (Guild g : candidates) {
            if (i >= size) break;
            inv.setItem(i++, named(Material.WHITE_BANNER, "§e" + g.getTag(),
                    "§7Czlonkow: §f" + g.getMembers().size(),
                    "§7Punkty: §f" + g.getRankPoints(),
                    "§7Kliknij: wybor czasu wojny."));
        }
        if (candidates.isEmpty()) inv.setItem(4, named(Material.BARRIER, "§cBrak dostepnych gildii"));
        player.openInventory(inv);
    }

    public static void openDurationPicker(Player player, String targetTag) {
        Inventory inv = Bukkit.createInventory(null, 9, TITLE_DURATION);
        inv.setItem(2, named(Material.CLOCK, "§a1 godzina", "§7Wyzwij §e" + targetTag + " §7na §f1h", "§8TAG:" + targetTag.toUpperCase() + ":1"));
        inv.setItem(4, named(Material.CLOCK, "§e2 godziny", "§7Wyzwij §e" + targetTag + " §7na §f2h", "§8TAG:" + targetTag.toUpperCase() + ":2"));
        inv.setItem(6, named(Material.CLOCK, "§c3 godziny", "§7Wyzwij §e" + targetTag + " §7na §f3h", "§8TAG:" + targetTag.toUpperCase() + ":3"));
        player.openInventory(inv);
    }

    public static void openStatsPicker(Player player, GuildManager guildManager, WarManager warManager) {
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null) { player.sendMessage("§cNie masz gildii."); return; }
        List<War> wars = warManager.getActiveWarsOf(own.getTag());
        if (wars.isEmpty()) { player.sendMessage("§cTwoja gildia nie prowadzi zadnej wojny."); return; }
        if (wars.size() == 1) { openStats(player, own, wars.get(0)); return; }
        Inventory inv = Bukkit.createInventory(null, 9, TITLE_PICK_STATS);
        int slot = 2;
        for (War w : wars) {
            String opp = w.getOpponent(own.getTag());
            inv.setItem(slot, named(Material.IRON_SWORD, "§e" + opp,
                    "§7Koniec za: §f" + formatDuration(w.getRemainingMs()),
                    "§7Kliknij: statystyki"));
            slot += 2;
        }
        player.openInventory(inv);
    }

    public static void openStats(Player player, Guild own, War war) {
        String tag = war.getOpponent(own.getTag());
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_STATS + tag);
        WarStats ownS = war.getStats(own.getTag());
        WarStats enemyS = war.getStats(tag);

        boolean ownConquered = own.getTag().equals(war.getAttackerTag()) ? war.isConquestByAttacker() : war.isConquestByDefender();
        boolean enemyConquered = !ownConquered && (war.isConquestByAttacker() || war.isConquestByDefender());

        inv.setItem(11, named(Material.DIAMOND_SWORD, "§aTwoja gildia §f" + own.getTag(),
                "§7Zabojstwa: §f" + ownS.getKills(),
                "§7Zgony: §f" + ownS.getDeaths(),
                "§7Koxy: §f" + ownS.getKoxyEaten(),
                "§7Refy: §f" + ownS.getRefillsUsed(),
                "§7Perly: §f" + ownS.getPearlsUsed(),
                "§7TNT: §f" + ownS.getTntFired(),
                "§7Uderzenia w jajo: §f" + ownS.getEggHits(),
                "§7Serce zniszczone: " + (ownConquered ? "§cTAK" : "§aNIE"),
                "§7Punkty rankingu: §f" + own.getRankPoints()));

        Guild enemy = null;
        for (Guild g : org.bukkit.Bukkit.getOnlinePlayers().isEmpty() ? java.util.List.<Guild>of() : java.util.List.<Guild>of()) { /* noop */ }
        inv.setItem(15, named(Material.IRON_SWORD, "§cPrzeciwnik §f" + tag,
                "§7Zabojstwa: §f" + enemyS.getKills(),
                "§7Zgony: §f" + enemyS.getDeaths(),
                "§7Koxy: §f" + enemyS.getKoxyEaten(),
                "§7Refy: §f" + enemyS.getRefillsUsed(),
                "§7Perly: §f" + enemyS.getPearlsUsed(),
                "§7TNT: §f" + enemyS.getTntFired(),
                "§7Uderzenia w jajo: §f" + enemyS.getEggHits(),
                "§7Serce zniszczone: " + (enemyConquered ? "§cTAK" : "§aNIE")));

        inv.setItem(13, named(Material.CLOCK, "§eInformacje o wojnie",
                "§7Koniec za: §f" + formatDuration(war.getRemainingMs()),
                "§7Start: §f" + DATE_FMT.format(new Date(war.getStartTime())),
                "§7Wojne wypowiedzial: §f" + war.getDeclarerName(),
                "§7Stan: §aAktywna"));

        inv.setItem(22, named(Material.ARROW, "§7« Powrót", "§8Kliknij, aby wrocic"));
        player.openInventory(inv);
    }

    public static void openHistory(Player player, WarManager warManager) {
        List<War> sorted = new ArrayList<>(warManager.getHistory());
        sorted.sort((a, b) -> Long.compare(b.getStartTime(), a.getStartTime()));
        int size = Math.min(54, Math.max(9, ((sorted.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(null, size, TITLE_HISTORY);
        int i = 0;
        for (War w : sorted) {
            if (i >= size) break;
            Material mat = switch (w.getState()) {
                case ACTIVE -> Material.REDSTONE_BLOCK;
                case ENDED_CONQUEST -> Material.GOLD_BLOCK;
                case ENDED_KILLS -> Material.IRON_BLOCK;
                default -> Material.COAL_BLOCK;
            };
            String stan = switch (w.getState()) {
                case ACTIVE -> "§aAktywna";
                case ENDED_CONQUEST -> "§6Zakonczona (podbicie)";
                case ENDED_KILLS -> "§eZakończona (zabójstwa)";
                default -> "§7Zakonczona (czas)";
            };
            WarStats a = w.getStats(w.getAttackerTag());
            WarStats d = w.getStats(w.getDefenderTag());
            List<String> lore = new ArrayList<>();
            lore.add("§7Stan: " + stan);
            lore.add("§7Wypowiedzial: §f" + w.getDeclarerName());
            lore.add("§7Start: §f" + DATE_FMT.format(new Date(w.getStartTime())));
            if (w.isActive()) lore.add("§7Pozostalo: §f" + formatDuration(w.getRemainingMs()));
            else if (w.getEndTime() > 0) lore.add("§7Koniec: §f" + DATE_FMT.format(new Date(w.getEndTime())));
            lore.add("");
            lore.add("§7Zabojstwa: §a" + a.getKills() + " §7/ §c" + d.getKills());
            lore.add("§7Podbicia: §a" + (w.isConquestByAttacker() ? 1 : 0) + " §7/ §c" + (w.isConquestByDefender() ? 1 : 0));
            inv.setItem(i++, named(mat, "§e" + w.getAttackerTag() + " §7vs §e" + w.getDefenderTag(), lore.toArray(new String[0])));
        }
        if (sorted.isEmpty()) inv.setItem(4, named(Material.BARRIER, "§7Brak historii wojen"));
        player.openInventory(inv);
    }

    public static String formatDuration(long ms) {
        if (ms <= 0) return "0 min";
        long h = ms / 3_600_000, m = (ms % 3_600_000) / 60_000;
        if (h > 0) return h + "h " + m + "min";
        return m + " min";
    }

    private static ItemStack named(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
