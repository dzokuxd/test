package pl.gildie.db;

import pl.gildie.Const;
import pl.sectorsystem.common.mysql.MySQLService;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Adapter do MySQLService - używa wspólnego connection pool'a
 */
public class Database {
    private final MySQLService mysqlService;
    private boolean failed = false;

    public Database(MySQLService mysqlService) {
        this.mysqlService = mysqlService;
    }

    public void init() {
        if (mysqlService == null || !mysqlService.isEnabled()) {
            failed = true;
            Logger.getLogger("Gildie").severe("MySQLService not available");
            return;
        }

        try {
            createTables();
            migrate();
            Logger.getLogger("Gildie").info("Gildie tables initialized");
        } catch (Exception e) {
            failed = true;
            Logger.getLogger("Gildie").severe("MySQL init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isFailed() { return failed; }

    private void createTables() throws Exception {
        try (Connection c = mysqlService.getConnection(); Statement st = c.createStatement()) {
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
                    + " egg JSON NULL,"
                    + " regen_blocks JSON NULL,"
                    + " wars JSON NULL,"
                    + " monument_effects JSON NULL,"
                    + " monument_center_captured_date VARCHAR(10) NULL,"
                    + " rank_points INT NOT NULL DEFAULT 1000,"
                    // ── NOWE KOLUMNY SYSTEMU OCENIANIA (dla nowych instalacji) ──
                    + " admin_rating_sum INT NOT NULL DEFAULT 0,"
                    + " rated_guild_tag VARCHAR(10) NULL,"
                    + " player_votes JSON NULL,"
                    + " received_player_rating_sum INT NOT NULL DEFAULT 0,"
                    + " received_player_rating_count INT NOT NULL DEFAULT 0,"
                    // ─────────────────────────────────────────────────────────────
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

            st.execute("CREATE TABLE IF NOT EXISTS monument_effects ("
                    + " guild_tag VARCHAR(16) PRIMARY KEY,"
                    + " effects_json TEXT NOT NULL"
                    + ")");
            st.execute("CREATE TABLE IF NOT EXISTS placed_dispensers ("
                    + " world VARCHAR(32) NOT NULL,"
                    + " x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,"
                    + " owner_tag VARCHAR(5) NULL,"
                    + " PRIMARY KEY (world, x, y, z)"
                    + ")");
        }
    }

    private void migrate() throws Exception {
        try (Connection c = mysqlService.getConnection(); Statement st = c.createStatement()) {
            // Istniejące migracje
            st.execute("ALTER TABLE guilds MODIFY regen_blocks JSON NULL");
            st.execute("ALTER TABLE guilds MODIFY wars JSON NULL");
            st.execute("ALTER TABLE guilds ADD COLUMN monument_effects JSON NULL");
            st.execute("ALTER TABLE guilds ADD COLUMN monument_center_captured_date VARCHAR(10) NULL");
            st.execute("ALTER TABLE guilds ADD COLUMN rank_points INT NOT NULL DEFAULT 1000");
            st.execute("ALTER TABLE users ADD COLUMN monument_points INT NOT NULL DEFAULT 0");

            // ── NOWE MIGRACJE SYSTEMU OCENIANIA (dla istniejących instalacji) ──
            // Ignorujemy błędy, jeśli kolumny już istnieją (SQLState 42S21)
            try { st.execute("ALTER TABLE guilds ADD COLUMN admin_rating_sum INT NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE guilds ADD COLUMN rated_guild_tag VARCHAR(10) NULL"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE guilds ADD COLUMN player_votes JSON NULL"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE guilds ADD COLUMN received_player_rating_sum INT NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE guilds ADD COLUMN received_player_rating_count INT NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE guilds ADD COLUMN received_player_ratings JSON NULL"); } catch (Exception ignored) {}
            // ─────────────────────────────────────────────────────────────────────

        } catch (Exception e) {
            // Logujemy tylko krytyczne błędy, ignorujemy te o "Duplicate column name"
            if (!e.getMessage().contains("Duplicate column name")) {
                throw e;
            }
        }
    }

    public Connection getConnection() throws SQLException {
        if (mysqlService == null || !mysqlService.isEnabled()) {
            throw new SQLException("Database not initialized");
        }
        try {
            return mysqlService.getConnection();
        } catch (Exception e) {
            throw new SQLException("Failed to get connection", e);
        }
    }

    public void close() {
        // MySQLService is managed by SectorSystemPlugin, not here
    }
}