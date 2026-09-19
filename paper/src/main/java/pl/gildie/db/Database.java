package pl.gildie.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import pl.gildie.Const;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public class Database {
    private HikariDataSource pool;
    private boolean failed = false;

    public void init() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:mysql://" + Const.DB_HOST + ":" + Const.DB_PORT + "/" + Const.DB_NAME
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        cfg.setUsername(Const.DB_USER);
        cfg.setPassword(Const.DB_PASS);
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setPoolName("gildie-pool");
        cfg.setConnectionTimeout(5000);
        cfg.setInitializationFailTimeout(5000);
        try {
            this.pool = new HikariDataSource(cfg);
            createTables();
            migrate();
        } catch (Exception e) {
            failed = true;
            Logger.getLogger("Gildie").severe("MySQL connection failed: " + e.getMessage());
            if (pool != null && !pool.isClosed()) pool.close();
            pool = null;
        }
    }

    public boolean isFailed() { return failed; }

    private void createTables() {
        try (Connection c = pool.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS guilds ("
                    + " tag VARCHAR(5) PRIMARY KEY,"
                    + " owner_uuid CHAR(36) NOT NULL,"
                    + " deputies JSON NOT NULL,"
                    + " members JSON NOT NULL,"
                    + " allies JSON NOT NULL,"
                    + " center JSON NOT NULL,"
                    + " radius INT NOT NULL DEFAULT 50,"
                    + " home JSON NULL,"
                    + " raid_base JSON NULL,"
                    + " raid_base_exp BIGINT NULL,"
                    + " raid_waypoint CHAR(36) NULL,"
                    + " guild_waypoint CHAR(36) NULL,"
                    + " egg JSON NULL,"
                    + " regen_blocks JSON NULL,"
                    + " wars JSON NULL,"
                    + " monument_effects JSON NULL,"
                    + " monument_center_captured_date VARCHAR(10) NULL,"
                    + " rank_points INT NOT NULL DEFAULT 1000,"
                    + " created_at BIGINT NOT NULL,"
                    + " updated_at BIGINT NOT NULL,"
                    + " INDEX idx_owner (owner_uuid)"
                    + ")");
            st.execute("CREATE TABLE IF NOT EXISTS users ("
                    + " uuid CHAR(36) PRIMARY KEY,"
                    + " name VARCHAR(16) NOT NULL,"
                    + " guild_tag VARCHAR(5) NULL,"
                    + " role VARCHAR(8) NULL,"
                    + " joined_at BIGINT NULL,"
                    + " points INT NOT NULL DEFAULT 0,"
                    + " kills INT NOT NULL DEFAULT 0,"
                    + " deaths INT NOT NULL DEFAULT 0,"
                    + " koxy INT NOT NULL DEFAULT 0,"
                    + " perly INT NOT NULL DEFAULT 0,"
                    + " tnt INT NOT NULL DEFAULT 0,"
                    + " monument_points INT NOT NULL DEFAULT 0,"
                    + " updated_at BIGINT NOT NULL,"
                    + " INDEX idx_guild (guild_tag),"
                    + " INDEX idx_name (name)"
                    + ")");
            st.execute("CREATE TABLE IF NOT EXISTS monument_crystals ("
                    + " id INT PRIMARY KEY,"
                    + " world VARCHAR(32) NOT NULL,"
                    + " x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,"
                    + " type VARCHAR(10) NOT NULL,"
                    + " active BOOLEAN NOT NULL DEFAULT FALSE,"
                    + " respawn_at BIGINT NOT NULL DEFAULT 0,"
                    + " last_spawn_date VARCHAR(10) NULL,"
                    + " captured_date VARCHAR(10) NULL,"
                    + " INDEX idx_type (type)"
                    + ")");
            st.execute("CREATE TABLE IF NOT EXISTS monument_center_hits ("
                    + " uuid CHAR(36) PRIMARY KEY,"
                    + " name VARCHAR(16) NOT NULL,"
                    + " hits INT NOT NULL DEFAULT 0"
                    + ")");
            st.execute("CREATE TABLE IF NOT EXISTS placed_dispensers ("
                    + " world VARCHAR(32) NOT NULL,"
                    + " x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,"
                    + " owner_tag VARCHAR(5) NULL,"
                    + " PRIMARY KEY (world, x, y, z)"
                    + ")");
            st.execute("INSERT IGNORE INTO monument_crystals (id, world, x, y, z, type) VALUES (0,'world',0,0,0,'CENTER')");
            for (int i = 1; i <= 4; i++) {
                st.execute("INSERT IGNORE INTO monument_crystals (id, world, x, y, z, type) VALUES (" + i + ",'world',0,0,0,'CORNER')");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("MySQL init failed", e);
        }
    }

    private void migrate() {
        try (Connection c = pool.getConnection(); Statement st = c.createStatement()) {
            st.execute("ALTER TABLE guilds MODIFY regen_blocks JSON NULL");
            st.execute("ALTER TABLE guilds MODIFY wars JSON NULL");
            st.execute("ALTER TABLE guilds ADD COLUMN monument_effects JSON NULL");
            st.execute("ALTER TABLE guilds ADD COLUMN monument_center_captured_date VARCHAR(10) NULL");
            st.execute("ALTER TABLE guilds ADD COLUMN rank_points INT NOT NULL DEFAULT 1000");
            st.execute("ALTER TABLE users ADD COLUMN monument_points INT NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE monument_crystals ADD COLUMN captured_date VARCHAR(10) NULL");
        } catch (SQLException ignored) { }
    }

    public Connection getConnection() throws SQLException {
        if (pool == null) throw new SQLException("Database not initialized");
        return pool.getConnection();
    }

    public void close() { if (pool != null && !pool.isClosed()) pool.close(); }
}
