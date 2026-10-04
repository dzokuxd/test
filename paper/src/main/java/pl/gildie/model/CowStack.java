package pl.gildie.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cow;

import java.util.UUID;

public class CowStack {

    private final UUID repUUID;
    private int count;
    private ArmorStand hologram;
    private static final double HOLO_OFFSET = 1.2;

    public CowStack(Cow rep) {
        this.repUUID = rep.getUniqueId();
        this.count = 1;
    }

    public UUID getRepUUID() { return repUUID; }
    public int getCount() { return count; }
    public boolean isRep(UUID id) { return repUUID.equals(id); }

    public Cow getRep() { return (Cow) Bukkit.getEntity(repUUID); }

    public void addCount(int n) {
        count += n;
        updateHologram();
    }

    public void updateHologram() {
        Cow rep = getRep();
        if (rep == null || rep.isDead() || count <= 1) { removeHologram(); return; }

        if (hologram == null || hologram.isDead()) {
            hologram = rep.getWorld().spawn(holoLoc(rep), ArmorStand.class, s -> {
                s.setInvisible(true);
                s.setGravity(false);
                s.setSmall(true);
                s.setMarker(true);
                s.setPersistent(true);
                s.setInvulnerable(true);
                s.setSilent(true);
                s.setCustomNameVisible(true);
            });
            if (hologram == null) return;
        }
        hologram.teleport(holoLoc(rep));
        hologram.setCustomName("§6x" + count);
        hologram.setCustomNameVisible(true);
    }

    public void removeHologram() {
        if (hologram != null && !hologram.isDead()) hologram.remove();
        hologram = null;
    }
    private long breedCooldownUntil = 0;

    public boolean isBreedCooldown() {
        return System.currentTimeMillis() < breedCooldownUntil;
    }

    public void startBreedCooldown(long millis) {
        this.breedCooldownUntil = System.currentTimeMillis() + millis;
    }

    /** Pozostały cooldown w sekundach */
    public long getBreedCooldownSeconds() {
        return Math.max(0, (breedCooldownUntil - System.currentTimeMillis()) / 1000L);
    }
    private Location holoLoc(Cow rep) {
        return rep.getLocation().add(0.5, HOLO_OFFSET, 0.5);
    }
}