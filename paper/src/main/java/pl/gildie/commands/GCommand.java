package pl.gildie.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.gildie.Const;
import pl.gildie.GildieModule;
import pl.gildie.managers.GuildManager;
import pl.gildie.managers.MenuManager;
import pl.gildie.managers.PingScoreboard;
import pl.gildie.managers.RegenManager;
import pl.gildie.managers.TerritoryBarManager;
import pl.gildie.model.Guild;
import pl.gildie.util.ItemCost;
import pl.gildie.util.TeleportUtil;
import pl.gildie.util.WaypointHook;
import pl.gildie.war.War;
import pl.gildie.EggMonument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class GCommand implements CommandExecutor, TabCompleter {
    private static final int DEFAULT_RADIUS = 50;
    private static final int TELEPORT_SECONDS = 15;
    private static final long INVITE_EXPIRE_MS = 60_000L;
    private static final long ALLIANCE_EXPIRE_MS = 60_000L;
    private static final int ALLIANCE_MAX_MEMBERS = 15;
    private static final long ALLIANCE_CD_MS = 30_000L;
    private static final ItemCost ALLIANCE_COST;
    static {
        Map<Material, Integer> c = new LinkedHashMap<>();
        c.put(Material.DIAMOND, 8);
        ALLIANCE_COST = new ItemCost(c);
    }
    private final Map<UUID, Long> allianceCooldown = new HashMap<>();
    private final Map<UUID, Long> ubwCooldown = new HashMap<>();
    public static final String WAND_NAME = "§6§lRóżdżka zaproszeń gildii";

    private final GildieModule module;
    private final GuildManager guildManager;
    private final RegenManager regenManager;
    private final TerritoryBarManager territoryBarManager;

    public GCommand(GildieModule module, GuildManager guildManager, RegenManager regenManager, TerritoryBarManager bar) {
        this.module = module;
        this.guildManager = guildManager;
        this.regenManager = regenManager;
        this.territoryBarManager = bar;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cTylko gracz."); return true; }
        if (args.length == 0) { sendHelp(player); return true; }
        switch (args[0].toLowerCase()) {
            case "zaloz", "stworz", "create" -> handleCreate(player, args);
            case "opusc", "leave" -> handleLeave(player);
            case "rozwiaz", "disband" -> handleDisband(player);
            case "wyrzuc", "kick" -> handleKick(player, args);
            case "zapros", "invite" -> handleInvite(player, args);
            case "dolacz", "accept", "akceptuj" -> handleAccept(player, args);
            case "odrzuc", "deny" -> handleDeny(player, args);
            case "info", "i" -> handleInfo(player, args);
            case "lista", "list" -> handleList(player);
            case "lider", "leader" -> handleLeader(player, args);
            case "zastepca", "deputy" -> handleDeputy(player, args);
            case "ustawdom", "sethome" -> handleSetHome(player);
            case "dom", "home" -> handleHome(player);
            case "regeneruj", "regen" -> handleRegen(player);
            case "panel" -> MenuManager.openMainMenu(player);
            case "ustawbazawypadowa", "ubw" -> handleSetRaidBase(player);
            case "bazawypadowa", "bw" -> handleRaidBaseTp(player);
            case "peryskop", "p" -> module.getPeriscopeManager().start(player);
            case "sojusz", "ally" -> handleAlliance(player, args);
            case "wojna", "war" -> pl.gildie.war.WarGui.openMain(player, guildManager, module.getWarManager());
            case "pp" -> handlePp(player);
            case "pomoc", "help" -> sendHelp(player);
            default -> player.sendMessage("§cNieznana komenda. Uzyj §e/g pomoc");
        }
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage("§8§m--------------------------------");
        p.sendMessage("§6§lGildie §7- komendy:");
        p.sendMessage("§e/g zaloz <tag> §7- zaloz gildie (r=50)");
        p.sendMessage("§e/g zapros <nick|wand> §7- zapros");
        p.sendMessage("§e/g dolacz <tag> §7- akceptuj zaproszenie");
        p.sendMessage("§e/g opusc | wyrzuc | lider | zastepca | rozwiaz");
        p.sendMessage("§e/g info [tag] | lista");
        p.sendMessage("§e/g ustawdom | dom §7- TP 15s");
        p.sendMessage("§e/g ubw | bw §7- baza wypadowa (1h, cooldown 1h)");
        p.sendMessage("§e/g regeneruj §7- regen <=Y60");
        p.sendMessage("§e/g panel §7- fosa/sciany");
        p.sendMessage("§e/g peryskop | sojusz | wojna | pp");
        p.sendMessage("§8§m--------------------------------");
    }

    private void handlePp(Player player) {
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null) { player.sendMessage("§cNie jestes w gildii!"); return; }
        PingScoreboard.ping(player, g);
        Location loc = player.getLocation();
        WaypointHook.addGuildWaypoint(player, "POMOC " + player.getName(), loc, 0xFFAA00)
                .ifPresent(wp -> Bukkit.getScheduler().runTaskLater(module.plugin(),
                        () -> WaypointHook.removeGuildWaypoint(wp), 20L * 60));
        for (UUID id : g.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendMessage("§6§l[PING] §e" + player.getName() + " §7potrzebuje pomocy! §f"
                        + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            }
        }
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("§cUzycie: /g zaloz <tag>"); return; }
        String tag = args[1];
        if (!tag.matches("[A-Za-z0-9]{2,5}")) { player.sendMessage("§cTag 2-5 znakow."); return; }
        if (guildManager.getGuildByPlayer(player.getUniqueId()) != null) { player.sendMessage("§cJestes juz w gildii!"); return; }
        if (guildManager.getGuild(tag) != null) { player.sendMessage("§cTaka gildia istnieje!"); return; }
        if (guildManager.getGuildAt(player.getLocation()) != null) { player.sendMessage("§cStoisz na terenie innej gildii!"); return; }
        
        // 1. Teleportuj gracza na Y=40
        Location tpLoc = player.getLocation();
        tpLoc.setY(40);
        player.teleport(tpLoc);
        
        // 2. Utwórz gildię
        boolean ok = guildManager.createGuild(tag, player.getUniqueId(), tpLoc, DEFAULT_RADIUS);
        if (ok) {
            player.sendMessage("§aZalozono gildie §e" + tag.toUpperCase() + " §a(r=" + DEFAULT_RADIUS + ").");
            territoryBarManager.update(player);
            
            // 3. Generuj monument (względem pozycji gracza na Y=40)
            EggMonument.generate(player.getLocation());
            player.sendMessage("§6§lStworzono monument gildii!");
        }
        else player.sendMessage("§cNie udalo sie zalozyc gildii.");
    }

    private void handleLeave(Player player) {
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null) { player.sendMessage("§cNie jestes w gildii!"); return; }
        if (g.isOwner(player.getUniqueId())) { player.sendMessage("§cLider: uzyj /g rozwiaz lub /g lider."); return; }
        g.removeMember(player.getUniqueId());
        guildManager.markDirty(g.getTag()); guildManager.save();
        player.sendMessage("§aOpusciles §e" + g.getTag());
    }

    private void handleDisband(Player player) {
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null) { player.sendMessage("§cNie jestes w gildii!"); return; }
        if (!g.isOwner(player.getUniqueId())) { player.sendMessage("§cTylko lider!"); return; }
        if (module.getWarManager().getActiveWarCount(g.getTag()) > 0) {
            player.sendMessage("§cNie mozesz rozwiazac gildii podczas wojny!");
            return;
        }
        guildManager.disband(g);
        player.sendMessage("§aRozwiazano gildie §e" + g.getTag());
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("§cUzycie: /g wyrzuc <nick>"); return; }
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null) { player.sendMessage("§cNie jestes w gildii!"); return; }
        if (!g.isLeaderOrDeputy(player.getUniqueId())) { player.sendMessage("§cBrak uprawnien!"); return; }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null) { player.sendMessage("§cGracz online?"); return; }
        if (!g.isMember(t.getUniqueId()) || g.isOwner(t.getUniqueId())) { player.sendMessage("§cNie mozna."); return; }
        if (g.isDeputy(t.getUniqueId()) && !g.isOwner(player.getUniqueId())) { player.sendMessage("§cTylko lider zrzuca zastepce."); return; }
        g.removeMember(t.getUniqueId());
        guildManager.markDirty(g.getTag()); guildManager.save();
        t.sendMessage("§cWyrzucono Cie z §e" + g.getTag());
        player.sendMessage("§aWyrzucono §e" + t.getName());
    }

    private void handleInvite(Player player, String[] args) {
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null) { player.sendMessage("§cNie jestes w gildii!"); return; }
        if (!g.isLeaderOrDeputy(player.getUniqueId())) { player.sendMessage("§cBrak uprawnien!"); return; }
        if (args.length < 2) { player.sendMessage("§cUzycie: /g zapros <nick|wand>"); return; }
        if (args[1].equalsIgnoreCase("wand") || args[1].equalsIgnoreCase("rozdzka")) {
            ItemStack wand = new ItemStack(Material.STICK);
            ItemMeta meta = wand.getItemMeta();
            meta.setDisplayName(WAND_NAME);
            List<String> lore = new ArrayList<>();
            lore.add("§7PPM na gracza = zaproszenie.");
            lore.add("§7Gildia: §e" + g.getTag());
            lore.add("§8Wazna 5 min.");
            meta.setLore(lore);
            wand.setItemMeta(meta);
            player.getInventory().addItem(wand);
            module.getInviteWandUsers().put(player.getUniqueId(), System.currentTimeMillis() + 300_000L);
            player.sendMessage("§aMasz rozdzke zaproszen.");
            return;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null) { player.sendMessage("§cGracz offline."); return; }
        tryInvite(player, g, t);
    }

    private boolean tryInvite(Player leader, Guild g, Player t) {
        if (g.isMember(t.getUniqueId())) { leader.sendMessage("§cJuz w gildii."); return false; }
        if (guildManager.getGuildByPlayer(t.getUniqueId()) != null) { leader.sendMessage("§cW innej gildii."); return false; }
        ItemCost cost = module.getInviteCost();
        if (!cost.isEmpty() && (!cost.has(leader) || !cost.take(leader))) {
            leader.sendMessage("§cBrak: " + cost.describeInline());
            return false;
        }
        g.addInvite(t.getUniqueId(), System.currentTimeMillis() + INVITE_EXPIRE_MS);
        leader.sendMessage("§aZaproszono §e" + t.getName());
        t.sendMessage("§aZaproszenie do §e" + g.getTag() + "§a. /g dolacz " + g.getTag());
        return true;
    }

    public void inviteFromWand(Player leader, Player target) {
        Guild g = guildManager.getGuildByPlayer(leader.getUniqueId());
        if (g == null || !g.isLeaderOrDeputy(leader.getUniqueId())) return;
        tryInvite(leader, g, target);
    }

    private void handleAccept(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("§cUzycie: /g dolacz <tag>"); return; }
        if (guildManager.getGuildByPlayer(player.getUniqueId()) != null) { player.sendMessage("§cJuz w gildii!"); return; }
        Guild g = guildManager.getGuild(args[1]);
        if (g == null) { player.sendMessage("§cBrak gildii."); return; }
        if (!g.hasInvite(player.getUniqueId())) { player.sendMessage("§cBrak zaproszenia."); return; }
        g.addMember(player.getUniqueId());
        guildManager.markDirty(g.getTag()); guildManager.save();
        guildManager.checkAllianceLimit(g);
        player.sendMessage("§aDolaczono do §e" + g.getTag());
        territoryBarManager.update(player);
    }

    private void handleDeny(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("§cUzycie: /g odrzuc <tag>"); return; }
        Guild g = guildManager.getGuild(args[1]);
        if (g == null) { player.sendMessage("§cBrak gildii."); return; }
        g.removeInvite(player.getUniqueId());
        player.sendMessage("§7Odrzucono.");
    }

    private void handleInfo(Player player, String[] args) {
        Guild g = args.length >= 2 ? guildManager.getGuild(args[1]) : guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null) { player.sendMessage("§cBrak gildii."); return; }
        player.sendMessage("§6Gildia §e" + g.getTag() + " §7| lider: §f" + nameOf(g.getOwner())
                + " §7| czlonkow: §f" + g.getMembers().size() + " §7| teren r=" + g.getRadius()
                + " §7| pkt: §f" + g.getRankPoints());
        if (g.hasHome()) player.sendMessage("§7Dom: " + (int) g.getHomeX() + ", " + (int) g.getHomeY() + ", " + (int) g.getHomeZ());
        for (War w : module.getWarManager().getActiveWarsOf(g.getTag())) {
            player.sendMessage("§cWojna z §e" + w.getOpponent(g.getTag()) + " §c(koniec za " + WarGuiFmt(w.getRemainingMs()) + ")");
        }
    }

    private static String WarGuiFmt(long ms) {
        long h = ms / 3_600_000, m = (ms % 3_600_000) / 60_000;
        return h + "h " + m + "m";
    }

    private void handleList(Player player) {
        if (guildManager.getAll().isEmpty()) { player.sendMessage("§7Brak gildii."); return; }
        for (Guild g : guildManager.getAll())
            player.sendMessage("§e" + g.getTag() + " §7- " + g.getMembers().size() + " osob §7| pkt: §f" + g.getRankPoints());
    }

    private void handleLeader(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("§cUzycie: /g lider <nick>"); return; }
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null || !g.isOwner(player.getUniqueId())) { player.sendMessage("§cTylko lider."); return; }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null || !g.isMember(t.getUniqueId())) { player.sendMessage("§cGracz online i w gildii?"); return; }
        g.setOwner(t.getUniqueId());
        guildManager.markDirty(g.getTag()); guildManager.save();
        player.sendMessage("§aNowy lider: §e" + t.getName());
    }

    private void handleDeputy(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("§cUzycie: /g zastepca <nick>"); return; }
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null || !g.isOwner(player.getUniqueId())) { player.sendMessage("§cTylko lider."); return; }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null || !g.isMember(t.getUniqueId()) || g.isOwner(t.getUniqueId())) { player.sendMessage("§cNie mozna."); return; }
        if (g.isDeputy(t.getUniqueId())) { g.removeDeputy(t.getUniqueId()); player.sendMessage("§aOdebrano zastepce."); }
        else { g.addDeputy(t.getUniqueId()); player.sendMessage("§aNadano zastepce."); }
        guildManager.markDirty(g.getTag()); guildManager.save();
    }

    private void handleSetHome(Player player) {
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null) { player.sendMessage("§cNie jestes w gildii!"); return; }
        if (!g.isLeaderOrDeputy(player.getUniqueId())) { player.sendMessage("§cBrak uprawnien!"); return; }
        if (!g.isInTerritory(player.getLocation())) { player.sendMessage("§cTylko na terenie gildii!"); return; }
        g.setHome(player.getLocation());
        guildManager.markDirty(g.getTag()); guildManager.save();
        player.sendMessage("§aUstawiono dom.");
    }

    private void handleHome(Player player) {
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null) { player.sendMessage("§cNie jestes w gildii!"); return; }
        if (g.isInTerritory(player.getLocation())) { player.sendMessage("§cNie na wlasnym terenie."); return; }
        Location home = g.getHome();
        if (home == null) { player.sendMessage("§cBrak domu."); return; }
        TeleportUtil.teleportCountdown(module.plugin(), player, home, TELEPORT_SECONDS, "dom gildii " + g.getTag());
    }

    private void handleRegen(Player player) {
        if (guildManager.getGuildByPlayer(player.getUniqueId()) == null) { player.sendMessage("§cNie jestes w gildii!"); return; }
        regenManager.startManualRegen(player);
    }

    private void handleSetRaidBase(Player player) {
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null) { player.sendMessage("§cNie jestes w gildii!"); return; }
        if (!g.isLeaderOrDeputy(player.getUniqueId())) { player.sendMessage("§cBrak uprawnien!"); return; }

        Long cd = ubwCooldown.get(player.getUniqueId());
        if (cd != null && System.currentTimeMillis() < cd) {
            player.sendMessage("§cCooldown bazy wypadowej: jeszcze §e" + ((cd - System.currentTimeMillis()) / 1000) + "s§c.");
            return;
        }

        Location loc = player.getLocation();
        if (g.isInTerritory(loc)) { player.sendMessage("§cNie na wlasnym terenie!"); return; }
        if (guildManager.getGuildAt(loc) != null) { player.sendMessage("§cNie na obcym terenie!"); return; }

        Guild target = null;
        for (War w : module.getWarManager().getActiveWarsOf(g.getTag())) {
            Guild og = guildManager.getGuild(w.getOpponent(g.getTag()));
            if (og == null) continue;
            double d = og.distanceToBorder(loc);
            if (d > 0 && d <= Const.RAID_NEAR_BLOCKS) { target = og; break; }
        }
        if (target == null) {
            player.sendMessage("§cBaze wypadowa mozesz ustawic tylko w poblizu terenu gildii, z ktora masz WOJNE (<=§e"
                    + (int) Const.RAID_NEAR_BLOCKS + "§c blokow)!");
            return;
        }

        Block under = loc.getBlock().getRelative(BlockFace.DOWN);
        under.setType(Material.OBSIDIAN);
        Location baseLoc = under.getLocation();

        if (g.getRaidWaypointId() != null) WaypointHook.removeGuildWaypoint(g.getRaidWaypointId());
        UUID wp = WaypointHook.addGuildWaypoint(player, "Baza wypadowa", baseLoc, 0xFF5555).orElse(null);
        g.setRaidBase(baseLoc, Const.RAID_DURATION_MS, wp);
        guildManager.markDirty(g.getTag()); guildManager.save();

        ubwCooldown.put(player.getUniqueId(), System.currentTimeMillis() + Const.RAID_COOLDOWN_MS);

        player.sendMessage("§aUstawiono baze wypadowa na 1h (blok pod Toba = obsydian).");
        player.sendMessage("§7Cel wojny: §e" + target.getTag() + " §7| /g bw = TP.");
        notifyOnlineMembers(g, "§cBaza wypadowa ustawiona! §7(/g bw)");
    }

    private void handleRaidBaseTp(Player player) {
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null) { player.sendMessage("§cNie jestes w gildii!"); return; }
        if (!g.hasActiveRaidBase()) { player.sendMessage("§cBrak bazy. /g ubw."); return; }
        Location dest = g.getRaidBase();
        if (dest == null) { player.sendMessage("§cBrak bazy."); return; }
        TeleportUtil.teleportCountdown(module.plugin(), player, dest, TELEPORT_SECONDS, "baza wypadowa");
    }

    private void handleAlliance(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§e/g sojusz <tag> | akceptuj | odrzuc | rozwiaz | lista");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "akceptuj", "accept" -> allianceAccept(player, args);
            case "odrzuc", "deny" -> allianceDeny(player, args);
            case "rozwiaz", "break" -> allianceBreak(player, args);
            case "lista", "list" -> allianceList(player);
            default -> allianceRequest(player, args[1]);
        }
    }

    private void allianceRequest(Player player, String targetTag) {
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null || !own.isLeaderOrDeputy(player.getUniqueId())) { player.sendMessage("§cBrak uprawnien."); return; }
        long now = System.currentTimeMillis();
        Long last = allianceCooldown.get(player.getUniqueId());
        if (last != null && now - last < ALLIANCE_CD_MS) { player.sendMessage("§cCooldown."); return; }
        Guild other = guildManager.getGuild(targetTag);
        if (other == null || other.getTag().equals(own.getTag())) { player.sendMessage("§cBrak gildii."); return; }
        if (own.isAlliedWith(other.getTag()) || !own.getAllies().isEmpty() || !other.getAllies().isEmpty()) { player.sendMessage("§cLimit 1 sojuszu."); return; }
        if (own.getMembers().size() + other.getMembers().size() > ALLIANCE_MAX_MEMBERS) { player.sendMessage("§cLimit osob."); return; }
        if (!ALLIANCE_COST.has(player) || !ALLIANCE_COST.take(player)) { player.sendMessage("§cBrak: " + ALLIANCE_COST.describeInline()); return; }
        allianceCooldown.put(player.getUniqueId(), now);
        other.addAllianceRequest(own.getTag(), now + ALLIANCE_EXPIRE_MS);
        player.sendMessage("§aProsba wyslana do §e" + other.getTag());
    }

    private void allianceAccept(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage("§cUzycie: /g sojusz akceptuj <tag>"); return; }
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null || !own.isLeaderOrDeputy(player.getUniqueId())) return;
        Guild other = guildManager.getGuild(args[2]);
        if (other == null || !own.hasAllianceRequestFrom(other.getTag())) { player.sendMessage("§cBrak prosby."); return; }
        if (!own.getAllies().isEmpty() || !other.getAllies().isEmpty()) { player.sendMessage("§cLimit 1 sojuszu."); return; }
        if (!ALLIANCE_COST.has(player) || !ALLIANCE_COST.take(player)) { player.sendMessage("§cBrak przedmiotow."); return; }
        own.removeAllianceRequest(other.getTag());
        own.addAlly(other.getTag()); other.addAlly(own.getTag());
        guildManager.markDirty(own.getTag()); guildManager.markDirty(other.getTag()); guildManager.save();
        player.sendMessage("§aSojusz z §e" + other.getTag());
    }

    private void allianceDeny(Player player, String[] args) {
        if (args.length < 3) return;
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null) return;
        own.removeAllianceRequest(args[2]);
        player.sendMessage("§7Odrzucono.");
    }

    private void allianceBreak(Player player, String[] args) {
        if (args.length < 3) return;
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null || !own.isAlliedWith(args[2])) { player.sendMessage("§cBrak sojuszu."); return; }
        Guild other = guildManager.getGuild(args[2]);
        own.removeAlly(other.getTag()); other.removeAlly(own.getTag());
        guildManager.markDirty(own.getTag()); guildManager.markDirty(other.getTag()); guildManager.save();
        player.sendMessage("§cZerwano sojusz.");
    }

    private void allianceList(Player player) {
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null || own.getAllies().isEmpty()) { player.sendMessage("§7Brak sojusznikow."); return; }
        player.sendMessage("§6Sojusze: " + String.join(", ", own.getAllies()));
    }

    private void notifyOnlineMembers(Guild guild, String msg) {
        for (UUID id : guild.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.sendMessage(msg);
        }
    }

    private static String nameOf(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n != null ? n : uuid.toString().substring(0, 8);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("zaloz", "zapros", "dolacz", "odrzuc", "opusc", "wyrzuc", "rozwiaz",
                    "info", "lista", "lider", "zastepca", "ustawdom", "dom", "ubw", "bw", "regeneruj", "panel",
                    "peryskop", "sojusz", "wojna", "pp", "pomoc");
            String in = args[0].toLowerCase();
            return subs.stream().filter(s -> s.startsWith(in)).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("dolacz") || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("odrzuc"))) {
            String in = args[1].toLowerCase();
            return guildManager.getAll().stream().map(Guild::getTag).filter(t -> t.toLowerCase().startsWith(in)).collect(Collectors.toList());
        }
        return List.of();
    }
}
