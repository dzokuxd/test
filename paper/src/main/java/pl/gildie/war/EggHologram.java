package pl.gildie.war;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.model.Guild;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EggHologram {
    public static final String META_KEY = "gildie_egg_holo";
    public static final String META_TAG = "gildie_egg_tag";
    public static final String TAG = "gildie_egg_holo";

    private final JavaPlugin plugin;
    private final Map<String, List<UUID>> stands = new ConcurrentHashMap<>();

    public EggHologram(JavaPlugin plugin) { this.plugin = plugin; }

    public void spawnOrUpdate(Guild guild) {
        if (guild == null || !guild.hasEgg()) return;
        Location egg = guild.getEggLocation();
        if (egg == null || egg.getWorld() == null) return;

        remove(guild.getTag());
        removeNearby(egg);

        Location base = egg.clone().add(0, 1.6, 0);
        List<UUID> ids = new ArrayList<>();
        int max = guild.getMaxEggHp();
        int hp = guild.getEggHp();
        String color = hpColor(hp, max);

        ArmorStand line1 = spawnLine(base.clone().add(0, 0.3, 0), "\u00a7c\u00a7l" + guild.getTag());
        if (line1 != null) { line1.setMetadata(META_TAG, new FixedMetadataValue(plugin, guild.getTag())); ids.add(line1.getUniqueId()); }
        ArmorStand line2 = spawnLine(base, color + "HP: \u00a7f" + hp + "\u00a77/\u00a7f" + max);
        if (line2 != null) { line2.setMetadata(META_TAG, new FixedMetadataValue(plugin, guild.getTag())); ids.add(line2.getUniqueId()); }

        stands.put(guild.getTag().toUpperCase(), ids);
    }

    public void updateHp(Guild guild) {
        if (guild == null) return;
        List<UUID> ids = stands.get(guild.getTag().toUpperCase());
        if (ids == null || ids.size() < 2) { spawnOrUpdate(guild); return; }
        World w = guild.getEggLocation() != null ? guild.getEggLocation().getWorld() : null;
        if (w == null) return;
        int max = guild.getMaxEggHp();
        int hp = guild.getEggHp();
        String color = hpColor(hp, max);
        Entity e = Bukkit.getEntity(ids.get(1));
        if (e instanceof ArmorStand as) as.setCustomName(color + "HP: \u00a7f" + hp + "\u00a77/\u00a7f" + max);
        else spawnOrUpdate(guild);
    }

    public void remove(String tag) {
        if (tag == null) return;
        List<UUID> ids = stands.remove(tag.toUpperCase());
        if (ids == null) return;
        for (UUID id : ids) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
    }

    public void removeAll() {
        for (String tag : new ArrayList<>(stands.keySet())) remove(tag);
    }

    public void cleanupWorldAndRespawn(Iterable<Guild> guilds) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e instanceof ArmorStand as
                        && (as.hasMetadata(META_KEY) || as.getScoreboardTags().contains(TAG))) {
                    as.remove();
                }
            }
        }
        stands.clear();
        for (Guild g : guilds) {
            if (g.hasEgg()) spawnOrUpdate(g);
        }
    }

    private void removeNearby(Location egg) {
        World w = egg.getWorld();
        if (w == null) return;
        for (Entity e : w.getNearbyEntities(egg, 3, 4, 3)) {
            if (e instanceof ArmorStand as
                    && (as.getScoreboardTags().contains(TAG) || as.hasMetadata(META_KEY))) {
                as.remove();
            }
        }
    }

    private ArmorStand spawnLine(Location loc, String text) {
        World w = loc.getWorld();
        if (w == null) return null;
        ArmorStand as = (ArmorStand) w.spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setGravity(false);
        as.setMarker(true);
        as.setSmall(true);
        as.setBasePlate(false);
        as.setArms(false);
        as.setCustomNameVisible(true);
        as.setCustomName(text);
        as.setInvulnerable(true);
        as.setCollidable(false);
        as.setMetadata(META_KEY, new FixedMetadataValue(plugin, true));
        as.addScoreboardTag(TAG);
        try { as.setPersistent(false); } catch (Throwable ignored) { }
        return as;
    }

    private static String hpColor(int hp, int max) {
        if (max <= 0) return "\u00a7a";
        double r = (double) hp / max;
        if (r > 0.6) return "\u00a7a";
        if (r > 0.3) return "\u00a7e";
        return "\u00a7c";
    }
}
