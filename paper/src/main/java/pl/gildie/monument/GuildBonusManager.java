package pl.gildie.monument;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.gildie.Const;
import pl.gildie.db.MonumentRepository;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;
import pl.gildie.util.WaypointHook;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GuildBonusManager implements Listener {
    private static final Gson gson = new Gson();
    private static JavaPlugin plugin;
    private static MonumentRepository repo;
    private static GuildManager guildManager;

    private static final Map<String, Long> centerBonus = new HashMap<>();
    private static final Map<String, UUID> centerWaypoints = new HashMap<>();
    private static final Map<String, Map<String, Long>> cornerEffects = new HashMap<>();

    public static void init(JavaPlugin p, MonumentRepository r, GuildManager gm) {
        plugin = p; repo = r; guildManager = gm;
        load();
        p.getServer().getPluginManager().registerEvents(new GuildBonusManager(), p);
        Bukkit.getScheduler().runTaskTimer(p, () -> { applyEffects(); cleanExpired(); }, 40L, 40L);
    }

    public static void addCenterBonus(String tag, UUID wp) {
        centerBonus.put(tag.toUpperCase(), System.currentTimeMillis() + Const.MONUMENT_CENTER_BONUS_MS);
        if (wp != null) centerWaypoints.put(tag.toUpperCase(), wp);
        save();
    }

    public static void addCornerEffect(String tag, String effect) {
        cornerEffects.computeIfAbsent(tag.toUpperCase(), k -> new HashMap<>())
                .put(effect.toUpperCase(), System.currentTimeMillis() + Const.MONUMENT_CORNER_EFFECT_MS);
        save();
    }

    public static boolean hasCenterBonus(String tag) {
        Long e = centerBonus.get(tag.toUpperCase());
        if (e == null) return false;
        if (System.currentTimeMillis() > e) { centerBonus.remove(tag.toUpperCase()); return false; }
        return true;
    }

    public static Set<String> getActiveCornerEffects(String tag) {
        Set<String> a = new HashSet<>();
        Map<String, Long> fx = cornerEffects.get(tag.toUpperCase());
        if (fx == null) return a;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> en : fx.entrySet()) if (now <= en.getValue()) a.add(en.getKey());
        return a;
    }

    public static String describeActive(String tag) {
        StringBuilder sb = new StringBuilder();
        if (hasCenterBonus(tag)) sb.append("KORONA(+drop) ");
        for (String e : getActiveCornerEffects(tag)) sb.append(e).append(" ");
        return sb.length() == 0 ? "brak" : sb.toString().trim();
    }

    public static void cleanExpired() {
        long now = System.currentTimeMillis();
        boolean ch = false;
        for (String t : new HashSet<>(centerBonus.keySet())) {
            if (now > centerBonus.get(t)) {
                centerBonus.remove(t); ch = true;
                UUID wp = centerWaypoints.remove(t);
                if (wp != null) WaypointHook.removeWaypoint(wp);
            }
        }
        for (String t : new HashSet<>(cornerEffects.keySet())) {
            Map<String, Long> fx = cornerEffects.get(t);
            if (fx == null) continue;
            for (String e : new HashSet<>(fx.keySet())) {
                if (now > fx.get(e)) { fx.remove(e); ch = true; }
            }
            if (fx.isEmpty()) cornerEffects.remove(t);
        }
        if (ch) save();
    }

    private static void applyEffects() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Guild g = guildManager.getGuildByPlayer(p.getUniqueId());
            if (g == null) continue;
            Set<String> fx = getActiveCornerEffects(g.getTag());
            if (fx.isEmpty()) continue;
            if (fx.contains("SPEED")) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, Const.MONUMENT_SPEED_AMPLIFIER, false, false));
            if (fx.contains("FIRE_RESISTANCE")) p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, Const.MONUMENT_FIRE_RESIST_AMPLIFIER, false, false));
            if (fx.contains("REGENERATION")) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, Const.MONUMENT_REGEN_AMPLIFIER, false, false));
            if (fx.contains("HASTE")) p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, Const.MONUMENT_HASTE_AMPLIFIER, false, false));
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null) return;
        java.util.List<ItemStack> d = e.getDrops();
        if (d == null || d.isEmpty()) return;
        if (rollBonus(k)) e.getEntity().getWorld().dropItemNaturally(e.getEntity().getLocation(), d.get(new Random().nextInt(d.size())).clone());
    }

    @EventHandler
    public void onBlockDrop(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        if (e.getItems() == null || e.getItems().isEmpty()) return;
        if (rollBonus(p)) e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), e.getItems().get(0).getItemStack().clone());
    }

    private static boolean rollBonus(Player p) {
        Guild g = guildManager.getGuildByPlayer(p.getUniqueId());
        if (g == null) return false;
        double ch = 0.0;
        if (hasCenterBonus(g.getTag())) ch += Const.MONUMENT_CENTER_DROP_BONUS_PERCENT;
        if (getActiveCornerEffects(g.getTag()).contains("HASTE")) ch += Const.MONUMENT_HASTE_EXTRA_DROP_PERCENT;
        return ch > 0 && ThreadLocalRandom.current().nextDouble(100.0) < ch;
    }

    public static void load() {
        centerBonus.clear(); centerWaypoints.clear(); cornerEffects.clear();
        for (Guild g : guildManager.getAll()) {
            String json = repo.loadMonumentEffects(g.getTag());
            if (json == null || json.isBlank()) continue;
            try {
                JsonObject obj = gson.fromJson(json, JsonObject.class);
                if (obj.has("center")) {
                    JsonObject c = obj.getAsJsonObject("center");
                    long exp = c.get("expires").getAsLong();
                    if (exp > System.currentTimeMillis()) {
                        centerBonus.put(g.getTag().toUpperCase(), exp);
                        if (c.has("waypoint")) {
                            try { centerWaypoints.put(g.getTag().toUpperCase(), UUID.fromString(c.get("waypoint").getAsString())); } catch (Exception ignored) {}
                        }
                    }
                }
                if (obj.has("corners")) {
                    JsonObject cr = obj.getAsJsonObject("corners");
                    Map<String, Long> fx = new HashMap<>();
                    for (String ef : cr.keySet()) {
                        long exp = cr.get(ef).getAsLong();
                        if (exp > System.currentTimeMillis()) fx.put(ef.toUpperCase(), exp);
                    }
                    if (!fx.isEmpty()) cornerEffects.put(g.getTag().toUpperCase(), fx);
                }
            } catch (Exception ignored) { }
        }
    }

    public static void save() {
        for (Guild g : guildManager.getAll()) {
            JsonObject obj = new JsonObject();
            Long cExp = centerBonus.get(g.getTag().toUpperCase());
            if (cExp != null) {
                JsonObject c = new JsonObject();
                c.addProperty("expires", cExp);
                UUID wp = centerWaypoints.get(g.getTag().toUpperCase());
                if (wp != null) c.addProperty("waypoint", wp.toString());
                obj.add("center", c);
            }
            Map<String, Long> fx = cornerEffects.get(g.getTag().toUpperCase());
            if (fx != null && !fx.isEmpty()) {
                JsonObject cr = new JsonObject();
                for (Map.Entry<String, Long> e : fx.entrySet()) cr.addProperty(e.getKey(), e.getValue());
                obj.add("corners", cr);
            }
            repo.saveMonumentEffects(g.getTag(), gson.toJson(obj));
        }
    }
}
