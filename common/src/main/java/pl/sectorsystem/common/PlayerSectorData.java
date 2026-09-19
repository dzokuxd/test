package pl.sectorsystem.common;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pełny stan gracza synchronizowany przez Redis.
 */
public class PlayerSectorData {
    private UUID uuid;
    private String username;
    private String currentSectorId;
    private String lastSectorId;
    private long lastUpdate;

    // Podstawowe statystyki
    private double health;
    private double maxHealth;
    private int foodLevel;
    private float saturation;
    private int level;
    private float exp;
    private int totalExperience;
    private String gameMode;
    private boolean allowFlight;
    private boolean flying;
    private int fireTicks;
    private int remainingAir;

    // Pozycja (opcjonalna – przy transferze i tak zmieniamy sektor)
    private String world;
    private double x, y, z;
    private float yaw, pitch;

    // Serializowane dane (Base64 ItemStack[])
    private String inventoryBase64;       // główny ekwipunek (36 + armor + offhand)
    private String enderChestBase64;      // enderchest
    private String effectsBase64;         // potion effects

    public PlayerSectorData() {}

    public PlayerSectorData(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
        this.lastUpdate = System.currentTimeMillis();
    }

    // --- Gettery / Settery ---

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getCurrentSectorId() { return currentSectorId; }
    public void setCurrentSectorId(String currentSectorId) { this.currentSectorId = currentSectorId; }

    public String getLastSectorId() { return lastSectorId; }
    public void setLastSectorId(String lastSectorId) { this.lastSectorId = lastSectorId; }

    public long getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(long lastUpdate) { this.lastUpdate = lastUpdate; }

    public double getHealth() { return health; }
    public void setHealth(double health) { this.health = health; }

    public double getMaxHealth() { return maxHealth; }
    public void setMaxHealth(double maxHealth) { this.maxHealth = maxHealth; }

    public int getFoodLevel() { return foodLevel; }
    public void setFoodLevel(int foodLevel) { this.foodLevel = foodLevel; }

    public float getSaturation() { return saturation; }
    public void setSaturation(float saturation) { this.saturation = saturation; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public float getExp() { return exp; }
    public void setExp(float exp) { this.exp = exp; }

    public int getTotalExperience() { return totalExperience; }
    public void setTotalExperience(int totalExperience) { this.totalExperience = totalExperience; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public boolean isAllowFlight() { return allowFlight; }
    public void setAllowFlight(boolean allowFlight) { this.allowFlight = allowFlight; }

    public boolean isFlying() { return flying; }
    public void setFlying(boolean flying) { this.flying = flying; }

    public int getFireTicks() { return fireTicks; }
    public void setFireTicks(int fireTicks) { this.fireTicks = fireTicks; }

    public int getRemainingAir() { return remainingAir; }
    public void setRemainingAir(int remainingAir) { this.remainingAir = remainingAir; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }

    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }

    public String getInventoryBase64() { return inventoryBase64; }
    public void setInventoryBase64(String inventoryBase64) { this.inventoryBase64 = inventoryBase64; }

    public String getEnderChestBase64() { return enderChestBase64; }
    public void setEnderChestBase64(String enderChestBase64) { this.enderChestBase64 = enderChestBase64; }

    public String getEffectsBase64() { return effectsBase64; }
    public void setEffectsBase64(String effectsBase64) { this.effectsBase64 = effectsBase64; }
}
