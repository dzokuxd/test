package pl.dzoku.sectorsystem.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnCommand implements CommandExecutor, Listener {
    private static final int DELAY_SECONDS = 30;

    private final SectorSystemPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public SpawnCommand(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Pending {
        final BukkitTask task;
        final Location start;
        Pending(BukkitTask task, Location start) { this.task = task; this.start = start; }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko dla graczy");
            return true;
        }
        UUID uuid = player.getUniqueId();

        if (pending.containsKey(uuid)) {
            cancel(uuid);
            player.sendMessage(Component.text("Teleportacja anulowana.", NamedTextColor.YELLOW));
            return true;
        }

        Location start = player.getLocation().clone();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int left = DELAY_SECONDS;

            @Override
            public void run() {
                if (!player.isOnline()) { cancel(uuid); return; }
                left--;
                if (left <= 0) {
                    cancel(uuid);
                    plugin.getTransferStateMachine().initiateTransfer(player, "spawn");
                } else {
                    player.sendActionBar(Component.text("Teleport na spawn za " + left + "s - NIE ruszaj sie!", NamedTextColor.GREEN));
                }
            }
        }, 20L, 20L);

        pending.put(uuid, new Pending(task, start));
        player.sendMessage(Component.text("Teleportacja za " + DELAY_SECONDS + "s. Ruch anuluje!", NamedTextColor.GREEN));
        return true;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Pending p = pending.get(event.getPlayer().getUniqueId());
        if (p == null) return;
        Location from = p.start;
        Location to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            cancel(event.getPlayer().getUniqueId());
            event.getPlayer().sendActionBar(Component.text("Ruszyles sie - teleport anulowany!", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    private void cancel(UUID uuid) {
        Pending p = pending.remove(uuid);
        if (p != null) p.task.cancel();
    }
}
