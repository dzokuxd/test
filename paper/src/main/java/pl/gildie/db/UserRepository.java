package pl.gildie.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class UserRepository {
    private final Database db;

    public UserRepository(Database db) { this.db = db; }

    public void upsertIdentity(UUID uuid, String name) {
        String sql = "INSERT INTO users (uuid, name, updated_at) VALUES (?,?,?)"
                + " ON DUPLICATE KEY UPDATE name=VALUES(name), updated_at=VALUES(updated_at)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) { }
    }

    public void setGuild(UUID uuid, String tag, String role) {
        String sql = "UPDATE users SET guild_tag=?, role=?, joined_at=?, updated_at=? WHERE uuid=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tag);
            ps.setString(2, role);
            ps.setLong(3, System.currentTimeMillis());
            ps.setLong(4, System.currentTimeMillis());
            ps.setString(5, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException ignored) { }
    }

    public void clearGuild(UUID uuid) {
        String sql = "UPDATE users SET guild_tag=NULL, role=NULL, updated_at=? WHERE uuid=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException ignored) { }
    }

    public String[] fetchGuildRole(UUID uuid) {
        String sql = "SELECT guild_tag, role FROM users WHERE uuid=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new String[]{ rs.getString(1), rs.getString(2) };
            }
        } catch (SQLException ignored) { }
        return null;
    }
}
