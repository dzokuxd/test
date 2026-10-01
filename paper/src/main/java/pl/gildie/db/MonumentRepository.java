package pl.gildie.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class MonumentRepository {
    private final Database db;

    public MonumentRepository(Database db) {
        this.db = db;
        createTables();
    }

    private void createTables() {
        try (Connection conn = db.getConnection()) {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS monument_crystals (" +
                            "  id INT PRIMARY KEY," +
                            "  world VARCHAR(64) NOT NULL," +
                            "  x DOUBLE NOT NULL," +
                            "  y DOUBLE NOT NULL," +
                            "  z DOUBLE NOT NULL," +
                            "  type VARCHAR(16) NOT NULL" +
                            ")"
            );
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS monument_center_state (" +
                            "  id INT PRIMARY KEY DEFAULT 1," +
                            "  active BOOLEAN DEFAULT FALSE," +
                            "  last_spawn_date VARCHAR(16) DEFAULT NULL," +
                            "  next_respawn_at BIGINT DEFAULT 0," +
                            "  captured_date VARCHAR(16) DEFAULT NULL" +
                            ")"
            );
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS monument_corner_respawn (" +
                            "  id INT PRIMARY KEY," +
                            "  respawn_at BIGINT DEFAULT 0" +
                            ")"
            );

            // Tworzenie tabeli z PRIMARY KEY
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS monument_center_hits (" +
                            "  player_uuid VARCHAR(36) NOT NULL," +
                            "  player_name VARCHAR(16) NOT NULL," +
                            "  hits INT DEFAULT 0," +
                            "  PRIMARY KEY (player_uuid)" +
                            ")"
            );

            // 🛠️ NAPRAWA STRUKTURY TABELI
            dropColumnIfExists(conn, "monument_center_hits", "uuid");
            dropColumnIfExists(conn, "monument_center_hits", "name");
            dropColumnIfExists(conn, "monument_corner_points", "uuid");
            dropColumnIfExists(conn, "monument_corner_points", "name");

            ensureColumnExists(conn, "monument_center_hits", "player_uuid", "VARCHAR(36) NOT NULL DEFAULT ''");
            ensureColumnExists(conn, "monument_center_hits", "player_name", "VARCHAR(16) NOT NULL DEFAULT ''");
            ensureColumnExists(conn, "monument_center_hits", "hits", "INT DEFAULT 0");

            // 🔥 KLUCZOWE: Ustaw player_uuid jako PRIMARY KEY (naprawia istniejące tabele)
            try {
                conn.createStatement().executeUpdate(
                        "ALTER TABLE monument_center_hits DROP PRIMARY KEY, ADD PRIMARY KEY (player_uuid)"
                );
            } catch (Exception e) {
                // Może już mieć PRIMARY KEY - ignoruj
            }

            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS monument_corner_points (" +
                            "  player_uuid VARCHAR(36) NOT NULL," +
                            "  player_name VARCHAR(16) NOT NULL," +
                            "  points INT DEFAULT 0," +
                            "  PRIMARY KEY (player_uuid)" +
                            ")"
            );

            // Również dla corner_points
            try {
                conn.createStatement().executeUpdate(
                        "ALTER TABLE monument_corner_points DROP PRIMARY KEY, ADD PRIMARY KEY (player_uuid)"
                );
            } catch (Exception e) {
                // Może już mieć PRIMARY KEY - ignoruj
            }

            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS monument_effects (" +
                            "  guild_tag VARCHAR(16) PRIMARY KEY," +
                            "  effects_json TEXT NOT NULL" +
                            ")"
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void ensureColumnExists(Connection conn, String tableName, String columnName, String definition) {
        try {
            conn.createStatement().executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        } catch (Exception e) {
            // Ignoruj - kolumna prawdopodobnie już istnieje
        }
    }

    private void dropColumnIfExists(Connection conn, String tableName, String columnName) {
        try {
            conn.createStatement().executeUpdate("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
        } catch (Exception e) {
            // Ignoruj - kolumna prawdopodobnie nie istnieje
        }
    }

    // ── Crystal locations ──────────────────────────────────────────────────
    public List<Crystal> loadCrystals() {
        List<Crystal> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM monument_crystals")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Crystal(
                        rs.getInt("id"),
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getString("type")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void saveCrystal(int id, String world, double x, double y, double z, String type) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO monument_crystals (id, world, x, y, z, type) VALUES (?, ?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE world=?, x=?, y=?, z=?, type=?")) {
            ps.setInt(1, id);
            ps.setString(2, world);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.setString(6, type);
            ps.setString(7, world);
            ps.setDouble(8, x);
            ps.setDouble(9, y);
            ps.setDouble(10, z);
            ps.setString(11, type);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Center state ───────────────────────────────────────────────────────
    public CenterState getCenterState() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM monument_center_state WHERE id = 1")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new CenterState(
                        rs.getBoolean("active"),
                        rs.getString("last_spawn_date"),
                        rs.getLong("next_respawn_at"),
                        rs.getString("captured_date")
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new CenterState(false, null, 0L, null);
    }

    public void setCenterActive(boolean active) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO monument_center_state (id, active) VALUES (1, ?) " +
                             "ON DUPLICATE KEY UPDATE active=?")) {
            ps.setBoolean(1, active);
            ps.setBoolean(2, active);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setCenterLastSpawnDate(String date) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO monument_center_state (id, last_spawn_date) VALUES (1, ?) " +
                             "ON DUPLICATE KEY UPDATE last_spawn_date=?")) {
            ps.setString(1, date);
            ps.setString(2, date);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setCenterNextRespawnAt(long time) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO monument_center_state (id, next_respawn_at) VALUES (1, ?) " +
                             "ON DUPLICATE KEY UPDATE next_respawn_at=?")) {
            ps.setLong(1, time);
            ps.setLong(2, time);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setCenterCapturedDate(String date) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO monument_center_state (id, captured_date) VALUES (1, ?) " +
                             "ON DUPLICATE KEY UPDATE captured_date=?")) {
            ps.setString(1, date);
            ps.setString(2, date);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Corner respawn ─────────────────────────────────────────────────────
    public void setCornerRespawnAt(int id, long time) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO monument_corner_respawn (id, respawn_at) VALUES (?, ?) " +
                             "ON DUPLICATE KEY UPDATE respawn_at=?")) {
            ps.setInt(1, id);
            ps.setLong(2, time);
            ps.setLong(3, time);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─ Center hits (TOP5) ─────────────────────────────────────────────────
    public void addCenterHit(String playerUuid, String playerName) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO monument_center_hits (player_uuid, player_name, hits) VALUES (?, ?, 1) " +
                             "ON DUPLICATE KEY UPDATE hits = hits + 1")) {
            ps.setString(1, playerUuid);
            ps.setString(2, playerName);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Hit> getTopCenterHits(int limit) {
        List<Hit> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT player_uuid, player_name, hits FROM monument_center_hits ORDER BY hits DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Hit(rs.getString("player_uuid"), rs.getString("player_name"), rs.getInt("hits")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void resetCenterHits() {
        try (Connection conn = db.getConnection()) {
            conn.createStatement().executeUpdate("DELETE FROM monument_center_hits");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─ Corner points ──────────────────────────────────────────────────────
    public void addCornerPoints(String playerUuid, String playerName, int points) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO monument_corner_points (player_uuid, player_name, points) VALUES (?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE points = points + ?")) {
            ps.setString(1, playerUuid);
            ps.setString(2, playerName);
            ps.setInt(3, points);
            ps.setInt(4, points);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void resetCornerPoints() {
        try (Connection conn = db.getConnection()) {
            conn.createStatement().executeUpdate("DELETE FROM monument_corner_points");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Monument points (PointsManager) ────────────────────────────────────
    public int getMonumentPoints(String playerUuid) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT points FROM monument_corner_points WHERE player_uuid=?")) {
            ps.setString(1, playerUuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("points");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void setMonumentPoints(String playerUuid, String playerName, int points) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO monument_corner_points (player_uuid, player_name, points) VALUES (?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE points=?")) {
            ps.setString(1, playerUuid);
            ps.setString(2, playerName != null ? playerName : "Gracz");
            ps.setInt(3, points);
            ps.setInt(4, points);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─ Monument effects (bonusy z korony i narożnych) ─────────────────────
    public void saveMonumentEffects(String guildTag, String json) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO monument_effects (guild_tag, effects_json) VALUES (?, ?) " +
                             "ON DUPLICATE KEY UPDATE effects_json=?")) {
            ps.setString(1, guildTag.toUpperCase());
            ps.setString(2, json);
            ps.setString(3, json);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String loadMonumentEffects(String guildTag) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT effects_json FROM monument_effects WHERE guild_tag=?")) {
            ps.setString(1, guildTag.toUpperCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("effects_json");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteMonumentEffects(String guildTag) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM monument_effects WHERE guild_tag=?")) {
            ps.setString(1, guildTag.toUpperCase());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Data classes ───────────────────────────────────────────────────────
    public static class Crystal {
        public final int id;
        public final String world;
        public final double x, y, z;
        public final String type;

        public Crystal(int id, String world, double x, double y, double z, String type) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
        }
    }

    public static class CenterState {
        public final boolean active;
        public final String lastSpawnDate;
        public final long nextRespawnAt;
        public final String capturedDate;

        public CenterState(boolean active, String lastSpawnDate, long nextRespawnAt, String capturedDate) {
            this.active = active;
            this.lastSpawnDate = lastSpawnDate;
            this.nextRespawnAt = nextRespawnAt;
            this.capturedDate = capturedDate;
        }
    }

    public static class Hit {
        public final String uuid;
        public final String name;
        public final int hits;

        public Hit(String uuid, String name, int hits) {
            this.uuid = uuid;
            this.name = name;
            this.hits = hits;
        }
    }
}