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

public class TerritoryBarManager {
    private final JavaPlugin plugin;
    private final GuildManager guildManager;
    private final RegenManager regenManager;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, String> lastState = new HashMap<>();

    public TerritoryBarManager(JavaPlugin plugin, GuildManager guildManager, RegenManager regenManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        this.regenManager = regenManager;
    }

    public void start() { Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L); }

    public void tick() { for (Player player : Bukkit.getOnlinePlayers()) update(player); }

    public void update(Player player) {
        if (regenManager.isRegenerating(player.getUniqueId())) { hide(player); return; }
        Guild territory = guildManager.getGuildAt(player.getLocation());
        if (territory == null) { hide(player); lastState.remove(player.getUniqueId()); return; }

        boolean own = territory.isMember(player.getUniqueId());
        String state = (own ? "OWN:" : "ENEMY:") + territory.getTag();
        if (state.equals(lastState.get(player.getUniqueId()))) return;
        lastState.put(player.getUniqueId(), state);

        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar created = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
            created.addPlayer(player);
            created.setProgress(1.0);
            return created;
        });

        if (own) { bar.setColor(BarColor.GREEN); bar.setTitle("\u00a7aTeren twojej gildii \u00a7e" + territory.getTag()); }
        else { bar.setColor(BarColor.RED); bar.setTitle("\u00a7cTeren obcej gildii \u00a7e" + territory.getTag()); }
        bar.setProgress(1.0);
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
    }

    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
        lastState.remove(player.getUniqueId());
    }

    public void shutdown() {
        for (BossBar bar : bars.values()) bar.removeAll();
        bars.clear();
        lastState.clear();
    }
}
