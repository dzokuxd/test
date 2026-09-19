package pl.gildie.monument;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CrystalHP {
    private static final Map<UUID, Integer> hp = new HashMap<>();

    public static void set(UUID id, int max) { hp.put(id, max); }
    public static int get(UUID id) { return hp.getOrDefault(id, 0); }
    public static int damage(UUID id, int amt) {
        int cur = hp.getOrDefault(id, 0);
        int left = Math.max(0, cur - amt);
        hp.put(id, left);
        return left;
    }
    public static void remove(UUID id) { hp.remove(id); }
}
