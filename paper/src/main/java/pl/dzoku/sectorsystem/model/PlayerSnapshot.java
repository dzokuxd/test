package pl.dzoku.sectorsystem.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerSnapshot {
    public UUID uuid;
    public long timestamp;

    // Base64 z BukkitObjectOutputStream: [0..35]=main, [36..39]=armor, [40]=offhand
    public String inventoryBase64;

    public double health;
    public double maxHealth;
    public int food;
    public float saturation;
    public int level;
    public float exp;
    public int totalExp;

    public List<EffectEntry> effects = new ArrayList<>();

    public static class EffectEntry {
        public String type;
        public int amplifier;
        public int duration;

        public EffectEntry() {}
        public EffectEntry(String type, int amplifier, int duration) {
            this.type = type;
            this.amplifier = amplifier;
            this.duration = duration;
        }
    }
}
