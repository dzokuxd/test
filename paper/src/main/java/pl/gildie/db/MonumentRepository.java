package pl.gildie.db;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MonumentRepository {
    private static final Gson gson = new Gson();
    private final Database db;

    public MonumentRepository(Database db) {
        this.db = db;
        createTables();
    }

    private void createTables() {
        try (Connection conn = db.getConnection(); Statement st = conn.createStatement()) {
            // JEDNA tabela: pozycje + stan + respawny + hity korony (hits_json, tylko id=0)
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS monuments (" +
                            "  id INT PRIMARY KEY," +
                            "  world VARCHAR(64) NOT NULL," +
                            "  x DOUBLE NOT NULL," +
                            "  y DOUBLE NOT NULL," +
                            "  z DOUBLE NOT NULL," +
                            "  type VARCHAR(10) NOT NULL," +
                            "  active BOOLEAN NOT NULL DEFAULT FALSE," +
                            "  respawn_at BIGINT NOT NULL DEFAULT 0," +
                            "  last_spawn_date VARCHAR(10) NULL," +
                            "  captured_date VARCHAR(10) NULL," +
                            "  hits_json TEXT NULL" +
                            ")"
            );
            st.executeUpdate("INSERT IGNORE INTO monuments (id, world, x, y, z, type) VALUES (0,'world',0,0,0,'CENTER')");
            for (int i = 1; i <= 4; i++)
                st.executeUpdate("INSERT IGNORE INTO monuments (id, world, x, y, z, type) VALUES (" + i + ",'world',0,0,0,'CORNER')");

            migrateAndDropOld(st);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Jednorazowa migracja starych danych + sprzątanie
    private void migrateAndDropOld(Statement st) {
        try { st.executeUpdate("UPDATE monuments m JOIN monument_crystals c ON c.id = m.id SET m.world=c.world, m.x=c.x, m.y=c.y, m.z=c.z, m.type=c.type"); } catch (Exception ignored) { }
        try { st.executeUpdate("UPDATE monuments m JOIN monument_center_state s ON s.id = 1 SET m.active=s.active, m.respawn_at=s.next_respawn_at, m.last_spawn_date=s.last_spawn_date, m.captured_date=s.captured_date WHERE m.id=0"); } catch (Exception ignored) { }
        try { st.executeUpdate("UPDATE monuments m JOIN monument_corner_respawn r ON r.id = m.id SET m.respawn_at=r.respawn_at WHERE m.id BETWEEN 1 AND 4"); } catch (Exception ignored) { }
        // punkty narożne -> users.monument_points
        try { st.executeUpdate("UPDATE users u JOIN monument_corner_points p ON p.player_uuid = u.uuid SET u.monument_points = u.monument_points + p.points"); } catch (Exception ignored) { }

        // ── NOWE: migracja efektów ze starej tabeli monument_effects do guilds.monument_effects ──
        try {
            st.executeUpdate("UPDATE guilds g JOIN monument_effects m ON m.guild_tag = g.tag SET g.monument_effects = m.effects_json");
            st.executeUpdate("DROP TABLE IF EXISTS monument_effects");
        } catch (Exception ignored) { }

        // stare hity korony -> hits_json w monuments
        try {
            Map<String, Hit> hits = loadCenterHits();
            boolean merged = false;
            try (ResultSet rs = st.executeQuery("SELECT player_uuid, player_name, hits FROM monument_center_hits")) {
                while (rs.next()) {
                    Hit h = hits.get(rs.getString(1));
                    int add = rs.getInt("hits");
                    hits.put(rs.getString(1), new Hit(rs.getString(1), rs.getString(2), (h == null ? 0 : h.hits) + add));
                    merged = true;
                }
            } catch (Exception ignored) { }
            if (!merged) {
                try (ResultSet rs = st.executeQuery("SELECT uuid, name, hits FROM monument_center_hits")) {
                    while (rs.next()) {
                        Hit h = hits.get(rs.getString(1));
                        int add = rs.getInt("hits");
                        hits.put(rs.getString(1), new Hit(rs.getString(1), rs.getString(2), (h == null ? 0 : h.hits) + add));
                    }
                } catch (Exception ignored) { }
            }
            if (!hits.isEmpty()) saveCenterHits(hits);
        } catch (Exception ignored) { }

        // sprzątanie śmieci (jeśli została z poprzedniej poprawki - usuwamy)
        try { st.executeUpdate("ALTER TABLE users DROP COLUMN center_hits"); } catch (Exception ignored) { }
        try {
            st.executeUpdate("DROP TABLE IF EXISTS monument_crystals");
            st.executeUpdate("DROP TABLE IF EXISTS monument_center_state");
            st.executeUpdate("DROP TABLE IF EXISTS monument_corner_respawn");
            st.executeUpdate("DROP TABLE IF EXISTS monument_corner_points");
            st.executeUpdate("DROP TABLE IF EXISTS monument_center_hits");
        } catch (Exception ignored) { }
    }

    // ── Kryształy (pozycje + stan) ────────────────────────────────────────
    public List<Crystal> loadCrystals() {
        List<Crystal> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM monuments")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Crystal(rs.getInt("id"), rs.getString("world"),
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getString("type"), rs.getBoolean("active"),
                        rs.getLong("respawn_at"), rs.getString("last_spawn_date"),
                        rs.getString("captured_date")));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public void saveCrystal(int id, String world, double x, double y, double z, String type) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO monuments (id, world, x, y, z, type) VALUES (?,?,?,?,?,?) " +
                             "ON DUPLICATE KEY UPDATE world=?, x=?, y=?, z=?, type=?")) {
            ps.setInt(1, id); ps.setString(2, world); ps.setDouble(3, x); ps.setDouble(4, y); ps.setDouble(5, z); ps.setString(6, type);
            ps.setString(7, world); ps.setDouble(8, x); ps.setDouble(9, y); ps.setDouble(10, z); ps.setString(11, type);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void set(int id, String column, String value) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE monuments SET " + column + "=? WHERE id=?")) {
            if (value == null) ps.setNull(1, java.sql.Types.VARCHAR); else ps.setString(1, value);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void set(int id, String column, long value) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE monuments SET " + column + "=? WHERE id=?")) {
            ps.setLong(1, value); ps.setInt(2, id);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void setBool(int id, String column, boolean value) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE monuments SET " + column + "=? WHERE id=?")) {
            ps.setBoolean(1, value); ps.setInt(2, id);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void setActive(int id, boolean active)     { setBool(id, "active", active); }
    public void setRespawnAt(int id, long t)          { set(id, "respawn_at", t); }
    public void setLastSpawnDate(int id, String date) { set(id, "last_spawn_date", date); }
    public void setCapturedDate(int id, String date)  { set(id, "captured_date", date); }

    public CenterState getCenterState() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM monuments WHERE id=0")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new CenterState(rs.getBoolean("active"), rs.getString("last_spawn_date"), rs.getLong("respawn_at"), rs.getString("captured_date"));
        } catch (Exception e) { e.printStackTrace(); }
        return new CenterState(false, null, 0L, null);
    }

    // ── Hity korony (TOP5) -> monuments.hits_json (wiersz id=0) ───────────
    public Map<String, Hit> loadCenterHits() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT hits_json FROM monuments WHERE id=0")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String json = rs.getString("hits_json");
                if (json != null && !json.isBlank()) {
                    Map<String, Hit> m = gson.fromJson(json, new TypeToken<LinkedHashMap<String, Hit>>(){}.getType());
                    if (m != null) return m;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return new LinkedHashMap<>();
    }

    private void saveCenterHits(Map<String, Hit> map) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE monuments SET hits_json=? WHERE id=0")) {
            ps.setString(1, gson.toJson(map));
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void addCenterHit(String playerUuid, String playerName) {
        Map<String, Hit> map = loadCenterHits();
        Hit h = map.get(playerUuid);
        map.put(playerUuid, new Hit(playerUuid, playerName, (h == null ? 0 : h.hits) + 1));
        saveCenterHits(map);
    }

    public List<Hit> getTopCenterHits(int limit) {
        List<Hit> list = new ArrayList<>(loadCenterHits().values());
        list.sort((a, b) -> Integer.compare(b.hits, a.hits));
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    public void resetCenterHits() {
        saveCenterHits(new LinkedHashMap<>());
    }

    // ── Punkty monumentu -> users.monument_points (1 narożny / 3 środek) ──
    public void addMonumentPoints(String uuid, String name, int delta) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE users SET monument_points = GREATEST(0, monument_points + ?), updated_at=? WHERE uuid=?")) {
            ps.setInt(1, delta); ps.setLong(2, System.currentTimeMillis()); ps.setString(3, uuid);
            if (ps.executeUpdate() == 0) {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO users (uuid, name, monument_points, updated_at) VALUES (?,?,GREATEST(0,?),?)")) {
                    ins.setString(1, uuid); ins.setString(2, name); ins.setInt(3, delta); ins.setLong(4, System.currentTimeMillis());
                    ins.executeUpdate();
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public int getMonumentPoints(String uuid) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT monument_points FROM users WHERE uuid=?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    public List<Hit> getTopMonumentPoints(int limit) {
        List<Hit> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT uuid, name, monument_points FROM users WHERE monument_points > 0 ORDER BY monument_points DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new Hit(rs.getString("uuid"), rs.getString("name"), rs.getInt("monument_points")));
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // ── Efekty monumentu -> kolumna guilds.monument_effects ───────────────
    public void saveMonumentEffects(String guildTag, String json) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE guilds SET monument_effects=? WHERE tag=?")) {
            ps.setString(1, json);
            ps.setString(2, guildTag.toUpperCase());
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public String loadMonumentEffects(String guildTag) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT monument_effects FROM guilds WHERE tag=?")) {
            ps.setString(1, guildTag.toUpperCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public void deleteMonumentEffects(String guildTag) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE guilds SET monument_effects=NULL WHERE tag=?")) {
            ps.setString(1, guildTag.toUpperCase());
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Klasy danych ───────────────────────────────────────────────────────
    public static class Crystal {
        public final int id;
        public final String world;
        public final double x, y, z;
        public final String type;
        public final boolean active;
        public final long respawnAt;
        public final String lastSpawnDate;
        public final String capturedDate;

        public Crystal(int id, String world, double x, double y, double z, String type,
                       boolean active, long respawnAt, String lastSpawnDate, String capturedDate) {
            this.id = id; this.world = world; this.x = x; this.y = y; this.z = z; this.type = type;
            this.active = active; this.respawnAt = respawnAt; this.lastSpawnDate = lastSpawnDate; this.capturedDate = capturedDate;
        }
    }

    public static class CenterState {
        public final boolean active;
        public final String lastSpawnDate;
        public final long nextRespawnAt;
        public final String capturedDate;

        public CenterState(boolean active, String lastSpawnDate, long nextRespawnAt, String capturedDate) {
            this.active = active; this.lastSpawnDate = lastSpawnDate; this.nextRespawnAt = nextRespawnAt; this.capturedDate = capturedDate;
        }
    }

    public static class Hit {
        public String uuid;
        public String name;
        public int hits;

        public Hit() { }

        public Hit(String uuid, String name, int hits) {
            this.uuid = uuid; this.name = name; this.hits = hits;
        }
    }
}