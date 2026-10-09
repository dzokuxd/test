package pl.dzoku.sectorsystem.discord;

import pl.sectorsystem.common.mysql.MySQLService;
import java.sql.Connection;
import java.sql.Statement;

public class DiscordDatabase {
    private final MySQLService mysql;

    public DiscordDatabase(MySQLService mysql) {
        this.mysql = mysql;
        createTables();
    }

    private void createTables() {
        if (!mysql.isEnabled()) return;
        try (Connection conn = mysql.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS discord_verifications (uuid VARCHAR(36) PRIMARY KEY, code VARCHAR(10), discord_id VARCHAR(30), expires_at BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS discord_linked (uuid VARCHAR(36) PRIMARY KEY, discord_id VARCHAR(30) NOT NULL, mc_nick VARCHAR(50), linked_at BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS discord_proposals (id VARCHAR(20) PRIMARY KEY, author_discord_id VARCHAR(30), author_mc_nick VARCHAR(50), content TEXT, status VARCHAR(20) DEFAULT 'Nowa', channel_id VARCHAR(30), message_id VARCHAR(30), created_at BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS discord_proposal_votes (proposal_id VARCHAR(20), discord_user_id VARCHAR(30), vote_type VARCHAR(10), PRIMARY KEY (proposal_id, discord_user_id))");
            stmt.execute("CREATE TABLE IF NOT EXISTS discord_bans (uuid VARCHAR(36) PRIMARY KEY, discord_id VARCHAR(30), mc_nick VARCHAR(50), reason TEXT, banned_by VARCHAR(50), banned_at BIGINT, expires_at BIGINT DEFAULT 0, unbanned BOOLEAN DEFAULT FALSE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS discord_ban_appeals (id INT AUTO_INCREMENT PRIMARY KEY, ban_uuid VARCHAR(36), discord_id VARCHAR(30), message TEXT, screenshot_url VARCHAR(255), submitted_at BIGINT, reviewed BOOLEAN DEFAULT FALSE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS discord_mutes (uuid VARCHAR(36) PRIMARY KEY, discord_id VARCHAR(30), mc_nick VARCHAR(50), reason TEXT, muted_by VARCHAR(50), muted_at BIGINT, expires_at BIGINT, unmuted BOOLEAN DEFAULT FALSE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS discord_tickets (id INT AUTO_INCREMENT PRIMARY KEY, channel_id VARCHAR(30) NOT NULL, discord_id VARCHAR(30) NOT NULL, topic VARCHAR(100), description TEXT, created_at BIGINT, closed_at BIGINT, status VARCHAR(20) DEFAULT 'open')");
            System.out.println("[Discord] Tabele bazy danych utworzone/sprawdzone");
        } catch (Exception e) {
            System.err.println("[Discord] Błąd tworzenia tabel: " + e.getMessage());
        }
    }

    public Connection getConnection() throws Exception {
        return mysql.getConnection();
    }
}