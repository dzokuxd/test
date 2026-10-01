package pl.dzoku.sectorsystem.config;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;
import org.yaml.snakeyaml.Yaml;

/**
 * Czyta config.yml. Wspiera ${ENV_VAR:default}
 */
public class ProxyConfigManager {
    private static final Pattern ENV = Pattern.compile("\\$\\{([^:}]+):?([^}]*)\\}");
    private final Path configFile;
    private final Logger logger;
    private Map<String, Object> config;

    public ProxyConfigManager(Path dataDir, Logger logger) {
        this.logger = logger;
        this.configFile = dataDir.resolve("config.yml");
        load();
    }

    public void load() {
        try {
            if (!Files.exists(configFile)) saveDefault();
            Yaml yaml = new Yaml();
            try (InputStream is = Files.newInputStream(configFile)) {
                config = yaml.load(is);
                if (config == null) config = new HashMap<>();
                resolveEnv(config);
                logger.info("Config loaded: " + configFile);
            }
        } catch (Exception e) {
            logger.severe("Config error: " + e.getMessage());
            config = new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private void resolveEnv(Map<String, Object> map) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String) e.setValue(resolveStr((String) v));
            else if (v instanceof Map) resolveEnv((Map<String, Object>) v);
            else if (v instanceof List) {
                List<Object> l = (List<Object>) v;
                for (int i = 0; i < l.size(); i++)
                    if (l.get(i) instanceof String) l.set(i, resolveStr((String) l.get(i)));
            }
        }
    }

    private String resolveStr(String s) {
        Matcher m = ENV.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String env = System.getenv(m.group(1));
            String def = m.group(2) != null ? m.group(2) : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(
                (env != null && !env.isEmpty()) ? env : def));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private void saveDefault() {
        try {
            Files.createDirectories(configFile.getParent());
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                if (is != null) { Files.copy(is, configFile); return; }
            }
            Files.writeString(configFile, "# SectorSystem Proxy Config\n");
        } catch (Exception e) { logger.severe("Cannot create config: " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String path, T def) {
        if (config == null) return def;
        String[] keys = path.split("\\.");
        Map<String, Object> cur = config;
        for (int i = 0; i < keys.length - 1; i++) {
            Object n = cur.get(keys[i]);
            if (n instanceof Map) cur = (Map<String, Object>) n;
            else return def;
        }
        Object v = cur.get(keys[keys.length - 1]);
        if (v == null) return def;
        try {
            if (def instanceof Integer) {
                if (v instanceof Number) return (T) Integer.valueOf(((Number) v).intValue());
                if (v instanceof String) { try { return (T) Integer.valueOf(Integer.parseInt(((String) v).trim())); } catch (NumberFormatException e) { return def; } }
                return def;
            }
            if (def instanceof Long) {
                if (v instanceof Number) return (T) Long.valueOf(((Number) v).longValue());
                if (v instanceof String) { try { return (T) Long.valueOf(Long.parseLong(((String) v).trim())); } catch (NumberFormatException e) { return def; } }
                return def;
            }
            if (def instanceof Boolean) {
                if (v instanceof Boolean) return (T) v;
                if (v instanceof String) return (T) Boolean.valueOf(Boolean.parseBoolean(((String) v).trim()));
                return def;
            }
            if (def instanceof String) return (T) String.valueOf(v);
            return (T) v;
        } catch (Exception e) { return def; }
    }

    // Redis
    public String getRedisHost() { return get("redis.host", "localhost"); }
    public int getRedisPort() { return get("redis.port", 6379); }
    public String getRedisPass() { return get("redis.password", ""); }
    public int getRedisMaxConn() { return get("redis.max-connections", 10); }
    // NATS
    public String getNatsUrl() { return get("nats.url", "nats://localhost:4222"); }
    // MySQL
    public boolean isMysqlEnabled() { return get("mysql.enabled", true); }
    public String getMysqlHost() { return get("mysql.host", "localhost"); }
    public int getMysqlPort() { return get("mysql.port", 3306); }
    public String getMysqlDb() { return get("mysql.database", "sectorsystem"); }
    public String getMysqlUser() { return get("mysql.username", "root"); }
    public String getMysqlPass() { return get("mysql.password", ""); }
    public int getMysqlPool() { return get("mysql.pool-size", 10); }
    // Auth
    public int getLoginTimeout() { return get("auth.login-timeout", 60); }
    public int getMaxAttempts() { return get("auth.max-attempts", 5); }
    public int getRememberIpHours() { return get("auth.remember-ip-hours", 24); }
    public boolean isAutoRegister() { return get("auth.auto-register", false); }
    public boolean isPremiumSkip() { return get("auth.premium-skip-login", true); }
    public boolean isWhitelist() { return get("auth.whitelist.enabled", false); }
    // Limbo
    public String getLimboName() { return get("limbo.name", "limbo"); }
    public String getLimboDisplay() { return get("limbo.display-name", "Poczekalnia"); }
    // Health
    public int getHealthInterval() { return get("health-check.interval-seconds", 15); }
    public int getOfflineThreshold() { return get("health-check.offline-threshold", 3); }
    // Sectors
    public String getDefaultSector() { return get("sectors.default", "guild"); }
    public int getSectorMax(String s) { return get("sectors." + s + ".max-players", 0); }
    // Restart
    public int getRestartGrace() { return get("restart.grace-period-seconds", 60); }
    public int getRestartMaxAttempts() { return get("restart.max-attempts", 3); }
    // Debug
    public boolean isDebugTransfers() { return get("debug.log-transfers", false); }
    public boolean isDebugAuth() { return get("debug.log-auth", false); }
}
