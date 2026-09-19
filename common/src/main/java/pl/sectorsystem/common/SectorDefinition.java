package pl.sectorsystem.common;

public class SectorDefinition {
    private String id;
    private String name;
    private SectorType type;
    private String serverName;      // nazwa serwera w Velocity
    private boolean allowWalkIn;    // zawsze false w naszym systemie
    private boolean allowFreeTp;    // zawsze false

    public SectorDefinition() {}

    public SectorDefinition(String id, String name, SectorType type, String serverName) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.serverName = serverName;
        this.allowWalkIn = false;
        this.allowFreeTp = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public SectorType getType() { return type; }
    public void setType(SectorType type) { this.type = type; }

    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }

    public boolean isAllowWalkIn() { return allowWalkIn; }
    public void setAllowWalkIn(boolean allowWalkIn) { this.allowWalkIn = allowWalkIn; }

    public boolean isAllowFreeTp() { return allowFreeTp; }
    public void setAllowFreeTp(boolean allowFreeTp) { this.allowFreeTp = allowFreeTp; }
}
