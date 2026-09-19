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

    public GuildRepository(Database db) { this.db = db; }

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
        JsonObject center = gson.fromJson(rs.getString("center"), JsonObject.class);
        Guild g = new Guild(tag, owner, JsonLoc.world(center), JsonLoc.x(center), JsonLoc.y(center), JsonLoc.z(center), rs.getInt("radius"));

        for (var e : gson.fromJson(rs.getString("members"), JsonArray.class)) g.addMember(UUID.fromString(e.getAsString()));
        for (var e : gson.fromJson(rs.getString("deputies"), JsonArray.class)) g.addDeputy(UUID.fromString(e.getAsString()));
        List<String> alliesList = new ArrayList<>();
        for (var e : gson.fromJson(rs.getString("allies"), JsonArray.class)) alliesList.add(e.getAsString());
        g.loadAllies(alliesList);

        String home = rs.getString("home");
        if (home != null) {
            JsonObject h = gson.fromJson(home, JsonObject.class);
            g.loadHome(JsonLoc.world(h), JsonLoc.x(h), JsonLoc.y(h), JsonLoc.z(h));
        }
        String raid = rs.getString("raid_base");
        if (raid != null) {
            JsonObject r = gson.fromJson(raid, JsonObject.class);
            String rw = rs.getString("raid_waypoint");
            g.loadRaidBase(JsonLoc.world(r), JsonLoc.x(r), JsonLoc.y(r), JsonLoc.z(r), rs.getLong("raid_base_exp"),
                    rw != null ? UUID.fromString(rw) : null);
        }
        String gwp = rs.getString("guild_waypoint");
        if (gwp != null) g.setGuildWaypointId(UUID.fromString(gwp));

        String egg = rs.getString("egg");
        if (egg != null) {
            JsonObject e = gson.fromJson(egg, JsonObject.class);
            g.loadEgg(e.get("x").getAsDouble(), e.get("y").getAsDouble(), e.get("z").getAsDouble(),
                    e.get("hp").getAsInt(), e.get("maxHp").getAsInt());
        }
        g.setRankPoints(rs.getInt("rank_points"));
        return g;
    }

    public void upsert(Guild g) {
        String sql = "INSERT INTO guilds (tag, owner_uuid, deputies, members, allies, center, radius,"
                + " home, raid_base, raid_base_exp, raid_waypoint, guild_waypoint, egg, rank_points,"
                + " created_at, updated_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE"
                + " owner_uuid=VALUES(owner_uuid), deputies=VALUES(deputies), members=VALUES(members),"
                + " allies=VALUES(allies), center=VALUES(center), radius=VALUES(radius),"
                + " home=VALUES(home), raid_base=VALUES(raid_base), raid_base_exp=VALUES(raid_base_exp),"
                + " raid_waypoint=VALUES(raid_waypoint), guild_waypoint=VALUES(guild_waypoint), egg=VALUES(egg),"
                + " rank_points=VALUES(rank_points), updated_at=VALUES(updated_at)";

        JsonObject center = JsonLoc.of(g.getWorldName(), g.getX(), g.getY(), g.getZ());
        JsonArray members = new JsonArray(); g.getMembers().forEach(u -> members.add(u.toString()));
        JsonArray deputies = new JsonArray(); g.getDeputies().forEach(u -> deputies.add(u.toString()));
        JsonArray allies = new JsonArray(); g.getAllies().forEach(allies::add);

        JsonObject home = g.hasHome() ? JsonLoc.of(g.getHomeWorld(), g.getHomeX(), g.getHomeY(), g.getHomeZ()) : null;
        JsonObject raid = g.getRaidWorld() != null ? JsonLoc.of(g.getRaidWorld(), g.getRaidX(), g.getRaidY(), g.getRaidZ()) : null;
        JsonObject egg = null;
        if (g.hasEgg()) {
            egg = new JsonObject();
            egg.addProperty("x", g.getEggX()); egg.addProperty("y", g.getEggY()); egg.addProperty("z", g.getEggZ());
            egg.addProperty("hp", g.getEggHp()); egg.addProperty("maxHp", g.getMaxEggHp());
        }

        long now = System.currentTimeMillis();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g.getTag());
            ps.setString(2, g.getOwner().toString());
            ps.setString(3, gson.toJson(deputies));
            ps.setString(4, gson.toJson(members));
            ps.setString(5, gson.toJson(allies));
            ps.setString(6, gson.toJson(center));
            ps.setInt(7, g.getRadius());
            ps.setString(8, home != null ? gson.toJson(home) : null);
            ps.setString(9, raid != null ? gson.toJson(raid) : null);
            ps.setLong(10, Math.max(0, g.getRaidExpiresAt()));
            ps.setString(11, g.getRaidWaypointId() != null ? g.getRaidWaypointId().toString() : null);
            ps.setString(12, g.getGuildWaypointId() != null ? g.getGuildWaypointId().toString() : null);
            ps.setString(13, egg != null ? gson.toJson(egg) : null);
            ps.setInt(14, g.getRankPoints());
            ps.setLong(15, now);
            ps.setLong(16, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("guilds upsert failed: " + g.getTag(), e);
        }
    }

    public void delete(String tag) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM guilds WHERE tag=?")) {
            ps.setString(1, tag);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("guilds delete failed: " + tag, e);
        }
    }

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
            ps.setString(1, json); ps.setString(2, tag); ps.executeUpdate();
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
            ps.setString(1, json); ps.setString(2, tag); ps.executeUpdate();
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

    // ── Dispensery na niczyim terenie ──────────────────────────────────────
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
             PreparedStatement ps = c.prepareStatement("SELECT owner_tag FROM placed_dispensers WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, world); ps.setInt(2, x); ps.setInt(3, y); ps.setInt(4, z);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString(1); }
        } catch (SQLException ignored) { }
        return null;
    }

    public void removeDispenserClaim(String world, int x, int y, int z) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM placed_dispensers WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, world); ps.setInt(2, x); ps.setInt(3, y); ps.setInt(4, z);
            ps.executeUpdate();
        } catch (SQLException ignored) { }
    }
}
