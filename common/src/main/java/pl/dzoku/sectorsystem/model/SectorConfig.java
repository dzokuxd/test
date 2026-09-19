package pl.dzoku.sectorsystem.model;

public class SectorConfig {
    private String id;
    private String displayName;
    private int maxPlayers;
    private boolean bounded;
    private int minX, maxX, minZ, maxZ;
    private int minY, maxY;
    private String fallbackSector;
    private boolean pvpEnabled;
    private boolean buildEnabled;
    private long transferCooldownMs;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public boolean isBounded() { return bounded; }
    public void setBounded(boolean bounded) { this.bounded = bounded; }
    public int getMinX() { return minX; }
    public void setMinX(int minX) { this.minX = minX; }
    public int getMaxX() { return maxX; }
    public void setMaxX(int maxX) { this.maxX = maxX; }
    public int getMinZ() { return minZ; }
    public void setMinZ(int minZ) { this.minZ = minZ; }
    public int getMaxZ() { return maxZ; }
    public void setMaxZ(int maxZ) { this.maxZ = maxZ; }
    public int getMinY() { return minY; }
    public void setMinY(int minY) { this.minY = minY; }
    public int getMaxY() { return maxY; }
    public void setMaxY(int maxY) { this.maxY = maxY; }
    public String getFallbackSector() { return fallbackSector; }
    public void setFallbackSector(String fallbackSector) { this.fallbackSector = fallbackSector; }
    public boolean isPvpEnabled() { return pvpEnabled; }
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }
    public boolean isBuildEnabled() { return buildEnabled; }
    public void setBuildEnabled(boolean buildEnabled) { this.buildEnabled = buildEnabled; }
    public long getTransferCooldownMs() { return transferCooldownMs; }
    public void setTransferCooldownMs(long transferCooldownMs) { this.transferCooldownMs = transferCooldownMs; }

    public boolean isInBounds(double x, double y, double z) {
        if (!bounded) return true;
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }
}
