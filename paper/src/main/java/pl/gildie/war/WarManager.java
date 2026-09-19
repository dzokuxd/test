package pl.gildie.war;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.Const;
import pl.gildie.GildieModule;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;
import pl.gildie.util.WaypointHook;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WarManager {
    private static final Gson gson = new Gson();
    private static final int HISTORY_CAP = 80;
    public static final long MIN_DURATION_MS = 60L * 60 * 1000;
    public static final long MAX_DURATION_MS = 3L * 60 * 60 * 1000;

    private final GildieModule module;
    private final JavaPlugin plugin;
    private final GuildManager guildManager;
    private final Logger log;

    private final Map<String, War> activeWars = new ConcurrentHashMap<>();
    private final Map<UUID, War> warsById = new ConcurrentHashMap<>();
    private final List<War> history = new ArrayList<>();
    private final Map<UUID, UUID> bannerWaypoints = new ConcurrentHashMap<>();
    private final Set<UUID> bannerCarriers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastEggHitAt = new ConcurrentHashMap<>();
    private volatile boolean warsDirty;
    private static Attribute SCALE_ATTR;

    static {
        try { SCALE_ATTR = (Attribute) Attribute.class.getField("GENERIC_SCALE").get(null); }
        catch (Throwable ignored) { SCALE_ATTR = null; }
    }

    public WarManager(GildieModule module, GuildManager guildManager) {
        this.module = module;
        this.plugin = module.plugin();
        this.guildManager = guildManager;
        this.log = plugin.getLogger();
        BannerItem.init(plugin);
        load();
    }

    private String pairKey(String a, String b) {
        a = a.toUpperCase(); b = b.toUpperCase();
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    public boolean isBannerCarrier(UUID id) { return id != null && bannerCarriers.contains(id); }
    public Optional<War> getActiveWarBetween(String t1, String t2) { return Optional.ofNullable(activeWars.get(pairKey(t1, t2))); }

    public Optional<War> getActiveWarOf(String tag) {
        if (tag == null) return Optional.empty();
        tag = tag.toUpperCase();
        for (War w : activeWars.values()) if (w.isActive() && w.isParticipant(tag)) return Optional.of(w);
        return Optional.empty();
    }

    public List<War> getActiveWarsOf(String tag) {
        List<War> out = new ArrayList<>();
        if (tag == null) return out;
        tag = tag.toUpperCase();
        for (War w : activeWars.values()) if (w.isActive() && w.isParticipant(tag)) out.add(w);
        return out;
    }

    public int getActiveWarCount(String tag) { return getActiveWarsOf(tag).size(); }

    public Collection<War> getActiveWars() { return activeWars.values().stream().filter(War::isActive).collect(Collectors.toList()); }
    public List<War> getHistory() { synchronized (history) { return new ArrayList<>(history); } }
    public Optional<War> getWarById(UUID id) { return id == null ? Optional.empty() : Optional.ofNullable(warsById.get(id)); }

    public boolean declareWar(Player leader, String targetTag, long durationMs) {
        Guild attacker = guildManager.getGuildByPlayer(leader.getUniqueId());
        if (attacker == null) { leader.sendMessage("§cNie nalezysz do zadnej gildii."); return false; }
        if (!attacker.isLeaderOrDeputy(leader.getUniqueId())) { leader.sendMessage("§cTylko lider lub zastepca."); return false; }
        Guild defender = guildManager.getGuild(targetTag);
        if (defender == null) { leader.sendMessage("§cNie znaleziono gildii §f" + targetTag); return false; }
        if (attacker.getTag().equalsIgnoreCase(defender.getTag())) { leader.sendMessage("§cNie sobie."); return false; }
        if (attacker.isAlliedWith(defender.getTag())) { leader.sendMessage("§cNie sojusznikowi."); return false; }
        if (getActiveWarCount(attacker.getTag()) >= Const.WAR_MAX_ACTIVE_PER_GUILD) { leader.sendMessage("§cTwoja gildia ma juz maks. " + Const.WAR_MAX_ACTIVE_PER_GUILD + " wojny."); return false; }
        if (getActiveWarCount(defender.getTag()) >= Const.WAR_MAX_ACTIVE_PER_GUILD) { leader.sendMessage("§cTa gildia ma juz maks. wojny."); return false; }
        if (getActiveWarBetween(attacker.getTag(), defender.getTag()).isPresent()) { leader.sendMessage("§cJuz jestescie w wojnie."); return false; }
        if (!TntManager.isTntEnabled()) { leader.sendMessage("§cWojne mozna wypowiedziec tylko gdy TNT jest wlaczone (16-21)."); return false; }
        durationMs = Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, durationMs));

        War war = new War(UUID.randomUUID(), attacker.getTag(), defender.getTag(), durationMs);
        war.setDeclarerName(leader.getName());
        activeWars.put(pairKey(attacker.getTag(), defender.getTag()), war);
        warsById.put(war.getId(), war);
        synchronized (history) { history.add(war); }
        markDirty(); save();

        Bukkit.broadcastMessage("§c§l[WOJNA] §e" + attacker.getTag() + " §cwypowiedziala wojne §e" + defender.getTag()
                + " §cna §f" + String.format("%.0f", durationMs / 3600000.0) + "h§c! (deklarujacy: §f" + leader.getName() + "§c)");
        notifyGuild(defender, "§c§lWOJNA! §e" + attacker.getTag() + " §catakuje!",
                "§7Chroncie jajo (HP " + defender.getEggHp() + "/" + defender.getMaxEggHp() + ")", true, Sound.ENTITY_WITHER_SPAWN);
        notifyGuild(attacker, "§aWypowiedzieliscie wojne §e" + defender.getTag(), "§7Zniszczcie jajo wroga", false, Sound.ENTITY_PLAYER_LEVELUP);
        return true;
    }

    public boolean handleEggHit(Player player, Guild eggGuild) {
        Guild attackerGuild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (attackerGuild == null) return false;
        if (attackerGuild.getTag().equals(eggGuild.getTag())) return false;
        Optional<War> opt = getActiveWarBetween(attackerGuild.getTag(), eggGuild.getTag());
        if (opt.isEmpty() || !opt.get().isActive()) return false;
        if (!TntManager.isTntEnabled()) { player.sendMessage("§cJajo tylko gdy TNT wlaczone (16-21)."); return true; }

        long now = System.currentTimeMillis();
        Long prev = lastEggHitAt.put(player.getUniqueId(), now);
        if (prev != null && now - prev < 80L) return true;

        War war = opt.get();
        if (eggGuild.getEggHp() <= 0) {
            if (war.getActiveBannerId() == null) startConquest(player, war, eggGuild, attackerGuild);
            else player.sendMessage("§cSztandar tej gildii jest juz w grze.");
            return true;
        }
        war.getStats(attackerGuild.getTag()).addEggHit();
        boolean broken = eggGuild.damageEgg(1);
        guildManager.markDirty();
        module.getEggHologram().updateHp(eggGuild);
        notifyEggAttack(eggGuild, player, attackerGuild, eggGuild.getEggHp(), eggGuild.getMaxEggHp(), broken);
        if (broken) { ensureEggBlock(eggGuild); startConquest(player, war, eggGuild, attackerGuild); }
        markDirty();
        return true;
    }

    private void notifyEggAttack(Guild def, Player attacker, Guild att, int hp, int max, boolean broken) {
        String bar = hp + "/" + max;
        String action = broken ? "§4§lJAJO ROZBITE! §cSztandar zabral §f" + attacker.getName()
                : "§c⚠ Jajo atakowane! §f" + bar + " §7(" + attacker.getName() + ")";
        for (UUID id : def.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) sendActionBar(p, action);
        }
        if (broken) {
            notifyGuild(def, "§4§lJAJO ROZBITE!", "§c" + attacker.getName() + " §czabral sztandar!", true, Sound.ENTITY_ENDER_DRAGON_GROWL);
            return;
        }
        int step = Math.max(1, Const.WAR_EGG_ALERT_EVERY);
        if (hp > 0 && hp % step == 0) {
            notifyGuild(def, "§c⚠ JAJO ATAKOWANE", "§cHP: §f" + bar + " §7- §e" + attacker.getName(), true, Sound.BLOCK_NOTE_BLOCK_BASS);
        }
    }

    private void notifyGuild(Guild guild, String title, String subtitle, boolean useTitle, Sound sound) {
        if (guild == null) return;
        for (UUID id : guild.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            p.sendMessage("§c§l[GILDIA] §r" + title + (subtitle == null || subtitle.isEmpty() ? "" : " §8- " + subtitle));
            if (useTitle) p.sendTitle(title, subtitle == null ? "" : subtitle, 5, 40, 10);
            if (sound != null) p.playSound(p.getLocation(), sound, 0.8f, 1.0f);
        }
    }

    private static void sendActionBar(Player p, String msg) {
        try { p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg)); }
        catch (Throwable t) { p.sendMessage(msg); }
    }

    private void ensureEggBlock(Guild g) {
        Location loc = g.getEggLocation();
        if (loc == null || loc.getWorld() == null) return;
        org.bukkit.Material mat;
        try { mat = org.bukkit.Material.valueOf(Const.EGG_MATERIAL); }
        catch (Exception e) { mat = org.bukkit.Material.DRAGON_EGG; }
        if (loc.getBlock().getType() != mat) loc.getBlock().setType(mat, false);
    }

    public void tickEggRegen() {
        if (TntManager.isTntEnabled()) return;
        boolean any = false;
        for (Guild g : guildManager.getAll()) {
            if (!g.hasEgg()) continue;
            if (g.regenEgg(Const.WAR_EGG_REGEN)) { any = true; module.getEggHologram().updateHp(g); }
        }
        if (any) guildManager.markDirty();
    }

    private void startConquest(Player player, War war, Guild conquered, Guild conqueror) {
        if (war.getActiveBannerId() != null) { player.sendMessage("§cSztandar juz w grze."); return; }
        UUID bannerId = UUID.randomUUID();
        ItemStack banner = BannerItem.create(war.getId(), bannerId, conquered.getTag(), conqueror.getTag());
        ItemStack old = player.getInventory().getHelmet();
        if (old != null && old.getType() != org.bukkit.Material.AIR) player.getWorld().dropItemNaturally(player.getLocation(), old);
        player.getInventory().setHelmet(banner);
        war.setActiveBannerId(bannerId);
        war.setBannerCarrierGuild(conqueror.getTag());
        war.setBannerCarrierPlayer(player.getUniqueId());
        if (conqueror.getTag().equals(war.getAttackerTag())) war.setConquestByAttacker(true);
        else war.setConquestByDefender(true);
        bannerCarriers.add(player.getUniqueId());
        setScale(player, 1.6);
        Location top = findHighestBlock(conquered);
        if (top != null) player.teleport(top.add(0.5, 1, 0.5));
        WaypointHook.startGlobalLiveTrack(player, " SZTANDAR " + conquered.getTag());
        Bukkit.broadcastMessage("§c§l[WOJNA] §e" + player.getName() + " §cprzejal sztandar §e" + conquered.getTag() + "§c!");
        notifyGuild(conquered, "§4SZTANDAR SKRADZIONY!", "§cZabijcie §f" + player.getName(), true, Sound.ENTITY_WITHER_SPAWN);
        markDirty(); save();
    }

    private Location findHighestBlock(Guild g) {
        org.bukkit.World w = Bukkit.getWorld(g.getWorldName());
        if (w == null) return null;
        int x = (int) Math.floor(g.getX()), z = (int) Math.floor(g.getZ());
        return new Location(w, x, w.getHighestBlockYAt(x, z), z);
    }

    public void tryCompleteConquest(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (!BannerItem.isBanner(helmet)) { bannerCarriers.remove(player.getUniqueId()); return; }
        UUID warId = BannerItem.getWarId(helmet), bannerId = BannerItem.getBannerId(helmet);
        String from = BannerItem.getFromGuild(helmet);
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null || !own.hasEgg()) return;
        Location egg = own.getEggLocation();
        if (egg == null || egg.getWorld() == null || !egg.getWorld().equals(player.getWorld())) return;
        if (egg.distanceSquared(player.getLocation()) > 9.0) return;
        Optional<War> opt = getWarById(warId);
        if (opt.isEmpty() || !opt.get().isActive() || !opt.get().isParticipant(own.getTag())) return;
        War war = opt.get();
        if (war.getActiveBannerId() != null && bannerId != null && !war.getActiveBannerId().equals(bannerId)) return;

        if (from != null && from.equalsIgnoreCase(own.getTag())) { recoverBanner(player, war, bannerId, own); return; }

        stripBanner(player, bannerId);
        war.clearBanner();
        Guild conquered = guildManager.getGuild(from);
        if (conquered != null && conquered.hasEgg()) {
            conquered.setEggHp(Math.max(1, conquered.getMaxEggHp() / 4));
            guildManager.markDirty();
            module.getEggHologram().updateHp(conquered);
            ensureEggBlock(conquered);
        }
        Bukkit.broadcastMessage("§a§l[WOJNA] §e" + own.getTag() + " §azaliczyla podbicie §e" + from + "§a! Wojna trwa.");
        notifyGuild(own, "§aPodbicie zaliczone!", "§7Wojna trwa.", true, Sound.ENTITY_PLAYER_LEVELUP);
        markDirty(); save();
    }

    public void tickBannerWaypoints() {
        for (UUID id : new ArrayList<>(bannerCarriers)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) { WaypointHook.stopGlobalLiveTrack(id); continue; }
            if (!BannerItem.isBanner(p.getInventory().getHelmet())) {
                bannerCarriers.remove(id);
                WaypointHook.stopGlobalLiveTrack(id);
            }
        }
    }

    private void recoverBanner(Player player, War war, UUID bannerId, Guild owners) {
        stripBanner(player, bannerId);
        war.clearBanner();
        int restored = Math.max(1, owners.getMaxEggHp() / 4);
        owners.setEggHp(restored);
        guildManager.markDirty();
        module.getEggHologram().updateHp(owners);
        ensureEggBlock(owners);
        Bukkit.broadcastMessage("§a§l[WOJNA] §e" + owners.getTag() + " §aodzyskala sztandar!");
        markDirty(); save();
    }

    private void stripBanner(Player player, UUID bannerId) {
        player.getInventory().setHelmet(null);
        setScale(player, 1.0);
        bannerCarriers.remove(player.getUniqueId());
        WaypointHook.stopGlobalLiveTrack(player.getUniqueId());
        UUID wp = bannerWaypoints.remove(bannerId);
        if (wp != null) WaypointHook.removeWaypoint(wp);
    }

    public void handleBannerDeath(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (!BannerItem.isBanner(helmet)) return;
        UUID bannerId = BannerItem.getBannerId(helmet), warId = BannerItem.getWarId(helmet);
        player.getInventory().setHelmet(null);
        setScale(player, 1.0);
        bannerCarriers.remove(player.getUniqueId());
        WaypointHook.stopGlobalLiveTrack(player.getUniqueId());
        Location drop = player.getLocation();
        Item item = player.getWorld().dropItemNaturally(drop, helmet);
        item.setPickupDelay(20);
        UUID old = bannerWaypoints.remove(bannerId);
        if (old != null) WaypointHook.removeWaypoint(old);
        UUID nw = WaypointHook.addGlobalWaypoint("Sztandar (upadl)", drop, 0xFF5555);
        if (nw != null) bannerWaypoints.put(bannerId, nw);
        getWarById(warId).ifPresent(w -> w.setBannerCarrierPlayer(null));
        Bukkit.broadcastMessage("§c§l[WOJNA] §eSztandar upadl!");
        markDirty();
    }

    public void handleBannerPickup(Player player, ItemStack banner) {
        if (!BannerItem.isBanner(banner)) return;
        UUID bannerId = BannerItem.getBannerId(banner), warId = BannerItem.getWarId(banner);
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null) { player.getWorld().dropItemNaturally(player.getLocation(), banner); return; }
        Optional<War> wo = getWarById(warId);
        if (wo.isEmpty() || !wo.get().isActive() || !wo.get().isParticipant(g.getTag())) return;
        ItemStack old = player.getInventory().getHelmet();
        if (old != null && old.getType() != org.bukkit.Material.AIR && !BannerItem.isBanner(old))
            player.getWorld().dropItemNaturally(player.getLocation(), old);
        player.getInventory().setHelmet(banner.clone());
        wo.ifPresent(war -> { war.setBannerCarrierPlayer(player.getUniqueId()); war.setBannerCarrierGuild(g.getTag()); });
        bannerCarriers.add(player.getUniqueId());
        UUID ow = bannerWaypoints.remove(bannerId);
        if (ow != null) WaypointHook.removeWaypoint(ow);
        WaypointHook.startGlobalLiveTrack(player, " SZTANDAR " + BannerItem.getFromGuild(banner));
        setScale(player, 1.6);
        markDirty();
    }

    public void tick() {
        List<War> toEnd = new ArrayList<>();
        for (War w : activeWars.values()) if (w.isExpired()) toEnd.add(w);
        for (War w : toEnd) endWar(w);
    }

    public void flush() { if (warsDirty) save(); guildManager.saveIfDirty(); }
    private void markDirty() { warsDirty = true; }

    // ── ZASADY KONCA WOJNY ─────────────────────────────────────────────────
    // 1) brak podbic i brak zabojstw -> przegrywa atakujacy
    // 2) podbicie > zabojstwa
    // 3) brak podbic -> wiecej zabojstw wygrywa (remis = obronca)
    private void endWar(War war) {
        String att = war.getAttackerTag(), def = war.getDefenderTag();
        boolean cA = war.isConquestByAttacker(), cD = war.isConquestByDefender();
        int kA = war.getStats(att).getKills(), kD = war.getStats(def).getKills();

        String winner;
        boolean byConquest;
        if (cA && !cD) { winner = att; byConquest = true; }
        else if (cD && !cA) { winner = def; byConquest = true; }
        else if (cA) { winner = kA >= kD ? att : def; byConquest = true; }
        else { winner = kA > kD ? att : def; byConquest = false; }

        war.setState(byConquest ? War.State.ENDED_CONQUEST : (kA == kD ? War.State.ENDED_TIMEOUT : War.State.ENDED_KILLS));
        war.setEndTime(System.currentTimeMillis());
        activeWars.remove(pairKey(att, def));
        clearActiveBannerWorld(war);

        Guild wG = guildManager.getGuild(winner);
        Guild lG = guildManager.getGuild(war.getOpponent(winner));
        String reason = byConquest ? "podbicie serca" : (kA == kD ? "brak aktywnosci (atakujacy przegrywa)" : "wiecej zabojstw");

        int pct = 0, transfer = 0;
        if (wG != null && lG != null) {
            int wk = war.getStats(winner).getKills(), lk = war.getStats(war.getOpponent(winner)).getKills();
            pct = Const.WAR_TRANSFER_BASE_PERCENT;
            if ((winner.equals(att) && cA) || (winner.equals(def) && cD)) pct += Const.WAR_TRANSFER_CONQUEST_PERCENT;
            if (wk + lk > 0) pct += (int) Math.round(Const.WAR_TRANSFER_KILLS_PERCENT * ((double) wk / (wk + lk)));
            pct = Math.min(Const.WAR_TRANSFER_CAP_PERCENT, pct);
            transfer = lG.getRankPoints() * pct / 100;
            lG.setRankPoints(lG.getRankPoints() - transfer);
            wG.setRankPoints(wG.getRankPoints() + transfer);
            guildManager.markDirty(wG.getTag());
            guildManager.markDirty(lG.getTag());
        }

        Bukkit.broadcastMessage("§e§l[WOJNA] §f" + att + " vs " + def + " §ezakonczona. Zwyciezca: §a" + winner
                + " §7(" + reason + ")");
        if (transfer > 0) {
            Bukkit.broadcastMessage("§7Transfer punktow: §c" + lG.getTag() + " §7-> §a" + wG.getTag()
                    + " §7" + pct + "% = §f" + transfer + " pkt");
        }
        markDirty(); save();
    }

    private void clearActiveBannerWorld(War war) {
        UUID carrierId = war.getBannerCarrierPlayer();
        if (carrierId != null) {
            Player p = Bukkit.getPlayer(carrierId);
            if (p != null && BannerItem.isBanner(p.getInventory().getHelmet())) p.getInventory().setHelmet(null);
            if (p != null) setScale(p, 1.0);
            bannerCarriers.remove(carrierId);
            WaypointHook.stopGlobalLiveTrack(carrierId);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack h = p.getInventory().getHelmet();
            if (BannerItem.isBanner(h) && war.getId().equals(BannerItem.getWarId(h))) {
                p.getInventory().setHelmet(null);
                setScale(p, 1.0);
                bannerCarriers.remove(p.getUniqueId());
                WaypointHook.stopGlobalLiveTrack(p.getUniqueId());
            }
        }
        UUID bannerId = war.getActiveBannerId();
        if (bannerId != null) {
            UUID wp = bannerWaypoints.remove(bannerId);
            if (wp != null) WaypointHook.removeWaypoint(wp);
            for (World w : Bukkit.getWorlds())
                for (Item it : w.getEntitiesByClass(Item.class))
                    if (BannerItem.isBanner(it.getItemStack()) && war.getId().equals(BannerItem.getWarId(it.getItemStack()))) it.remove();
        }
        war.clearBanner();
    }

    private static void setScale(Player player, double v) {
        if (SCALE_ATTR == null || player == null) return;
        try { AttributeInstance i = player.getAttribute(SCALE_ATTR); if (i != null) i.setBaseValue(v); }
        catch (Throwable ignored) { }
    }

    public void save() {
        Map<String, JsonArray> byAtt = snapshotByAttacker();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeAll(byAtt));
        warsDirty = false;
    }

    public void saveSync() { writeAll(snapshotByAttacker()); warsDirty = false; }

    private Map<String, JsonArray> snapshotByAttacker() {
        List<War> snap;
        synchronized (history) {
            int extra = history.size() - HISTORY_CAP;
            if (extra > 0) history.subList(0, extra).removeIf(w -> w.getState() != War.State.ACTIVE);
            snap = new ArrayList<>(history);
        }
        Map<String, JsonArray> out = new HashMap<>();
        for (War w : snap) out.computeIfAbsent(w.getAttackerTag().toUpperCase(), k -> new JsonArray()).add(toJson(w));
        return out;
    }

    private void writeAll(Map<String, JsonArray> byAtt) {
        for (Map.Entry<String, JsonArray> e : byAtt.entrySet()) {
            try { module.getGuildRepository().saveWarsJson(e.getKey(), gson.toJson(e.getValue())); }
            catch (Exception ex) { log.severe("Wars save fail: " + ex.getMessage()); }
        }
    }

    private JsonObject toJson(War w) {
        JsonObject m = new JsonObject();
        m.addProperty("id", w.getId().toString());
        m.addProperty("attacker", w.getAttackerTag());
        m.addProperty("defender", w.getDefenderTag());
        m.addProperty("start", w.getStartTime());
        m.addProperty("duration", w.getDurationMs());
        m.addProperty("end", w.getEndTime());
        m.addProperty("state", w.getState().name());
        m.addProperty("declarer", w.getDeclarerName());
        m.addProperty("cA", w.isConquestByAttacker());
        m.addProperty("cD", w.isConquestByDefender());
        if (w.getActiveBannerId() != null) m.addProperty("bannerId", w.getActiveBannerId().toString());
        if (w.getBannerCarrierPlayer() != null) m.addProperty("bannerPlayer", w.getBannerCarrierPlayer().toString());
        if (w.getBannerCarrierGuild() != null) m.addProperty("bannerGuild", w.getBannerCarrierGuild());
        JsonObject st = new JsonObject();
        for (Map.Entry<String, WarStats> e : w.getAllStats().entrySet()) {
            WarStats s = e.getValue();
            JsonObject sm = new JsonObject();
            sm.addProperty("kills", s.getKills()); sm.addProperty("deaths", s.getDeaths());
            sm.addProperty("ranking", s.getRankingGained()); sm.addProperty("koxy", s.getKoxyEaten());
            sm.addProperty("refills", s.getRefillsUsed()); sm.addProperty("pearls", s.getPearlsUsed());
            sm.addProperty("tnt", s.getTntFired()); sm.addProperty("eggHits", s.getEggHits());
            st.add(e.getKey(), sm);
        }
        m.add("stats", st);
        return m;
    }

    public void load() {
        Map<String, String> all = module.getGuildRepository().loadAllWars();
        synchronized (history) { history.clear(); }
        activeWars.clear(); warsById.clear();
        for (String json : all.values()) {
            if (json == null || json.isBlank()) continue;
            try {
                JsonArray arr = gson.fromJson(json, JsonArray.class);
                for (JsonElement el : arr) fromJson(el.getAsJsonObject());
            } catch (Exception ex) { log.warning("Wars load fail: " + ex.getMessage()); }
        }
        List<War> expired = new ArrayList<>();
        for (War w : activeWars.values()) if (w.isExpired()) expired.add(w);
        for (War w : expired) endWar(w);
    }

    private void fromJson(JsonObject m) {
        UUID id = UUID.fromString(m.get("id").getAsString());
        String att = m.get("attacker").getAsString(), def = m.get("defender").getAsString();
        long start = m.get("start").getAsLong(), dur = m.get("duration").getAsLong();
        long end = m.has("end") ? m.get("end").getAsLong() : 0L;
        War.State state = War.State.valueOf(m.get("state").getAsString());
        Map<String, WarStats> statsMap = new HashMap<>();
        if (m.has("stats")) {
            JsonObject st = m.getAsJsonObject("stats");
            for (String tag : st.keySet()) {
                JsonObject sm = st.getAsJsonObject(tag);
                statsMap.put(tag.toUpperCase(), new WarStats(sm.get("kills").getAsInt(), sm.get("deaths").getAsInt(),
                        sm.get("ranking").getAsInt(), sm.get("koxy").getAsInt(), sm.get("refills").getAsInt(),
                        sm.get("pearls").getAsInt(), sm.get("tnt").getAsInt(), sm.get("eggHits").getAsInt()));
            }
        }
        War war = new War(id, att, def, start, dur, end, state, statsMap);
        if (m.has("declarer")) war.setDeclarerName(m.get("declarer").getAsString());
        if (m.has("cA")) war.setConquestByAttacker(m.get("cA").getAsBoolean());
        if (m.has("cD")) war.setConquestByDefender(m.get("cD").getAsBoolean());
        if (m.has("bannerId") && !m.get("bannerId").getAsString().isBlank())
            try { war.setActiveBannerId(UUID.fromString(m.get("bannerId").getAsString())); } catch (Exception ignored) { }
        if (m.has("bannerPlayer") && !m.get("bannerPlayer").getAsString().isBlank())
            try { war.setBannerCarrierPlayer(UUID.fromString(m.get("bannerPlayer").getAsString())); } catch (Exception ignored) { }
        if (m.has("bannerGuild")) war.setBannerCarrierGuild(m.get("bannerGuild").getAsString());
        synchronized (history) { history.add(war); }
        warsById.put(id, war);
        if (state == War.State.ACTIVE) activeWars.put(pairKey(att, def), war);
    }

    public void rebindBanner(Player player) {
        if (player == null) return;
        ItemStack helmet = player.getInventory().getHelmet();
        if (!BannerItem.isBanner(helmet)) { bannerCarriers.remove(player.getUniqueId()); WaypointHook.stopGlobalLiveTrack(player.getUniqueId()); return; }
        UUID warId = BannerItem.getWarId(helmet), bannerId = BannerItem.getBannerId(helmet);
        Optional<War> opt = getWarById(warId);
        if (opt.isEmpty() || !opt.get().isActive()) {
            player.getInventory().setHelmet(null); setScale(player, 1.0);
            bannerCarriers.remove(player.getUniqueId()); WaypointHook.stopGlobalLiveTrack(player.getUniqueId());
            return;
        }
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null || !opt.get().isParticipant(g.getTag())) {
            player.getInventory().setHelmet(null); setScale(player, 1.0);
            bannerCarriers.remove(player.getUniqueId()); WaypointHook.stopGlobalLiveTrack(player.getUniqueId());
            return;
        }
        opt.get().setActiveBannerId(bannerId);
        opt.get().setBannerCarrierPlayer(player.getUniqueId());
        opt.get().setBannerCarrierGuild(g.getTag());
        bannerCarriers.add(player.getUniqueId());
        setScale(player, 1.6);
        WaypointHook.startGlobalLiveTrack(player, " SZTANDAR " + BannerItem.getFromGuild(helmet));
        markDirty();
    }
}
