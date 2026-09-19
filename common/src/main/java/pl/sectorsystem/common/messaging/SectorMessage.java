package pl.sectorsystem.common.messaging;

import java.util.UUID;

public class SectorMessage {
    public enum Type {
        TRANSFER_REQUEST,
        TRANSFER_ACCEPT,
        TRANSFER_COMPLETE,
        TRANSFER_FAILED,
        PLAYER_DATA_UPDATE,
        SECTOR_STATUS,
        FORCE_TRANSFER,
        GLOBAL_CHAT,
        TABLIST_UPDATE,
        SCOREBOARD_UPDATE,
        QUEUE_JOIN,
        QUEUE_LEAVE,
        QUEUE_UPDATE,
        HEARTBEAT,
        FAILSAFE_SHUTDOWN
    }

    private Type type;
    private UUID playerUuid;
    private String playerName;
    private String fromSector;
    private String toSector;
    private String reason;
    private long timestamp;
    private String payload;
    private String message;
    private String format;

    public SectorMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public SectorMessage(Type type) {
        this();
        this.type = type;
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public UUID getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public String getFromSector() { return fromSector; }
    public void setFromSector(String fromSector) { this.fromSector = fromSector; }
    public String getToSector() { return toSector; }
    public void setToSector(String toSector) { this.toSector = toSector; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
}
