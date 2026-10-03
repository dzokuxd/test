package pl.gildie.scoreboard.modules;
import org.bukkit.entity.Player;
import pl.gildie.scoreboard.ScoreboardModule;
import java.util.*;

public class PingScoreboardModule implements ScoreboardModule {
    private static final Map<UUID, PingData> activePings = new HashMap<>();
    public static class PingData {
        public final String playerName, guildTag, world, coords;
        public final long expiresAt;
        public final Set<UUID> viewers;
        public PingData(String playerName, String guildTag, String world, String coords, long expiresAt, Set<UUID> viewers) {
            this.playerName = playerName; this.guildTag = guildTag; this.world = world; this.coords = coords; this.expiresAt = expiresAt; this.viewers = viewers;
        }
    }
    public static void startPing(String playerName, String guildTag, String world, String coords, long expiresAt, Set<UUID> viewers) {
        activePings.put(UUID.randomUUID(), new PingData(playerName, guildTag, world, coords, expiresAt, viewers));
    }
    @Override public String getName() { return "Ping"; }
    @Override public int getPriority() { return 80; }
    @Override public boolean isActive(Player player) {
        long now = System.currentTimeMillis();
        activePings.entrySet().removeIf(e -> e.getValue().expiresAt < now);
        for (PingData data : activePings.values()) if (data.viewers.contains(player.getUniqueId())) return true;
        return false;
    }
    @Override public List<String> getLines(Player player) {
        List<String> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (PingData data : activePings.values()) {
            if (data.viewers.contains(player.getUniqueId())) {
                long remaining = (data.expiresAt - now) / 1000;
                if (remaining > 0) {
                    lines.add("§6§l⚠ PING POMOCY"); lines.add("§7Gracz: §f" + data.playerName);
                    lines.add("§7Gildia: §e" + data.guildTag); lines.add("§7Swiat: §f" + data.world);
                    lines.add("§7Kordy: §f" + data.coords); lines.add("§cWygasa za: §e" + remaining + "s");
                    break;
                }
            }
        }
        return lines;
    }
}