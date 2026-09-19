package pl.dzoku.sectorsystem.config;

import org.yaml.snakeyaml.Yaml;
import pl.sectorsystem.common.config.SystemConfig;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ProxyConfigManager {
    private final Path configPath;
    private final SystemConfig systemConfig;

    public ProxyConfigManager(Path dataDirectory, SystemConfig systemConfig) {
        this.configPath = dataDirectory.resolve("config.yml");
        this.systemConfig = systemConfig;
    }

    public void load() {
        try {
            if (!Files.exists(configPath)) {
                createDefaultConfig();
            }

            Yaml yaml = new Yaml();
            try (InputStream in = Files.newInputStream(configPath)) {
                Map<String, Object> config = yaml.load(in);
                if (config == null) return;
                applyConfig(config);
            }
        } catch (Exception e) {
            System.err.println("[ProxyConfig] Błąd wczytywania config.yml: " + e.getMessage());
        }
    }

    private void createDefaultConfig() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# SectorSystem Proxy Configuration\n");
        sb.append("redis:\n");
        sb.append("  host: localhost\n");
        sb.append("  port: 6379\n");
        sb.append("  max-connections: 10\n");
        sb.append("\n");
        sb.append("nats:\n");
        sb.append("  url: nats://localhost:4222\n");
        sb.append("\n");
        sb.append("mysql:\n");
        sb.append("  enabled: true\n");
        sb.append("  host: localhost\n");
        sb.append("  port: 3306\n");
        sb.append("  database: sectorsystem\n");
        sb.append("  username: root\n");
        sb.append("  password: \"\"\n");
        sb.append("\n");
        sb.append("auth:\n");
        sb.append("  login-timeout: 60\n");
        sb.append("  default-server: guild\n");
        
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, sb.toString());
    }

    @SuppressWarnings("unchecked")
    private void applyConfig(Map<String, Object> config) {
        Map<String, Object> redis = (Map<String, Object>) config.getOrDefault("redis", Map.of());
        systemConfig.getRedis().setHost((String) redis.getOrDefault("host", "localhost"));
        systemConfig.getRedis().setPort((Integer) redis.getOrDefault("port", 6379));

        Map<String, Object> nats = (Map<String, Object>) config.getOrDefault("nats", Map.of());
        systemConfig.getNats().setUrl((String) nats.getOrDefault("url", "nats://localhost:4222"));

        Map<String, Object> mysql = (Map<String, Object>) config.getOrDefault("mysql", Map.of());
        systemConfig.getMysql().setEnabled((Boolean) mysql.getOrDefault("enabled", true));
        systemConfig.getMysql().setHost((String) mysql.getOrDefault("host", "localhost"));
        systemConfig.getMysql().setPort((Integer) mysql.getOrDefault("port", 3306));
        systemConfig.getMysql().setDatabase((String) mysql.getOrDefault("database", "sectorsystem"));
        systemConfig.getMysql().setUsername((String) mysql.getOrDefault("username", "root"));
        systemConfig.getMysql().setPassword((String) mysql.getOrDefault("password", ""));
    }
}
