package pl.gildie.war;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class War {
    public enum State { ACTIVE, ENDED_CONQUEST, ENDED_KILLS, ENDED_TIMEOUT }

    private final UUID id;
    private final String attackerTag;
    private final String defenderTag;
    private final long startTime;
    private final long durationMs;
    private long endTime;
    private State state = State.ACTIVE;
    private final Map<String, WarStats> stats = new HashMap<>();
    private UUID activeBannerId;
    private String bannerCarrierGuild;
    private UUID bannerCarrierPlayer;

    private String declarerName = "?";
    private boolean conquestByAttacker = false;
    private boolean conquestByDefender = false;

    public War(UUID id, String attackerTag, String defenderTag, long durationMs) {
        this.id = id;
        this.attackerTag = attackerTag.toUpperCase();
        this.defenderTag = defenderTag.toUpperCase();
        this.startTime = System.currentTimeMillis();
        this.durationMs = durationMs;
        this.endTime = startTime + durationMs;
        this.stats.put(this.attackerTag, new WarStats());
        this.stats.put(this.defenderTag, new WarStats());
    }

    public War(UUID id, String attackerTag, String defenderTag, long startTime, long durationMs,
               long endTime, State state, Map<String, WarStats> stats) {
        this.id = id;
        this.attackerTag = attackerTag.toUpperCase();
        this.defenderTag = defenderTag.toUpperCase();
        this.startTime = startTime;
        this.durationMs = durationMs;
        this.endTime = endTime;
        this.state = state;
        this.stats.putAll(stats);
    }

    public UUID getId() { return id; }
    public String getAttackerTag() { return attackerTag; }
    public String getDefenderTag() { return defenderTag; }
    public long getStartTime() { return startTime; }
    public long getDurationMs() { return durationMs; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    public String getDeclarerName() { return declarerName; }
    public void setDeclarerName(String n) { this.declarerName = n == null ? "?" : n; }
    public boolean isConquestByAttacker() { return conquestByAttacker; }
    public void setConquestByAttacker(boolean b) { this.conquestByAttacker = b; }
    public boolean isConquestByDefender() { return conquestByDefender; }
    public void setConquestByDefender(boolean b) { this.conquestByDefender = b; }

    public WarStats getStats(String tag) { return stats.computeIfAbsent(tag.toUpperCase(), t -> new WarStats()); }
    public Map<String, WarStats> getAllStats() { return stats; }

    public boolean isParticipant(String tag) {
        if (tag == null) return false;
        tag = tag.toUpperCase();
        return tag.equals(attackerTag) || tag.equals(defenderTag);
    }

    public String getOpponent(String tag) {
        if (tag == null) return null;
        tag = tag.toUpperCase();
        if (tag.equals(attackerTag)) return defenderTag;
        if (tag.equals(defenderTag)) return attackerTag;
        return null;
    }

    public boolean isActive() { return state == State.ACTIVE && System.currentTimeMillis() < endTime; }
    public boolean isExpired() { return state == State.ACTIVE && System.currentTimeMillis() >= endTime; }
    public long getRemainingMs() { return Math.max(0, endTime - System.currentTimeMillis()); }

    public UUID getActiveBannerId() { return activeBannerId; }
    public void setActiveBannerId(UUID activeBannerId) { this.activeBannerId = activeBannerId; }
    public String getBannerCarrierGuild() { return bannerCarrierGuild; }
    public void setBannerCarrierGuild(String t) { this.bannerCarrierGuild = t == null ? null : t.toUpperCase(); }
    public UUID getBannerCarrierPlayer() { return bannerCarrierPlayer; }
    public void setBannerCarrierPlayer(UUID u) { this.bannerCarrierPlayer = u; }

    public void clearBanner() {
        this.activeBannerId = null;
        this.bannerCarrierGuild = null;
        this.bannerCarrierPlayer = null;
    }
}
