package pl.gildie.db;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import pl.gildie.model.Guild;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuildRepository {

    private static final Gson gson = new Gson();
    private final Database db;

    public GuildRepository(Database db) {
        this.db = db;
    }

    // ═══════════════════════ LOAD ═══════════════════════

    public List<Guild> loadAll() {
        List<Guild> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM guilds");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(fromRow(rs));
        } catch (SQLException e) {
            throw new IllegalStateException("guilds load failed", e);
        }
        return out;
    }

    private Guild fromRow(ResultSet rs) throws SQLException {
        String tag = rs.getString("tag");
        UUID owner = UUID.fromString(rs.getString("owner_uuid"));

        JsonObject center = obj(rs.getString("center"));
        if (center == null) {
            throw new SQLException("guild " + tag + " ma NULL w kolumnie center");
        }
        Guild g = new Guild(tag, owner,
                JsonLoc.world(center), JsonLoc.x(center), JsonLoc.y(center), JsonLoc.z(center),
                rs.getInt("radius"));

        for (var e : arr(rs.getString("members")))  g.addMember(UUID.fromString(e.getAsString()));
        for (var e : arr(rs.getString("deputies"))) g.addDeputy(UUID.fromString(e.getAsString()));

        List<String> alliesList = new ArrayList<>();
        for (var e : arr(rs.getString("allies"))) alliesList.add(e.getAsString());
        g.loadAllies(alliesList);

        String home = rs.getString("home");
        if (home != null) {
            JsonObject h = obj(home);
            if (h != null) g.loadHome(JsonLoc.world(h), JsonLoc.x(h), JsonLoc.y(h), JsonLoc.z(h));
        }

        String raid = rs.getString("raid_base");
        if (raid != null) {
            JsonObject r = obj(raid);
            if (r != null) {
                g.loadRaidBase(JsonLoc.world(r), JsonLoc.x(r), JsonLoc.y(r), JsonLoc.z(r),
                        rs.getLong("raid_base_exp"), null);
            }
        }

        // ── EGG: tylko HP + flaga, pozycja = center (stare JSON-y z x/y/z połknie bez błędu) ──
        g.deserializeEgg(rs.getString("egg"));

        g.setRankPoints(rs.getInt("rank_points"));

        // ── RATING SYSTEM: dwie kolumny INT, zgodne z upsert() ──
        g.setAdminRatingSum(rs.getInt("admin_rating_sum"));
        g.setRatedGuildTag(rs.getString("rated_guild_tag"));
        g.deserializePlayerVotes(rs.getString("player_votes"));
        g.setReceivedPlayerRatingSum(rs.getInt("received_player_rating_sum"));
        g.setReceivedPlayerRatingCount(rs.getInt("received_player_rating_count"));

        return g;
    }

    // ═══════════════════════ UPSERT ═══════════════════════

    public void upsert(Guild g) {
        String sql = "INSERT INTO guilds (tag, owner_uuid, deputies, members, allies, center, radius,"
                + " home, raid_base, raid_base_exp, egg, rank_points, admin_rating_sum, rated_guild_tag,"
                + " player_votes, received_player_rating_sum, received_player_rating_count,"
                + " created_at, updated_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE"
                + " owner_uuid=VALUES(owner_uuid), deputies=VALUES(deputies), members=VALUES(members),"
                + " allies=VALUES(allies), center=VALUES(center), radius=VALUES(radius),"
                + " home=VALUES(home), raid_base=VALUES(raid_base), raid_base_exp=VALUES(raid_base_exp),"
                + " egg=VALUES(egg), rank_points=VALUES(rank_points),"
                + " admin_rating_sum=VALUES(admin_rating_sum), rated_guild_tag=VALUES(rated_guild_tag),"
                + " player_votes=VALUES(player_votes),"
                + " received_player_rating_sum=VALUES(received_player_rating_sum),"
                + " received_player_rating_count=VALUES(received_player_rating_count),"
                + " updated_at=VALUES(updated_at)";

        JsonObject center = JsonLoc.of(g.getWorldName(), g.getX(), g.getY(), g.getZ());
        JsonArray members = new JsonArray();  g.getMembers().forEach(u -> members.add(u.toString()));
        JsonArray deputies = new JsonArray(); g.getDeputies().forEach(u -> deputies.add(u.toString()));
        JsonArray allies = new JsonArray();   g.getAllies().forEach(allies::add);

        JsonObject home = g.hasHome()
                ? JsonLoc.of(g.getHomeWorld(), g.getHomeX(), g.getHomeY(), g.getHomeZ())
                : null;
        JsonObject raid = g.getRaidWorld() != null
                ? JsonLoc.of(g.getRaidWorld(), g.getRaidX(), g.getRaidY(), g.getRaidZ())
                : null;

        long now = System.currentTimeMillis();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,  g.getTag());
            ps.setString(2,  g.getOwner().toString());
            ps.setString(3,  gson.toJson(deputies));
            ps.setString(4,  gson.toJson(members));
            ps.setString(5,  gson.toJson(allies));
            ps.setString(6,  gson.toJson(center));
            ps.setInt(7,     g.getRadius());
            ps.setString(8,  home != null ? gson.toJson(home) : null);
            ps.setString(9,  raid != null ? gson.toJson(raid) : null);
            ps.setLong(10,   Math.max(0, g.getRaidExpiresAt()));
            ps.setString(11, g.serializeEgg());          // zawsze non-null: {"hp":..,"maxHp":..,"alive":..}
            ps.setInt(12,    g.getRankPoints());
            ps.setInt(13,    g.getAdminRatingSum());
            ps.setString(14, g.getRatedGuildTag());
            ps.setString(15, g.serializePlayerVotes());
            ps.setInt(16,    g.getReceivedPlayerRatingSum());
            ps.setInt(17,    g.getReceivedPlayerRatingCount());
            ps.setLong(18,   now);
            ps.setLong(19,   now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("guilds upsert failed: " + g.getTag(), e);
        }
    }

    // ═══════════════════════ DELETE ═══════════════════════

    public void delete(String tag) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM guilds WHERE tag=?")) {
            ps.setString(1, tag);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("guilds delete failed: " + tag, e);
        }
    }

    // ═══════════════════════ REGEN ═══════════════════════

    public String loadRegenJson(String tag) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT regen_blocks FROM guilds WHERE tag=?")) {
            ps.setString(1, tag);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString(1); }
        } catch (SQLException e) { throw new IllegalStateException("regen load failed", e); }
        return null;
    }

    public void saveRegenJson(String tag, String json) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE guilds SET regen_blocks=? WHERE tag=?")) {
            ps.setString(1, json); ps.setString(2, tag);
            ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("regen save failed", e); }
    }

    public Map<String, String> loadAllRegen() {
        Map<String, String> out = new HashMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT tag, regen_blocks FROM guilds");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getString(1), rs.getString(2));
        } catch (SQLException e) { throw new IllegalStateException("regen loadAll failed", e); }
        return out;
    }

    // ═══════════════════════ WARS ═══════════════════════

    public String loadWarsJson(String tag) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT wars FROM guilds WHERE tag=?")) {
            ps.setString(1, tag);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString(1); }
        } catch (SQLException e) { throw new IllegalStateException("wars load failed", e); }
        return null;
    }

    public void saveWarsJson(String tag, String json) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE guilds SET wars=? WHERE tag=?")) {
            ps.setString(1, json); ps.setString(2, tag);
            ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("wars save failed", e); }
    }

    public Map<String, String> loadAllWars() {
        Map<String, String> out = new HashMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT tag, wars FROM guilds");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getString(1), rs.getString(2));
        } catch (SQLException e) { throw new IllegalStateException("wars loadAll failed", e); }
        return out;
    }

    // ═══════════════════════ DISPENSERS ═══════════════════════

    public void saveDispenserClaim(String world, int x, int y, int z, String ownerTag) {
        String sql = "INSERT INTO placed_dispensers (world, x, y, z, owner_tag) VALUES (?,?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE owner_tag=VALUES(owner_tag)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, world); ps.setInt(2, x); ps.setInt(3, y); ps.setInt(4, z); ps.setString(5, ownerTag);
            ps.executeUpdate();
        } catch (SQLException ignored) { }
    }

    public String getDispenserClaim(String world, int x, int y, int z) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT owner_tag FROM placed_dispensers WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, world); ps.setInt(2, x); ps.setInt(3, y); ps.setInt(4, z);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString(1); }
        } catch (SQLException ignored) { }
        return null;
    }

    public void removeDispenserClaim(String world, int x, int y, int z) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM placed_dispensers WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, world); ps.setInt(2, x); ps.setInt(3, y); ps.setInt(4, z);
            ps.executeUpdate();
        } catch (SQLException ignored) { }
    }

    // ═══════════════════════ HELPY JSON (odporne na NULL) ═══════════════════════

    private static JsonObject obj(String json) {
        if (json == null || json.isBlank()) return null;
        try { return gson.fromJson(json, JsonObject.class); }
        catch (Exception e) { return null; }
    }

    private static JsonArray arr(String json) {
        if (json == null || json.isBlank()) return new JsonArray();
        try {
            JsonArray a = gson.fromJson(json, JsonArray.class);
            return a != null ? a : new JsonArray();
        } catch (Exception e) { return new JsonArray(); }
    }
}