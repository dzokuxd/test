package pl.gildie.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import pl.gildie.Const;
import pl.gildie.db.GuildRepository;
import pl.gildie.managers.BuildLockManager;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectionListener implements Listener {
    private final GuildManager guildManager;
    private final BuildLockManager buildLock;
    private final GuildRepository repo;
    private final Map<String, Integer> dispenserHits = new ConcurrentHashMap<>();

    public ProtectionListener(GuildManager guildManager, BuildLockManager buildLock, GuildRepository repo) {
        this.guildManager = guildManager;
        this.buildLock = buildLock;
        this.repo = repo;
    }

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block b = event.getBlock();

        // Blokada budowy dotyczy WYLACZNIE terenu gildii, ktora ma lock
        Guild territory = guildManager.getGuildAt(b.getLocation());
        if (territory != null && buildLock.isLocked(territory.getTag())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie mozesz budowac na terenie gildii §e" + territory.getTag()
                    + " §cjeszcze przez §e" + buildLock.secondsLeft(territory.getTag()) + "s§c!");
            return;
        }

        Guild raid = guildManager.getRaidBaseOwnerAt(b.getLocation());
        if (raid != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie mozesz budować na bazie wypadowej gildii §e" + raid.getTag() + "§c!");
            return;
        }

        if (territory == null) {
            if (b.getType() == Material.DISPENSER) {
                Guild own = guildManager.getGuildByPlayer(event.getPlayer().getUniqueId());
                repo.saveDispenserClaim(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(),
                        own != null ? own.getTag() : null);
            }
            return;
        }
        if (!territory.isMember(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie mozesz budować na terenie gildii §e" + territory.getTag() + "§c!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();

        Guild raid = guildManager.getRaidBaseOwnerAt(b.getLocation());
        if (raid != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie mozesz niszczyć na bazie wypadowej gildii §e" + raid.getTag() + "§c!");
            return;
        }

        Guild guild = guildManager.getGuildAt(b.getLocation());
        if (guild == null) {
            if (b.getType() == Material.DISPENSER) {
                String owner = repo.getDispenserClaim(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
                Guild breaker = guildManager.getGuildByPlayer(event.getPlayer().getUniqueId());
                String breakerTag = breaker != null ? breaker.getTag() : null;
                if (owner != null && owner.equals(breakerTag)) {
                    repo.removeDispenserClaim(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
                    dispenserHits.remove(key(b));
                    return; // wlasciciel niszczy od razu
                }
                String k = key(b);
                int hits = dispenserHits.merge(k, 1, Integer::sum);
                if (hits < Const.DISPENSER_HITS_REQUIRED) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("§cTen dispenser nalezy do innej gildii! Uderzen pozostalo: §e"
                            + (Const.DISPENSER_HITS_REQUIRED - hits));
                    return;
                }
                dispenserHits.remove(k);
                repo.removeDispenserClaim(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            }
            return;
        }
        if (guild.isEggBlock(b.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (!guild.isMember(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie mozesz niszczyć na terenie gildii §e" + guild.getTag() + "§c!");
        }
    }
}
