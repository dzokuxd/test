package pl.gildie.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Cow;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import pl.gildie.model.CowStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class CowStackManager {

    private final Plugin plugin;
    private final List<CowStack> stacks = new ArrayList<>();
    private static final double MERGE_RADIUS = 5.0;
    public static final String RESPAWN_META = "cow_stack_respawn";

    public CowStackManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void handleCow(Cow cow) {
        if (getStackByRep(cow.getUniqueId()) != null)
            return;
        if (cow.hasMetadata(RESPAWN_META)) return;

        if (getStackByRep(cow.getUniqueId()) != null) return;

        CowStack nearest = findNearest(cow.getLocation());
        if (nearest != null) {
            nearest.addCount(1);
            Bukkit.getScheduler().runTask(plugin, cow::remove);
        } else {
            stacks.add(new CowStack(cow));
        }
    }
    public void addStack(CowStack s) {
        stacks.add(s);
        s.updateHologram();
    }

    public CowStack getStackByRep(UUID repUUID) {
        for (CowStack s : stacks) if (s.isRep(repUUID)) return s;
        return null;
    }

    public CowStack findNearest(Location loc) {
        CowStack best = null;
        double bestD = MERGE_RADIUS;
        for (CowStack s : stacks) {
            Cow rep = s.getRep();
            if (rep == null || rep.isDead() || !rep.getWorld().equals(loc.getWorld())) continue;
            double d = rep.getLocation().distance(loc);
            if (d < bestD) { bestD = d; best = s; }
        }
        return best;
    }

    public void removeStack(CowStack s) {
        s.removeHologram();
        stacks.remove(s);
    }
}