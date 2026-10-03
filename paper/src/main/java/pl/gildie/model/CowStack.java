package pl.gildie.model;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reprezentuje jeden stack krow
 */
public class CowStack {

    private final UUID stackId;
    private final Location location;
    private final List<UUID> cowUUIDs;
    private ArmorStand hologram;
    private int count;

    public CowStack(Location location) {
        this.stackId = UUID.randomUUID();
        this.location = location;
        this.cowUUIDs = new ArrayList<>();
        this.count = 0;
    }

    public UUID getStackId() {
        return stackId;
    }

    public Location getLocation() {
        return location;
    }

    public int getCount() {
        return count;
    }

    public List<UUID> getCowUUIDs() {
        return cowUUIDs;
    }

    /**
     * Dodaje krowe do stacka
     */
    public void addCow(Cow cow) {
        cowUUIDs.add(cow.getUniqueId());
        count++;
        updateHologram();
    }

    /**
     * Usuwa krowe ze stacka
     */
    public void removeCow(UUID cowUUID) {
        cowUUIDs.remove(cowUUID);
        count--;
        if (count < 0) count = 0;
        updateHologram();
    }

    /**
     * Zwiększa licznik (np. po rozmnozeniu)
     */
    public void incrementCount() {
        count++;
        updateHologram();
    }

    /**
     * Sprawdza czy stack jest pusty
     */
    public boolean isEmpty() {
        return count <= 0;
    }

    /**
     * Sprawdza czy krowa nalezy do tego stacka
     */
    public boolean containsCow(UUID cowUUID) {
        return cowUUIDs.contains(cowUUID);
    }

    /**
     * Tworzy lub aktualizuje hologram z licznikiem
     */
    public void updateHologram() {
        if (hologram == null || hologram.isDead()) {
            spawnHologram();
        }

        if (count <= 1) {
            // Nie pokazuj hologramu dla 1 krowy
            if (hologram != null && !hologram.isDead()) {
                hologram.remove();
                hologram = null;
            }
            return;
        }

        // Ustaw nazwe hologramu
        hologram.setCustomName("§6x" + count);
        hologram.setCustomNameVisible(true);
    }

    /**
     * Spawnuje niewidzialny ArmorStand jako hologram
     */
    private void spawnHologram() {
        Location holoLoc = location.clone().add(0, 1.5, 0);
        hologram = location.getWorld().spawn(holoLoc, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setPersistent(false);
            stand.setCustomNameVisible(false);
            stand.setInvulnerable(true);
        });
    }

    /**
     * Usuwa hologram
     */
    public void removeHologram() {
        if (hologram != null && !hologram.isDead()) {
            hologram.remove();
            hologram = null;
        }
    }

    /**
     * Usuwa wszystkie krowy ze stacka i hologram
     */
    public void destroy(org.bukkit.Server server) {
        // Usun krowy
        for (UUID uuid : cowUUIDs) {
            Entity entity = server.getEntity(uuid);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        cowUUIDs.clear();
        count = 0;

        // Usun hologram
        removeHologram();
    }
}