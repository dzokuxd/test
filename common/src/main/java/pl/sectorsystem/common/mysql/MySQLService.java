package pl.sectorsystem.common.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import pl.sectorsystem.common.config.SystemConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

public class MySQLService implements AutoCloseable {

    private final SystemConfig.MySQLConfig config;
    private HikariDataSource dataSource;
    private volatile boolean enabled;

    public MySQLService(SystemConfig.MySQLConfig config) {
        this.config = config;
        this.enabled = config != null && config.isEnabled();
        if (enabled) {
            try {
                initPool();
                createTables();
                System.out.println("[MySQL] HikariCP pool uruchomiony pomyślnie!");
            } catch (Exception e) {
                System.err.println("[MySQL] Nie udało się uruchomić poola: " + e.getMessage());
                e.printStackTrace();
                enabled = false;
                closeQuietly();
            }
        }
    }

    public boolean isEnabled() {
        return enabled && dataSource != null && !dataSource.isClosed();
    }

    private void initPool() throws Exception {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("[MySQL] Driver zarejestrowany");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL Driver not found in classpath", e);
        }

        HikariConfig hc = new HikariConfig();
        String jdbcUrl = "jdbc:mysql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase()
                       + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC";
        
        hc.setJdbcUrl(jdbcUrl);
        hc.setUsername(config.getUsername());
        hc.setPassword(config.getPassword());
        hc.setMaximumPoolSize(10);
        hc.setMinimumIdle(2);
        hc.setConnectionTimeout(10000);
        hc.setIdleTimeout(300000);
        hc.setMaxLifetime(1800000);
        hc.setPoolName("SectorSystem-MySQL");
        hc.setConnectionTestQuery("SELECT 1");

        this.dataSource = new HikariDataSource(hc);
        System.out.println("[HikariCP] Pool stworzony: " + hc.getPoolName());
    }

    public Connection getConnection() throws Exception {
        if (dataSource == null || dataSource.isClosed()) {
            throw new IllegalStateException("HikariCP pool is not available");
        }
        return dataSource.getConnection();
    }

    private void createTables() throws Exception {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS auth_players (uuid VARCHAR(36) PRIMARY KEY, username VARCHAR(32) NOT NULL, premium TINYINT(1) NOT NULL DEFAULT 0, password VARCHAR(255) NOT NULL DEFAULT '', registered TINYINT(1) NOT NULL DEFAULT 0, firstIP VARCHAR(64) NOT NULL, lastIP VARCHAR(64) NOT NULL, rememberIP VARCHAR(64) NOT NULL DEFAULT '') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS auth_whitelist (username VARCHAR(32) PRIMARY KEY, added_at BIGINT DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS sector_players (uuid VARCHAR(36) PRIMARY KEY, username VARCHAR(16) NOT NULL, current_sector VARCHAR(64), last_sector VARCHAR(64), first_join BIGINT, last_join BIGINT, playtime_seconds BIGINT DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS sector_stats (uuid VARCHAR(36) PRIMARY KEY, deaths INT DEFAULT 0, transfers INT DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            System.out.println("[MySQL] Tabele utworzone/zweryfikowane");
        }
    }

    public void upsertPlayer(UUID uuid, String username, String currentSector, String lastSector) {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();
        try (Connection c = getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO sector_players (uuid, username, current_sector, last_sector, first_join, last_join) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE username = VALUES(username), current_sector = VALUES(current_sector), last_sector = VALUES(last_sector), last_join = VALUES(last_join)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setString(3, currentSector);
                ps.setString(4, lastSector);
                ps.setLong(5, now);
                ps.setLong(6, now);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("[MySQL] upsertPlayer error: " + e.getMessage());
        }
    }

    public Optional<String> getLastSector(UUID uuid) {
        if (!isEnabled()) return Optional.empty();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("SELECT last_sector FROM sector_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.ofNullable(rs.getString("last_sector"));
            }
        } catch (Exception e) {
            System.err.println("[MySQL] getLastSector error: " + e.getMessage());
        }
        return Optional.empty();
    }

    private void closeQuietly() {
        if (dataSource != null) {
            try { dataSource.close(); } catch (Exception ignored) {}
            dataSource = null;
        }
    }

    @Override
    public void close() {
        System.out.println("[HikariCP] Zamykanie poola...");
        enabled = false;
        closeQuietly();
        System.out.println("[HikariCP] Pool zamknięty");
    }

    public void incrementDeaths(UUID uuid) {
        if (!isEnabled()) return;
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE sector_stats SET deaths = deaths + 1 WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    public void incrementTransfers(UUID uuid) {
        if (!isEnabled()) return;
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE sector_stats SET transfers = transfers + 1 WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

}