package pl.dzoku.sectorsystem.model;

import java.util.UUID;

public class PlayerSnapshot {
    private final UUID uuid;
    private final String name;
    private final long timestamp;
    private final byte[] inventoryData;
    private final byte[] enderchestData;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final int expLevel;
    private final float expProgress;
    private final String gamemode;
    private final boolean flying;
    private final boolean allowFlight;
    private final byte[] potionEffects;
    private final double x, y, z;
    private final float yaw, pitch;
    private final String world;

    public PlayerSnapshot(UUID uuid, String name, byte[] inventoryData, byte[] enderchestData,
                          double health, int foodLevel, float saturation,
                          int expLevel, float expProgress, String gamemode,
                          boolean flying, boolean allowFlight, byte[] potionEffects,
                          double x, double y, double z, float yaw, float pitch, String world) {
        this.uuid = uuid;
        this.name = name;
        this.timestamp = System.currentTimeMillis();
        this.inventoryData = inventoryData;
        this.enderchestData = enderchestData;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.expLevel = expLevel;
        this.expProgress = expProgress;
        this.gamemode = gamemode;
        this.flying = flying;
        this.allowFlight = allowFlight;
        this.potionEffects = potionEffects;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.world = world;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public long getTimestamp() { return timestamp; }
    public byte[] getInventoryData() { return inventoryData; }
    public byte[] getEnderchestData() { return enderchestData; }
    public double getHealth() { return health; }
    public int getFoodLevel() { return foodLevel; }
    public float getSaturation() { return saturation; }
    public int getExpLevel() { return expLevel; }
    public float getExpProgress() { return expProgress; }
    public String getGamemode() { return gamemode; }
    public boolean isFlying() { return flying; }
    public boolean isAllowFlight() { return allowFlight; }
    public byte[] getPotionEffects() { return potionEffects; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public String getWorld() { return world; }
}
