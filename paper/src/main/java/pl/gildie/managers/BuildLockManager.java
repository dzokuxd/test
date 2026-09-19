package pl.gildie.managers;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.model.Guild;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BuildLockManager {
    private final JavaPlugin plugin;
    private final GuildManager guildManager;
    private final Map<String, Long> locks = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public BuildLockManager(JavaPlugin plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void lock(String tag, long seconds) {
        locks.put(tag.toUpperCase(), System.currentTimeMillis() + seconds * 1000L);
    }

    public boolean isLocked(String tag) {
        if (tag == null) return false;
        Long until = locks.get(tag.toUpperCase());
        if (until == null) return false;
        if (System.currentTimeMillis() > until) { locks.remove(tag.toUpperCase()); return false; }
        return true;
    }

    public long secondsLeft(String tag) {
        Long until = locks.get(tag.toUpperCase());
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Guild g = guildManager.getGuildByPlayer(p.getUniqueId());
            if (g != null && isLocked(g.getTag())) {
                BossBar bar = bars.computeIfAbsent(p.getUniqueId(), id -> {
                    BossBar b = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
                    b.addPlayer(p);
                    return b;
                });
                bar.setTitle("§cNie mozesz budowac jeszcze przez: §e" + secondsLeft(g.getTag()) + "s");
                bar.setProgress(1.0);
            } else {
                BossBar bar = bars.remove(p.getUniqueId());
                if (bar != null) bar.removeAll();
            }
        }
    }

    public void shutdown() {
        for (BossBar b : bars.values()) b.removeAll();
        bars.clear();
    }
}
