package pl.gildie.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.gildie.Const;
import pl.gildie.db.MonumentRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PointsManager {
    private static MonumentRepository repo;
    private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public static void init(MonumentRepository r) {
        repo = r;
    }

    public static boolean tryHit(Player p) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(p.getUniqueId());
        if (last != null && now - last < Const.MONUMENT_HIT_COOLDOWN_MS) return false;
        cooldowns.put(p.getUniqueId(), now);
        return true;
    }

    // Punkty za uderzenia: 1 narożny / 3 środek (wartości z Const) -> users.monument_points
    public static void addPoints(Player p, int n) {
        repo.addMonumentPoints(p.getUniqueId().toString(), p.getName(), n);
    }

    public static int getPoints(UUID u) {
        return repo.getMonumentPoints(u.toString());
    }

    public static void removePoints(Player p, int n) {
        if (p == null) return;
        repo.addMonumentPoints(p.getUniqueId().toString(), p.getName(), -n);
    }

    public static void removePoints(UUID u, int n) {
        String name = "Gracz";
        Player p = Bukkit.getPlayer(u);
        if (p != null) name = p.getName();
        repo.addMonumentPoints(u.toString(), name, -n);
    }
}