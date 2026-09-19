package pl.gildie.managers;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.gildie.model.Guild;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PeriscopeManager {
    private static final int DURATION_SECONDS = 25;
    private static final int HEIGHT_ABOVE_CENTER = 45;
    private static final int BORDER_POINTS = 96;

    private final JavaPlugin plugin;
    private final GuildManager guildManager;
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Map<UUID, BukkitTask> borderTasks = new HashMap<>();

    public PeriscopeManager(JavaPlugin plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
    }

    public boolean isInPeriscope(Player player) { return returnLocations.containsKey(player.getUniqueId()); }

    private void sendActionBar(Player player, String msg) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }

    public void start(Player player) {
        if (isInPeriscope(player)) { player.sendMessage("\u00a7cJuz jestes w peryskopie!"); return; }
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) { player.sendMessage("\u00a7cNie jestes w gildii!"); return; }
        Location center = guild.getCenter();
        if (center == null) { player.sendMessage("\u00a7cSwiat gildii niedostepny."); return; }

        Location view = center.clone();
        view.setY(center.getY() + HEIGHT_ABOVE_CENTER);
        view.setPitch(90f);
        returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        player.teleport(view);
        player.setAllowFlight(true); player.setFlying(true); player.setFlySpeed(0.12f);
        player.sendMessage("\u00a7aPeryskop aktywny! \u00a77Kucnij, aby wyjsc.");

        final double viewY = view.getY();
        BukkitTask countdown = new BukkitRunnable() {
            int left = DURATION_SECONDS;
            @Override public void run() {
                if (!player.isOnline() || !returnLocations.containsKey(player.getUniqueId())) { cancel(); return; }
                sendActionBar(player, "\u00a7eKucnij aby wyjsc \u00a77- \u00a7c\u00a7l" + left + "s");
                if (left-- <= 0) stop(player);
            }
        }.runTaskTimer(plugin, 0L, 20L);

        BukkitTask border = new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline() || !returnLocations.containsKey(player.getUniqueId())) { cancel(); return; }
                drawBorder(player, guild, viewY);
            }
        }.runTaskTimer(plugin, 0L, 2L);

        tasks.put(player.getUniqueId(), countdown);
        borderTasks.put(player.getUniqueId(), border);
    }

    public void stop(Player player) {
        UUID id = player.getUniqueId();
        BukkitTask t = tasks.remove(id); if (t != null) t.cancel();
        BukkitTask b = borderTasks.remove(id); if (b != null) b.cancel();
        Location back = returnLocations.remove(id);
        if (back != null && player.isOnline()) {
            player.setFlying(false); player.setAllowFlight(false); player.setFlySpeed(0.1f);
            player.teleport(back);
            player.sendMessage("\u00a7aWyszedles z peryskopu.");
            sendActionBar(player, "");
        }
    }

    private void drawBorder(Player player, Guild guild, double viewY) {
        Location c = guild.getCenter();
        if (c == null) return;
        World world = c.getWorld();
        if (world == null) return;
        double r = guild.getRadius(), cx = c.getX() + 0.5, cz = c.getZ() + 0.5, groundY = c.getY() + 1.5;
        double[] ys = { viewY - 4, viewY - 10, viewY - 18, groundY };
        for (int i = 0; i < BORDER_POINTS; i++) {
            double a = 2 * Math.PI * i / BORDER_POINTS;
            double x = cx + r * Math.cos(a), z = cz + r * Math.sin(a);
            for (double y : ys) {
                player.spawnParticle(Particle.END_ROD, new Location(world, x, y, z), 2, 0.05, 0.15, 0.05, 0);
                if (i % 2 == 0) player.spawnParticle(Particle.FLAME, new Location(world, x, y, z), 1, 0.02, 0.08, 0.02, 0);
            }
        }
        for (double y = groundY; y < viewY - 2; y += 3)
            player.spawnParticle(Particle.END_ROD, new Location(world, cx, y, cz), 1, 0, 0, 0, 0);
    }
}
