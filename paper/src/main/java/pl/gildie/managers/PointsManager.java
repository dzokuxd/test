package pl.gildie.managers;

import org.bukkit.Bukkit;
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

    // Dodajemy cache nazw graczy, aby zawsze mieć poprawną nazwę do zapisu w bazie
    private static final Map<UUID, String> playerNames = new HashMap<>();

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

    public static void addPoints(Player p, int n) {
        UUID u = p.getUniqueId();
        String name = p.getName();
        playerNames.put(u, name); // Zapisujemy nazwę w cache

        int cur = points.getOrDefault(u, repo.getMonumentPoints(u.toString()));
        int nw = cur + n;
        points.put(u, nw);

        // POPRAWKA: Przekazujemy teraz UUID, NAZWĘ i punkty
        repo.setMonumentPoints(u.toString(), name, nw);
    }

    public static int getPoints(UUID u) {
        return points.getOrDefault(u, repo.getMonumentPoints(u.toString()));
    }

    // Wersja z obiektem Player (zalecana, gdy gracz jest online)
    public static void removePoints(Player p, int n) {
        if (p == null) return;
        UUID u = p.getUniqueId();
        String name = p.getName();
        playerNames.put(u, name);

        int nw = Math.max(0, getPoints(u) - n);
        points.put(u, nw);

        // POPRAWKA: Przekazujemy teraz UUID, NAZWĘ i punkty
        repo.setMonumentPoints(u.toString(), name, nw);
    }

    // Wersja z samym UUID (zachowana dla kompatybilności, próbuje odczytać nazwę z cache lub z serwera)
    public static void removePoints(UUID u, int n) {
        String name = playerNames.getOrDefault(u, "Gracz");

        // Jeśli gracz jest online, pobierz jego aktualną nazwę
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            name = p.getName();
            playerNames.put(u, name);
        }

        int nw = Math.max(0, getPoints(u) - n);
        points.put(u, nw);

        // POPRAWKA: Przekazujemy teraz UUID, NAZWĘ i punkty
        repo.setMonumentPoints(u.toString(), name, nw);
    }
}