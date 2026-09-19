package pl.gildie.war;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import pl.gildie.GildieModule;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TntManager {
    private static final AtomicBoolean tntEnabled = new AtomicBoolean(false);
    private static Boolean forced = null;
    private static BukkitTask task;
    private static int startHour = 16;
    private static int endHour = 21;
    private static ZoneId zone = ZoneId.of("Europe/Warsaw");

    private TntManager() { }

    public static void start(GildieModule module) {
        startHour = module.config().getInt("tnt.start-hour", 16);
        endHour = module.config().getInt("tnt.end-hour", 21);
        try { zone = ZoneId.of(module.config().getString("tnt.timezone", "Europe/Warsaw")); }
        catch (Exception ignored) { zone = ZoneId.of("Europe/Warsaw"); }
        task = Bukkit.getScheduler().runTaskTimer(module.plugin(), TntManager::checkTime, 20L, 20L * 30);
        checkTime();
    }

    public static void stop() { if (task != null) { task.cancel(); task = null; } }

    private static void checkTime() {
        int hour = LocalTime.now(zone).getHour();
        boolean should = forced != null ? forced : (hour >= startHour && hour < endHour);
        boolean was = tntEnabled.getAndSet(should);
        if (was != should) {
            if (should) Bukkit.broadcastMessage("\u00a7a\u00a7l[TNT] \u00a7aTNT wlaczone (" + startHour + "-" + endHour + ").");
            else Bukkit.broadcastMessage("\u00a7c\u00a7l[TNT] \u00a7cTNT wylaczone. Wybuchy nie niszcza blokow.");
        }
    }

    public static boolean isTntEnabled() { return tntEnabled.get(); }
    public static void setEnabled(boolean enabled) { forced = enabled; tntEnabled.set(enabled); }
    public static void clearForce() { forced = null; checkTime(); }
}
