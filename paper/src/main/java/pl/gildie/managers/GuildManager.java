package pl.gildie.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.Const;
import pl.gildie.GildieModule;
import pl.gildie.model.Guild;
import pl.gildie.util.WaypointHook;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuildManager {
    private final GildieModule module;
    private final JavaPlugin plugin;
    private final Map<String, Guild> guilds = new ConcurrentHashMap<>();
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();

    public GuildManager(GildieModule module) {
        this.module = module;
        this.plugin = module.plugin();
        load();
    }

    public void load() {
        guilds.clear();
        for (Guild g : module.getGuildRepository().loadAll()) guilds.put(g.getTag(), g);
        plugin.getLogger().info("Zaladowano " + guilds.size() + " gildii z MySQL");
    }

    public void markDirty(String tag) { dirty.add(tag); }
    public void markDirty() { dirty.addAll(guilds.keySet()); }

    public void save() {
        if (dirty.isEmpty()) return;
        Set<String> toSave = new HashSet<>(dirty);
        dirty.clear();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (String tag : toSave) {
                Guild g = guilds.get(tag);
                if (g != null) {
                    try {
                        module.getGuildRepository().upsert(g);
                        module.publishGuildUpdate(tag);
                    } catch (Exception e) {
                        plugin.getLogger().severe("Zapis gildii " + tag + " nie udal sie: " + e.getMessage());
                        dirty.add(tag);
                    }
                }
            }
        });
    }

    public void saveIfDirty() { save(); }

    public void reloadAsync(String tag) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Guild> all = module.getGuildRepository().loadAll();
                Guild fresh = all.stream().filter(g -> g.getTag().equals(tag)).findFirst().orElse(null);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (fresh == null) guilds.remove(tag);
                    else guilds.put(tag, fresh);
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Reload gildii " + tag + " nie udal sie: " + e.getMessage());
            }
        });
    }

    public boolean createGuild(String tag, UUID owner, Location center, int radius) {
        tag = tag.toUpperCase();
        if (guilds.containsKey(tag)) return false;
        if (getGuildByPlayer(owner) != null) return false;

        int roomY = Const.EGG_ROOM_Y;
        Location eggCenter = new Location(center.getWorld(), center.getBlockX() + 0.5, roomY + 1, center.getBlockZ() + 0.5);
        Guild guild = new Guild(tag, owner, eggCenter, radius);
        guilds.put(tag, guild);
        guild.setMaxEggHp(Const.WAR_EGG_HITS);
        guild.setEggHp(Const.WAR_EGG_HITS);

        Bukkit.getScheduler().runTaskLater(plugin, () -> module.getEggHologram().spawnOrUpdate(guild), 20L);
        markDirty(tag); save();

        Location wpLoc = guild.getCenterAtY(70);
        Player leader = Bukkit.getPlayer(owner);
        if (wpLoc != null && leader != null && leader.isOnline()) {
            WaypointHook.addGuildWaypoint(leader, "Gildia " + tag, wpLoc, 0x55FF55).ifPresent(guild::setGuildWaypointId);
        } else if (wpLoc != null) {
            WaypointHook.addGuildWaypoint(tag, "Gildia " + tag, wpLoc, 0x55FF55).ifPresent(guild::setGuildWaypointId);
        }
        markDirty(tag); save();
        return true;
    }

    public boolean disband(Guild guild) {
        for (String allyTag : new HashSet<>(guild.getAllies())) {
            Guild ally = getGuild(allyTag);
            if (ally != null) { ally.removeAlly(guild.getTag()); markDirty(ally.getTag()); }
        }
        guild.getAllies().clear();
        if (guild.getGuildWaypointId() != null) WaypointHook.removeGuildWaypoint(guild.getGuildWaypointId());
        if (guild.getRaidWaypointId() != null) WaypointHook.removeGuildWaypoint(guild.getRaidWaypointId());
        module.getEggHologram().remove(guild.getTag());
        guilds.remove(guild.getTag());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> module.getGuildRepository().delete(guild.getTag()));
        module.publishGuildUpdate(guild.getTag());
        return true;
    }

    public Guild getGuild(String tag) { return guilds.get(tag.toUpperCase()); }
    public Guild getGuildByPlayer(UUID uuid) {
        for (Guild guild : guilds.values()) if (guild.isMember(uuid)) return guild;
        return null;
    }
    public Guild getGuildAt(Location loc) {
        for (Guild guild : guilds.values()) if (guild.isInTerritory(loc)) return guild;
        return null;
    }
    public Guild getNearestEnemyGuild(Location loc, Guild own) {
        Guild best = null; double bestDist = Double.MAX_VALUE;
        for (Guild g : guilds.values()) {
            if (own != null && g.getTag().equals(own.getTag())) continue;
            double abs = Math.abs(g.distanceToBorder(loc));
            if (abs < bestDist) { bestDist = abs; best = g; }
        }
        return best;
    }
    public boolean isNearEnemyTerritory(Location loc, Guild own, double maxNear) {
        for (Guild g : guilds.values()) {
            if (own != null && g.getTag().equals(own.getTag())) continue;
            double d = g.distanceToBorder(loc);
            if (d > 0 && d <= maxNear) return true;
        }
        return false;
    }
    public Guild getRaidBaseOwnerAt(Location loc) {
        for (Guild g : guilds.values()) if (g.isRaidBaseBlock(loc)) return g;
        return null;
    }

    public void checkAllianceLimit(Guild guild) {
        if (guild == null) return;
        boolean changed = false;
        for (String allyTag : new HashSet<>(guild.getAllies())) {
            Guild ally = getGuild(allyTag);
            if (ally == null) { guild.removeAlly(allyTag); changed = true; continue; }
            int total = guild.getMembers().size() + ally.getMembers().size();
            if (total > Const.ALLIANCE_LIMIT) {
                guild.removeAlly(allyTag); ally.removeAlly(guild.getTag()); changed = true;
                for (UUID id : guild.getMembers()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) p.sendMessage("\u00a7cSojusz z \u00a7e" + allyTag + " \u00a7czerwany (limit " + Const.ALLIANCE_LIMIT + ").");
                }
                for (UUID id : ally.getMembers()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) p.sendMessage("\u00a7cSojusz z \u00a7e" + guild.getTag() + " \u00a7czerwany (limit " + Const.ALLIANCE_LIMIT + ").");
                }
                markDirty(ally.getTag());
            }
        }
        if (changed) { markDirty(guild.getTag()); save(); }
    }

    public Collection<Guild> getAll() { return guilds.values(); }

    public void tickRaidBases() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Guild g : guilds.values()) {
            if (g.getRaidExpiresAt() > 0 && g.getRaidExpiresAt() <= now) {
                if (g.getRaidWaypointId() != null) WaypointHook.removeGuildWaypoint(g.getRaidWaypointId());
                g.clearRaidBase();
                markDirty(g.getTag());
                changed = true;
            }
        }
        if (changed) save();
    }
}
