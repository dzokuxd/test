package pl.gildie.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportUtil {
    private static final Map<UUID, Integer> TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Location> START = new ConcurrentHashMap<>();

    private TeleportUtil() { }

    public static void teleportCountdown(JavaPlugin plugin, Player player, Location dest, int seconds, String label) {
        cancel(player);
        Location start = player.getLocation().clone();
        START.put(player.getUniqueId(), start);
        player.sendMessage("\u00a7eTeleportacja na \u00a7f" + label + " \u00a7eza \u00a7f" + seconds + "s\u00a7e. Nie ruszaj sie!");

        final int[] left = {seconds};
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) { cleanup(player.getUniqueId()); cancel(); return; }
                Location s = START.get(player.getUniqueId());
                if (s == null || moved(s, player.getLocation())) {
                    player.sendMessage("\u00a7cTeleportacja anulowana \u2014 ruszyles sie.");
                    cleanup(player.getUniqueId());
                    cancel();
                    return;
                }
                left[0]--;
                if (left[0] <= 0) {
                    if (dest == null || dest.getWorld() == null) player.sendMessage("\u00a7cCel teleportacji niedostepny.");
                    else { player.teleport(dest); player.sendMessage("\u00a7aPrzeteleportowano na \u00a7f" + label + "\u00a7a."); }
                    cleanup(player.getUniqueId());
                    cancel();
                    return;
                }
                if (left[0] <= 5 || left[0] % 5 == 0) player.sendMessage("\u00a7eTeleportacja za \u00a7f" + left[0] + "s\u00a7e...");
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
        TASKS.put(player.getUniqueId(), taskId);
    }

    public static void cancel(Player player) {
        Integer id = TASKS.remove(player.getUniqueId());
        if (id != null) Bukkit.getScheduler().cancelTask(id);
        START.remove(player.getUniqueId());
    }

    public static boolean isTeleporting(Player player) { return TASKS.containsKey(player.getUniqueId()); }

    private static void cleanup(UUID uuid) { TASKS.remove(uuid); START.remove(uuid); }

    private static boolean moved(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null) return true;
        if (!a.getWorld().equals(b.getWorld())) return true;
        return a.distanceSquared(b) > 0.3;
    }
}
