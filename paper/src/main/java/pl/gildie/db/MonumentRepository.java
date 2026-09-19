package pl.gildie.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MonumentRepository {
    private final Database db;

    public MonumentRepository(Database db) { this.db = db; }

    public static class CenterState {
        public boolean active;
        public String lastSpawnDate = "";
        public String capturedDate = "";
        public long nextRespawnAt;
    }

    public static class Crystal {
        public int id;
        public String world;
        public double x, y, z;
        public String type;
    }

    public static class Hit {
        public String uuid;
        public String name;
        public int hits;
    }

    public CenterState getCenterState() {
        CenterState s = new CenterState();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT active, last_spawn_date, captured_date, next_respawn_at FROM monument_crystals WHERE id=0")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    s.active = rs.getBoolean("active");
                    s.lastSpawnDate = rs.getString("last_spawn_date");
                    if (s.lastSpawnDate == null) s.lastSpawnDate = "";
                    s.capturedDate = rs.getString("captured_date");
                    if (s.capturedDate == null) s.capturedDate = "";
                    s.nextRespawnAt = rs.getLong("next_respawn_at");
                }
            }
        } catch (SQLException ignored) { }
        return s;
    }

    public void setCenterActive(boolean a) {
        exec("UPDATE monument_crystals SET active=? WHERE id=0", ps -> ps.setBoolean(1, a));
    }

    public void setCenterLastSpawnDate(String d) {
        exec("UPDATE monument_crystals SET last_spawn_date=? WHERE id=0", ps -> ps.setString(1, d));
    }

    public void setCenterCapturedDate(String d) {
        exec("UPDATE monument_crystals SET captured_date=? WHERE id=0", ps -> ps.setString(1, d));
    }

    public void setCenterNextRespawnAt(long t) {
        exec("UPDATE monument_crystals SET next_respawn_at=? WHERE id=0", ps -> ps.setLong(1, t));
    }

    public long getCornerRespawnAt(int id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT next_respawn_at FROM monument_crystals WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException ignored) { }
        return 0L;
    }

    public void setCornerRespawnAt(int id, long t) {
        exec("UPDATE monument_crystals SET next_respawn_at=? WHERE id=?", ps -> { ps.setLong(1, t); ps.setInt(2, id); });
    }

    public void saveCrystal(int id, String world, double x, double y, double z, String type) {
        String sql = "INSERT INTO monument_crystals (id, world, x, y, z, type) VALUES (?,?,?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE world=?, x=?, y=?, z=?, type=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id); ps.setString(2, world); ps.setDouble(3, x); ps.setDouble(4, y); ps.setDouble(5, z); ps.setString(6, type);
            ps.setString(7, world); ps.setDouble(8, x); ps.setDouble(9, y); ps.setDouble(10, z); ps.setString(11, type);
            ps.executeUpdate();
        } catch (SQLException ignored) { }
    }

    public List<Crystal> loadCrystals() {
        List<Crystal> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM monument_crystals");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Crystal cr = new Crystal();
                cr.id = rs.getInt("id");
                cr.world = rs.getString("world");
                cr.x = rs.getDouble("x");
                cr.y = rs.getDouble("y");
                cr.z = rs.getDouble("z");
                cr.type = rs.getString("type");
                list.add(cr);
            }
        } catch (SQLException ignored) { }
        return list;
    }

    public String loadMonumentEffects(String tag) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT monument_effects FROM guilds WHERE tag=?")) {
            ps.setString(1, tag);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException ignored) { }
        return null;
    }

    public void saveMonumentEffects(String tag, String json) {
        exec("UPDATE guilds SET monument_effects=? WHERE tag=?", ps -> { ps.setString(1, json); ps.setString(2, tag); });
    }

    public int getMonumentPoints(String uuid) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT monument_points FROM users WHERE uuid=?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ignored) { }
        return 0;
    }

    public void setMonumentPoints(String uuid, int points) {
        exec("UPDATE users SET monument_points=? WHERE uuid=?", ps -> { ps.setInt(1, points); ps.setString(2, uuid); });
    }

    // ── Hity w korone ──────────────────────────────────────────────────────
    public void addCenterHit(String uuid, String name) {
        String sql = "INSERT INTO monument_center_hits (uuid, name, hits) VALUES (?,?,1)"
                + " ON DUPLICATE KEY UPDATE hits = hits + 1, name = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.setString(3, name);
            ps.executeUpdate();
        } catch (SQLException ignored) { }
    }

    public List<Hit> getTopCenterHits(int limit) {
        List<Hit> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, name, hits FROM monument_center_hits ORDER BY hits DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Hit h = new Hit();
                    h.uuid = rs.getString("uuid");
                    h.name = rs.getString("name");
                    h.hits = rs.getInt("hits");
                    list.add(h);
                }
            }
        } catch (SQLException ignored) { }
        return list;
    }

    public void resetCenterHits() {
        exec("DELETE FROM monument_center_hits", ps -> { });
    }

    private interface Prep { void apply(PreparedStatement ps) throws SQLException; }

    private void exec(String sql, Prep prep) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            prep.apply(ps);
            ps.executeUpdate();
        } catch (SQLException ignored) { }
    }
}
