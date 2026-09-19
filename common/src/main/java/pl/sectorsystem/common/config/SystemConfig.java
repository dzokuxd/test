package pl.sectorsystem.common.config;

import pl.sectorsystem.common.SectorDefinition;
import pl.sectorsystem.common.SectorType;

import java.util.HashMap;
import java.util.Map;

public class SystemConfig {
    private RedisConfig redis = new RedisConfig();
    private NatsConfig nats = new NatsConfig();
    private MySQLConfig mysql = new MySQLConfig();
    private Map<String, SectorDefinition> sectors = new HashMap<>();

    public SystemConfig() {
        sectors.put("guild", new SectorDefinition("guild", "Gildia", SectorType.GUILD, "guild"));
        sectors.put("spawn", new SectorDefinition("spawn", "Spawn", SectorType.SPAWN, "spawn"));
        sectors.put("afk", new SectorDefinition("afk", "AFK", SectorType.AFK, "afk"));
    }

    public RedisConfig getRedis() { return redis; }
    public void setRedis(RedisConfig redis) { this.redis = redis; }

    public NatsConfig getNats() { return nats; }
    public void setNats(NatsConfig nats) { this.nats = nats; }

    public MySQLConfig getMysql() { return mysql; }
    public void setMysql(MySQLConfig mysql) { this.mysql = mysql; }

    public Map<String, SectorDefinition> getSectors() { return sectors; }
    public void setSectors(Map<String, SectorDefinition> sectors) { this.sectors = sectors; }

    public static class RedisConfig {
        private String host = "127.0.0.1";
        private int port = 6379;
        private String password = "";
        private int database = 0;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getDatabase() { return database; }
        public void setDatabase(int database) { this.database = database; }
    }

    public static class NatsConfig {
        private String url = "nats://127.0.0.1:4222";
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public static class MySQLConfig {
        private boolean enabled = true;
        private String host = "mysql.titanaxe.com";
        private int port = 3306;
        private String database = "srv325281";
        private String username = "srv325281";
        private String password = "Vaw4wCB4";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        
        private boolean whitelistEnabled = false;
        private String whitelistReason = "Serwer jest w trakcie konfiguracji.";
        private int maxSlots = 100;
        private String motd = "Witaj na serwerze!";
        
        public boolean isWhitelistEnabled() { return whitelistEnabled; }
        public void setWhitelistEnabled(boolean whitelistEnabled) { this.whitelistEnabled = whitelistEnabled; }
        public String getWhitelistReason() { return whitelistReason; }
        public void setWhitelistReason(String whitelistReason) { this.whitelistReason = whitelistReason; }
        public int getMaxSlots() { return maxSlots; }
        public void setMaxSlots(int maxSlots) { this.maxSlots = maxSlots; }
        public String getMotd() { return motd; }
        public void setMotd(String motd) { this.motd = motd; }
        public void setPassword(String password) { this.password = password; }
    }
}
