package pl.gildie.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Cow;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import pl.gildie.model.CowStack;

import java.util.*;

/**
 * Zarzadza wszystkimi stackami krow na serwerze
 */
public class CowStackManager {

    private final JavaPlugin plugin;
    private final List<CowStack> stacks;
    private final Map<UUID, UUID> cowToStackMap; // CowUUID -> StackUUID

    // Promien w blokach, w ktorym krowy sie stackuja
    private static final double STACK_RADIUS = 5.0;

    public CowStackManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.stacks = new ArrayList<>();
        this.cowToStackMap = new HashMap<>();
    }

    /**
     * Dodaje krowe do stacka lub tworzy nowy stack
     */
    public void addCowToStack(Cow cow) {
        Location cowLoc = cow.getLocation();

        // Szukaj istniejacego stacka w poblizu
        CowStack nearestStack = findNearestStack(cowLoc);

        if (nearestStack != null) {
            // Dodaj do istniejacego stacka
            nearestStack.addCow(cow);
            cowToStackMap.put(cow.getUniqueId(), nearestStack.getStackId());
        } else {
            // Stworz nowy stack
            CowStack newStack = new CowStack(cowLoc);
            newStack.addCow(cow);
            stacks.add(newStack);
            cowToStackMap.put(cow.getUniqueId(), newStack.getStackId());
        }
    }

    /**
     * Usuwa krowe ze stacka
     */
    public void removeCowFromStack(UUID cowUUID) {
        UUID stackId = cowToStackMap.get(cowUUID);
        if (stackId == null) return;

        CowStack stack = getStackById(stackId);
        if (stack != null) {
            stack.removeCow(cowUUID);

            // Jesli stack jest pusty, usun go
            if (stack.isEmpty()) {
                removeStack(stack);
            }
        }

        cowToStackMap.remove(cowUUID);
    }

    /**
     * Zwieksza licznik stacka (np. po rozmnozeniu)
     */
    public void incrementStackCount(UUID cowUUID) {
        UUID stackId = cowToStackMap.get(cowUUID);
        if (stackId == null) return;

        CowStack stack = getStackById(stackId);
        if (stack != null) {
            stack.incrementCount();
        }
    }

    /**
     * Pobiera stack dla danej krowy
     */
    public CowStack getStackForCow(UUID cowUUID) {
        UUID stackId = cowToStackMap.get(cowUUID);
        if (stackId == null) return null;
        return getStackById(stackId);
    }

    /**
     * Pobiera licznik stacka dla krowy
     */
    public int getStackCount(UUID cowUUID) {
        CowStack stack = getStackForCow(cowUUID);
        if (stack == null) return 1;
        return stack.getCount();
    }

    /**
     * Znajduje najblizszy stack w promieniu STACK_RADIUS
     */
    private CowStack findNearestStack(Location location) {
        CowStack nearest = null;
        double nearestDistance = STACK_RADIUS;

        for (CowStack stack : stacks) {
            if (!stack.getLocation().getWorld().equals(location.getWorld())) {
                continue;
            }

            double distance = stack.getLocation().distance(location);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = stack;
            }
        }

        return nearest;
    }

    /**
     * Pobiera stack po UUID
     */
    private CowStack getStackById(UUID stackId) {
        for (CowStack stack : stacks) {
            if (stack.getStackId().equals(stackId)) {
                return stack;
            }
        }
        return null;
    }

    /**
     * Usuwa stack
     */
    private void removeStack(CowStack stack) {
        stack.removeHologram();
        stacks.remove(stack);

        // Usun krowy z mapy
        for (UUID cowUUID : stack.getCowUUIDs()) {
            cowToStackMap.remove(cowUUID);
        }
    }

    /**
     * Usuwa wszystkie stacki (przy wylaczeniu serwera)
     */
    public void removeAllStacks() {
        for (CowStack stack : stacks) {
            stack.removeHologram();
        }
        stacks.clear();
        cowToStackMap.clear();
    }

    /**
     * Uruchamia task czyszczacy martwe stacki
     */
    public void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupDeadCows();
            }
        }.runTaskTimer(plugin, 200L, 200L); // Co 10 sekund
    }

    /**
     * Czyści stacki z martwych krow
     */
    private void cleanupDeadCows() {
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, UUID> entry : cowToStackMap.entrySet()) {
            UUID cowUUID = entry.getKey();
            org.bukkit.entity.Entity entity = Bukkit.getEntity(cowUUID);

            if (entity == null || entity.isDead()) {
                toRemove.add(cowUUID);
            }
        }

        for (UUID cowUUID : toRemove) {
            removeCowFromStack(cowUUID);
        }
    }

    /**
     * Pobiera ilosc stackow (do debugowania)
     */
    public int getStackCount() {
        return stacks.size();
    }

    /**
     * Pobiera totalna ilosc krow we wszystkich stackach
     */
    public int getTotalCowCount() {
        int total = 0;
        for (CowStack stack : stacks) {
            total += stack.getCount();
        }
        return total;
    }
}