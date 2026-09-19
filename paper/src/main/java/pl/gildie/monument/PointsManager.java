package pl.gildie.monument;

import org.bukkit.entity.Player;
import pl.gildie.Const;
import pl.gildie.db.MonumentRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PointsManager {
    private static MonumentRepository repo;
    private static final Map<UUID, Integer> points = new HashMap<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();

    public static void init(MonumentRepository r) { repo = r; }

    public static boolean tryHit(Player p) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(p.getUniqueId());
        if (last != null && now - last < Const.MONUMENT_HIT_COOLDOWN_MS) return false;
        cooldowns.put(p.getUniqueId(), now);
        return true;
    }

    public static void addPoints(Player p, int n) {
        UUID u = p.getUniqueId();
        int cur = points.getOrDefault(u, repo.getMonumentPoints(u.toString()));
        int nw = cur + n;
        points.put(u, nw);
        repo.setMonumentPoints(u.toString(), nw);
    }

    public static int getPoints(UUID u) {
        return points.getOrDefault(u, repo.getMonumentPoints(u.toString()));
    }

    public static void removePoints(UUID u, int n) {
        int nw = Math.max(0, getPoints(u) - n);
        points.put(u, nw);
        repo.setMonumentPoints(u.toString(), nw);
    }
}
