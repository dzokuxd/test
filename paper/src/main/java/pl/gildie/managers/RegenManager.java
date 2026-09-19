package pl.gildie.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import pl.gildie.GildieModule;
import pl.gildie.model.Guild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RegenManager {
    public static final int Y_SPLIT = 60;
    private static final int BLOCKS_PER_TICK = 5;
    private static final long TICK_PERIOD = 2L;
    private static final Gson gson = new Gson();

    private final GildieModule module;
    private final JavaPlugin plugin;
    private final GuildManager guildManager;
    private final Map<String, Map<String, SavedBlock>> byGuild = new HashMap<>();
    private final Map<UUID, BossBar> regenBars = new HashMap<>();

    public RegenManager(GildieModule module) {
        this.module = module;
        this.plugin = module.plugin();
        this.guildManager = module.getGuildManager();
        load();
    }

    public void load() {
        synchronized (byGuild) { byGuild.clear(); }
        Map<String, String> all = module.getGuildRepository().loadAllRegen();
        for (Map.Entry<String, String> e : all.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) continue;
            try {
                JsonArray arr = gson.fromJson(e.getValue(), JsonArray.class);
                Map<String, SavedBlock> m = new HashMap<>();
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    SavedBlock b = new SavedBlock(o.get("world").getAsString(), o.get("x").getAsInt(),
                            o.get("y").getAsInt(), o.get("z").getAsInt(), o.get("data").getAsString());
                    m.put(key(b.world, b.x, b.y, b.z), b);
                }
                synchronized (byGuild) { byGuild.put(e.getKey(), m); }
            } catch (Exception ex) { plugin.getLogger().warning("Regen load fail: " + ex.getMessage()); }
        }
    }

    public void save() {
        Map<String, JsonArray> snap = snapshot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeAll(snap));
    }

    public void saveSync() { writeAll(snapshot()); }

    private Map<String, JsonArray> snapshot() {
        Map<String, JsonArray> out = new HashMap<>();
        synchronized (byGuild) {
            for (Map.Entry<String, Map<String, SavedBlock>> e : byGuild.entrySet()) {
                JsonArray arr = new JsonArray();
                for (SavedBlock b : e.getValue().values()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("world", b.world); o.addProperty("x", b.x);
                    o.addProperty("y", b.y); o.addProperty("z", b.z); o.addProperty("data", b.data);
                    arr.add(o);
                }
                out.put(e.getKey(), arr);
            }
        }
        return out;
    }

    private void writeAll(Map<String, JsonArray> snap) {
        for (Map.Entry<String, JsonArray> e : snap.entrySet()) {
            try { module.getGuildRepository().saveRegenJson(e.getKey(), gson.toJson(e.getValue())); }
            catch (Exception ex) { plugin.getLogger().severe("Regen save fail: " + ex.getMessage()); }
        }
    }

    public void recordBlock(String world, int x, int y, int z, String data) {
        if (isFluid(data)) return;
        World w = Bukkit.getWorld(world);
        if (w == null) return;
        Guild g = guildManager.getGuildAt(new Location(w, x + 0.5, y + 0.5, z + 0.5));
        if (g == null) return;
        synchronized (byGuild) {
            byGuild.computeIfAbsent(g.getTag(), t -> new HashMap<>()).put(key(world, x, y, z), new SavedBlock(world, x, y, z, data));
        }
    }

    public void scheduleAutoRegenAbove(List<String> keys) {
        if (keys.isEmpty()) return;
        new BukkitRunnable() {
            @Override public void run() {
                List<SavedBlock> blocks = new ArrayList<>();
                for (String k : keys) { SavedBlock b = find(k); if (b != null && b.y > Y_SPLIT) blocks.add(b); }
                blocks.sort((a, b) -> Integer.compare(a.y, b.y));
                for (SavedBlock b : blocks) if (restore(b)) remove(key(b.world, b.x, b.y, b.z));
                save();
            }
        }.runTaskLater(plugin, 20 * 20L);
    }

    public boolean isRegenerating(UUID uuid) { return regenBars.containsKey(uuid); }

    public void startManualRegen(Player player) {
        if (isRegenerating(player.getUniqueId())) { player.sendMessage("\u00a7cRegeneracja juz trwa!"); return; }
        List<Map.Entry<String, SavedBlock>> toRegen = new ArrayList<>();
        synchronized (byGuild) {
            for (Map<String, SavedBlock> m : byGuild.values())
                for (Map.Entry<String, SavedBlock> en : m.entrySet())
                    if (en.getValue().y <= Y_SPLIT) toRegen.add(en);
        }
        if (toRegen.isEmpty()) { player.sendMessage("\u00a7cBrak blokow do regeneracji ponizej Y=" + Y_SPLIT + "."); return; }
        toRegen.sort((a, b) -> Integer.compare(a.getValue().y, b.getValue().y));

        int total = toRegen.size();
        BossBar bar = Bukkit.createBossBar("\u00a7aRegeneracja gildii...", BarColor.GREEN, BarStyle.SOLID);
        bar.addPlayer(player); bar.setProgress(0);
        regenBars.put(player.getUniqueId(), bar);
        player.sendMessage("\u00a7aRozpoczeto regeneracje \u00a7e" + total + " \u00a7ablokow.");

        new BukkitRunnable() {
            int index = 0, done = 0;
            @Override public void run() {
                if (!player.isOnline()) { bar.removeAll(); regenBars.remove(player.getUniqueId()); save(); cancel(); return; }
                if (index >= total) {
                    bar.removeAll(); regenBars.remove(player.getUniqueId());
                    player.sendMessage("\u00a7aRegeneracja zakonczona! \u00a7e" + done + " \u00a7ablokow.");
                    save(); cancel(); return;
                }
                for (int i = 0; i < BLOCKS_PER_TICK && index < total; i++, index++) {
                    Map.Entry<String, SavedBlock> en = toRegen.get(index);
                    if (restore(en.getValue())) { remove(en.getKey()); done++; }
                }
                double progress = total == 0 ? 1.0 : (double) done / total;
                long remTicks = (long) Math.ceil((total - done) / (double) BLOCKS_PER_TICK) * TICK_PERIOD;
                bar.setProgress(Math.min(1.0, progress));
                bar.setTitle("\u00a7aZregenerowano: \u00a7e" + done + "/" + total + " \u00a77(" + (int) (progress * 100) + "%) \u00a78| \u00a7fCzas: \u00a7e" + formatTime(remTicks / 20.0));
            }
        }.runTaskTimer(plugin, 0L, TICK_PERIOD);
    }

    public void clearBar(Player player) {
        BossBar bar = regenBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    private SavedBlock find(String k) {
        synchronized (byGuild) {
            for (Map<String, SavedBlock> m : byGuild.values()) { SavedBlock b = m.get(k); if (b != null) return b; }
        }
        return null;
    }

    private void remove(String k) {
        synchronized (byGuild) {
            for (Map<String, SavedBlock> m : byGuild.values()) if (m.remove(k) != null) return;
        }
    }

    private boolean restore(SavedBlock block) {
        World world = Bukkit.getWorld(block.world);
        if (world == null || block.data == null) return false;
        if (isFluid(block.data)) return true;
        try {
            Block b = world.getBlockAt(block.x, block.y, block.z);
            if (b.getType() == Material.WATER || b.getType() == Material.LAVA) b.setType(Material.AIR, false);
            b.setBlockData(Bukkit.createBlockData(block.data), false);
            return true;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Nie mozna przywrocic bloku " + block.data);
            return false;
        }
    }

    private boolean isFluid(String data) {
        if (data == null) return false;
        try {
            Material mat = Bukkit.createBlockData(data).getMaterial();
            return mat == Material.WATER || mat == Material.LAVA;
        } catch (IllegalArgumentException e) {
            String d = data.toLowerCase();
            return d.contains("water") || d.contains("lava");
        }
    }

    public static String key(String world, int x, int y, int z) { return world + ";" + x + ";" + y + ";" + z; }

    private String formatTime(double seconds) {
        if (seconds < 1) return "0s";
        int t = (int) Math.ceil(seconds);
        return (t / 60 > 0 ? (t / 60) + "m " : "") + (t % 60) + "s";
    }

    public static class SavedBlock {
        public final String world; public final int x, y, z; public final String data;
        public SavedBlock(String world, int x, int y, int z, String data) {
            this.world = world; this.x = x; this.y = y; this.z = z; this.data = data;
        }
    }
}
