package pl.discord;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;

public class DiscordDatabase {
    private final SectorSystemPlugin plugin;
    private HikariDataSource dataSource;

    public DiscordDatabase(SectorSystemPlugin plugin) {
        this.plugin = plugin;
        init();
        createTables();
    }

    private void init() {
        HikariConfig config = new HikariConfig();
        FileConfiguration cfg = plugin.getConfig();
        
        // Używamy tej samej konfiguracji MySQL co reszta SectorSystem, lub SQLite jako fallback
        if (cfg.getBoolean("mysql.enabled", false)) {
            String host = cfg.getString("mysql.host", "127.0.0.1");
            int port = cfg.getInt("mysql.port", 3306);
            String database = cfg.getString("mysql.database", "sectorsystem");
            String username = cfg.getString("mysql.username", "root");
            String password = cfg.getString("mysql.password", "");
            
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(5);
        } else {
            String dbPath = new File(plugin.getDataFolder(), "discord.db").getAbsolutePath();
            config.setJdbcUrl("jdbc:sqlite:" + dbPath);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1);
        }

        config.setConnectionTimeout(30000);
        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("✓ Baza danych Discord zainicjalizowana");
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public Connection getConnection() throws Exception {
        return dataSource.getConnection();
    }

    private void createTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Używamy VARCHAR(36) zamiast TEXT dla PRIMARY KEY (wymóg MySQL)
            stmt.execute("CREATE TABLE IF NOT EXISTS discord_verifications (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "code VARCHAR(10), " +
                    "discord_id VARCHAR(30), " +
                    "expires_at BIGINT)");

            stmt.execute("CREATE TABLE IF NOT EXISTS discord_linked (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "discord_id VARCHAR(30) NOT NULL, " +
                    "mc_nick VARCHAR(50), " +
                    "linked_at BIGINT)");

            stmt.execute("CREATE TABLE IF NOT EXISTS discord_proposals (" +
                    "id VARCHAR(20) PRIMARY KEY, " +
                    "author_discord_id VARCHAR(30), " +
                    "author_mc_nick VARCHAR(50), " +
                    "content TEXT, " +
                    "status VARCHAR(20) DEFAULT 'Nowa', " +
                    "channel_id VARCHAR(30), " +
                    "message_id VARCHAR(30), " +
                    "created_at BIGINT)");

            stmt.execute("CREATE TABLE IF NOT EXISTS discord_proposal_votes (" +
                    "proposal_id VARCHAR(20), " +
                    "discord_user_id VARCHAR(30), " +
                    "vote_type VARCHAR(10), " +
                    "PRIMARY KEY (proposal_id, discord_user_id))");

            stmt.execute("CREATE TABLE IF NOT EXISTS discord_bans (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "discord_id VARCHAR(30), " +
                    "mc_nick VARCHAR(50), " +
                    "reason TEXT, " +
                    "banned_by VARCHAR(50), " +
                    "banned_at BIGINT, " +
                    "unbanned BOOLEAN DEFAULT FALSE)");

            stmt.execute("CREATE TABLE IF NOT EXISTS discord_ban_appeals (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "ban_uuid VARCHAR(36), " +
                    "discord_id VARCHAR(30), " +
                    "message TEXT, " +
                    "screenshot_url VARCHAR(255), " +
                    "submitted_at BIGINT, " +
                    "reviewed BOOLEAN DEFAULT FALSE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS discord_tickets (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "channel_id VARCHAR(30) NOT NULL, " +
                    "discord_id VARCHAR(30) NOT NULL, " +
                    "topic VARCHAR(100), " +
                    "description TEXT, " +
                    "created_at BIGINT, " +
                    "closed_at BIGINT, " +
                    "status VARCHAR(20) DEFAULT 'open')");
            plugin.getLogger().info("✓ Tabele Discord utworzone/sprawdzone");
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd tworzenia tabel Discord: " + e.getMessage());
        }
    }
}
