package pl.gildie.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import pl.gildie.Const;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Guild {
    private final String tag;
    private UUID owner;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> deputies = new HashSet<>();
    private final String worldName;
    private final double x, y, z;
    private final int radius;

    private String homeWorld;
    private double homeX, homeY, homeZ;
    private boolean hasHome;

    private final Map<UUID, Long> pendingInvites = new HashMap<>();

    private String raidWorld;
    private double raidX, raidY, raidZ;
    private long raidExpiresAt;
    private UUID raidWaypointId;

    private UUID guildWaypointId;

    private double eggX, eggY = 40, eggZ;
    private boolean hasEgg;

    private final Set<String> allies = new HashSet<>();
    private final Map<String, Long> pendingAlliance = new HashMap<>();

    private int rankPoints = Const.WAR_RANK_START_POINTS;

    public Guild(String tag, UUID owner, Location center, int radius) {
        this.tag = tag.toUpperCase();
        this.owner = owner;
        this.members.add(owner);
        this.worldName = center.getWorld().getName();
        this.x = center.getX(); this.y = center.getY(); this.z = center.getZ();
        this.radius = radius;
        this.homeWorld = this.worldName;
        this.homeX = this.x; this.homeY = this.y; this.homeZ = this.z;
        this.hasHome = true;
    }

    public Guild(String tag, UUID owner, String worldName, double x, double y, double z, int radius) {
        this.tag = tag.toUpperCase();
        this.owner = owner;
        this.members.add(owner);
        this.worldName = worldName;
        this.x = x; this.y = y; this.z = z;
        this.radius = radius;
        this.homeWorld = worldName;
        this.homeX = x; this.homeY = y; this.homeZ = z;
        this.hasHome = true;
    }

    public String getTag() { return tag; }
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; members.add(owner); deputies.remove(owner); }
    public Set<UUID> getMembers() { return members; }
    public Set<UUID> getDeputies() { return deputies; }
    public String getWorldName() { return worldName; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public int getRadius() { return radius; }

    public int getRankPoints() { return rankPoints; }
    public void setRankPoints(int p) { this.rankPoints = Math.max(0, p); }

    public Location getCenter() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    public Location getCenterAtY(double atY) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, atY, z);
    }

    public boolean isMember(UUID uuid) { return members.contains(uuid); }
    public boolean isOwner(UUID uuid) { return owner.equals(uuid); }
    public boolean isDeputy(UUID uuid) { return deputies.contains(uuid); }
    public boolean isLeaderOrDeputy(UUID uuid) { return isOwner(uuid) || isDeputy(uuid); }

    public boolean isInTerritory(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;
        double dx = loc.getX() - x, dz = loc.getZ() - z;
        return (dx * dx + dz * dz) <= (double) radius * radius;
    }

    public double distanceToBorder(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return Double.MAX_VALUE;
        double dx = loc.getX() - x, dz = loc.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz) - radius;
    }

    public void addMember(UUID uuid) { members.add(uuid); pendingInvites.remove(uuid); }
    public void removeMember(UUID uuid) { if (owner.equals(uuid)) return; members.remove(uuid); deputies.remove(uuid); }
    public void addDeputy(UUID uuid) { if (members.contains(uuid) && !owner.equals(uuid)) deputies.add(uuid); }
    public void removeDeputy(UUID uuid) { deputies.remove(uuid); }

    public boolean hasHome() { return hasHome && homeWorld != null; }
    public Location getHome() {
        if (!hasHome()) return getCenter();
        World world = Bukkit.getWorld(homeWorld);
        if (world == null) return null;
        return new Location(world, homeX, homeY, homeZ);
    }
    public void setHome(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        this.homeWorld = loc.getWorld().getName();
        this.homeX = loc.getX(); this.homeY = loc.getY(); this.homeZ = loc.getZ();
        this.hasHome = true;
    }
    public String getHomeWorld() { return homeWorld; }
    public double getHomeX() { return homeX; }
    public double getHomeY() { return homeY; }
    public double getHomeZ() { return homeZ; }
    public void loadHome(String world, double hx, double hy, double hz) {
        this.homeWorld = world; this.homeX = hx; this.homeY = hy; this.homeZ = hz; this.hasHome = world != null;
    }

    public void addInvite(UUID target, long expireAt) { pendingInvites.put(target, expireAt); }
    public boolean hasInvite(UUID target) {
        Long exp = pendingInvites.get(target);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) { pendingInvites.remove(target); return false; }
        return true;
    }
    public void removeInvite(UUID target) { pendingInvites.remove(target); }
    public Map<UUID, Long> getPendingInvites() { return pendingInvites; }

    public boolean hasActiveRaidBase() { return raidExpiresAt > System.currentTimeMillis() && raidWorld != null; }
    public Location getRaidBase() {
        if (!hasActiveRaidBase()) return null;
        World world = Bukkit.getWorld(raidWorld);
        if (world == null) return null;
        return new Location(world, raidX, raidY, raidZ);
    }
    public void setRaidBase(Location loc, long durationMs, UUID waypointId) {
        if (loc == null || loc.getWorld() == null) return;
        this.raidWorld = loc.getWorld().getName();
        this.raidX = loc.getX(); this.raidY = loc.getY(); this.raidZ = loc.getZ();
        this.raidExpiresAt = System.currentTimeMillis() + durationMs;
        this.raidWaypointId = waypointId;
    }
    public void clearRaidBase() { this.raidWorld = null; this.raidExpiresAt = 0; this.raidWaypointId = null; }
    public long getRaidExpiresAt() { return raidExpiresAt; }
    public UUID getRaidWaypointId() { return raidWaypointId; }
    public void setRaidWaypointId(UUID id) { this.raidWaypointId = id; }
    public String getRaidWorld() { return raidWorld; }
    public double getRaidX() { return raidX; }
    public double getRaidY() { return raidY; }
    public double getRaidZ() { return raidZ; }
    public void loadRaidBase(String world, double rx, double ry, double rz, long expiresAt, UUID wpId) {
        this.raidWorld = world; this.raidX = rx; this.raidY = ry; this.raidZ = rz;
        this.raidExpiresAt = expiresAt; this.raidWaypointId = wpId;
    }
    public boolean isRaidBaseBlock(Location loc) {
        if (!hasActiveRaidBase() || loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(raidWorld)) return false;
        return loc.getBlockX() == (int) Math.floor(raidX)
                && loc.getBlockY() == (int) Math.floor(raidY)
                && loc.getBlockZ() == (int) Math.floor(raidZ);
    }

    public UUID getGuildWaypointId() { return guildWaypointId; }
    public void setGuildWaypointId(UUID id) { this.guildWaypointId = id; }

    public Set<String> getAllies() { return allies; }
    public boolean isAlliedWith(String tag) { return tag != null && allies.contains(tag.toUpperCase()); }
    public void addAlly(String tag) {
        if (tag == null || tag.isBlank()) return;
        tag = tag.toUpperCase();
        if (!allies.isEmpty() && !allies.contains(tag)) return;
        allies.add(tag);
    }
    public void removeAlly(String tag) { if (tag != null) allies.remove(tag.toUpperCase()); }
    public void addAllianceRequest(String tag, long expireAt) {
        if (tag == null || tag.isBlank()) return;
        pendingAlliance.put(tag.toUpperCase(), expireAt);
    }
    public boolean hasAllianceRequestFrom(String tag) {
        if (tag == null) return false;
        Long exp = pendingAlliance.get(tag.toUpperCase());
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) { pendingAlliance.remove(tag.toUpperCase()); return false; }
        return true;
    }
    public void removeAllianceRequest(String tag) { if (tag != null) pendingAlliance.remove(tag.toUpperCase()); }
    public Map<String, Long> getPendingAlliance() { return pendingAlliance; }
    public void loadAllies(List<String> list) {
        allies.clear();
        if (list == null) return;
        for (String t : list) if (t != null && !t.isBlank()) allies.add(t.toUpperCase());
    }

    private int eggHp = 500;
    private int maxEggHp = 500;

    public boolean hasEgg() { return hasEgg; }
    public double getEggX() { return eggX; }
    public double getEggY() { return eggY; }
    public double getEggZ() { return eggZ; }
    public void setEgg(double x, double y, double z) { this.eggX = x; this.eggY = y; this.eggZ = z; this.hasEgg = true; }
    public void loadEgg(double x, double y, double z) { setEgg(x, y, z); }
    public void loadEgg(double x, double y, double z, int hp, int maxHp) {
        setEgg(x, y, z);
        this.maxEggHp = Math.max(1, maxHp);
        this.eggHp = Math.max(0, Math.min(hp, this.maxEggHp));
    }
    public Location getEggLocation() {
        if (!hasEgg) return null;
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w, eggX, eggY, eggZ);
    }
    public boolean isEggBlock(Location loc) {
        if (!hasEgg || loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;
        return loc.getBlockX() == (int) Math.floor(eggX)
                && loc.getBlockY() == (int) Math.floor(eggY)
                && loc.getBlockZ() == (int) Math.floor(eggZ);
    }

    public int getEggHp() { return eggHp; }
    public int getMaxEggHp() { return maxEggHp; }
    public void setMaxEggHp(int max) { this.maxEggHp = Math.max(1, max); if (eggHp > maxEggHp) eggHp = maxEggHp; }
    public void setEggHp(int hp) { this.eggHp = Math.max(0, Math.min(hp, maxEggHp)); }
    public boolean damageEgg(int amount) { eggHp = Math.max(0, eggHp - Math.max(0, amount)); return eggHp <= 0; }
    public boolean regenEgg(int amount) {
        if (eggHp >= maxEggHp) return false;
        int before = eggHp;
        eggHp = Math.min(maxEggHp, eggHp + Math.max(0, amount));
        return eggHp != before;
    }
}
