package pl.gildie.db;

import com.google.gson.JsonObject;
import org.bukkit.Location;

public final class JsonLoc {
    public static JsonObject of(String world, double x, double y, double z) {
        JsonObject o = new JsonObject();
        o.addProperty("world", world);
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        return o;
    }
    public static JsonObject of(Location loc) {
        return of(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }
    public static String world(JsonObject o) { return o.get("world").getAsString(); }
    public static double x(JsonObject o) { return o.get("x").getAsDouble(); }
    public static double y(JsonObject o) { return o.get("y").getAsDouble(); }
    public static double z(JsonObject o) { return o.get("z").getAsDouble(); }
    private JsonLoc() {}
}
