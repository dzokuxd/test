package pl.gildie.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import pl.gildie.Const;
import pl.gildie.managers.BuildLockManager;
import pl.gildie.managers.GuildManager;
import pl.gildie.managers.RegenManager;
import pl.gildie.model.Guild;
import pl.gildie.war.WarManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ExplosionListener implements Listener {
    private final GuildManager guildManager;
    private final RegenManager regenManager;
    private final BuildLockManager buildLock;
    private final WarManager warManager;

    public ExplosionListener(GuildManager guildManager, RegenManager regenManager,
                             BuildLockManager buildLock, WarManager warManager) {
        this.guildManager = guildManager;
        this.regenManager = regenManager;
        this.buildLock = buildLock;
        this.warManager = warManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!isTnt(event.getEntityType())) return;

        List<String> autoKeys = new ArrayList<>();
        boolean any = false;
        Set<String> touchedGuilds = new HashSet<>();
        Location explosionLocation = event.getLocation();

        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Material type = block.getType();
            if (type == Material.AIR || type == Material.TNT || type == Material.FIRE || type.isAir()) continue;

            Guild guild = guildManager.getGuildAt(block.getLocation());
            boolean isInGuildTerritory = (guild != null);
            boolean isAllowed = isInGuildTerritory;

            if (!isAllowed) {
                for (Guild g : guildManager.getAll()) {
                    Location center = g.getCenter();
                    if (center == null) continue;
                    double dx = block.getX() - center.getX(), dz = block.getZ() - center.getZ();
                    if (dx * dx + dz * dz <= Math.pow(g.getRadius() + 5.0, 2)) { isAllowed = true; break; }
                }
            }
            if (!isAllowed) { iterator.remove(); continue; }

            if (isInGuildTerritory) {
                touchedGuilds.add(guild.getTag());
                regenManager.recordBlock(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                        block.getBlockData().getAsString());
                any = true;
                if (block.getY() > RegenManager.Y_SPLIT) {
                    autoKeys.add(RegenManager.key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
                }
            }
        }

        destroyResistantBlocks(explosionLocation, touchedGuilds);

        for (String tag : touchedGuilds) {
            // bez wojny = 60s, z wojna = 180s
            long sec = warManager.getActiveWarCount(tag) > 0
                    ? Const.TNT_BUILD_LOCK_WAR_SECONDS
                    : Const.TNT_BUILD_LOCK_SECONDS;
            buildLock.lock(tag, sec);
            Guild g = guildManager.getGuild(tag);
            if (g != null) {
                for (UUID id : g.getMembers()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) {
                        p.sendMessage("§c§l[ALERT] §cNa terenie gildii §e" + tag + " §cwybuchlo TNT! "
                                + "Blokada budowy: §e" + sec + "s");
                    }
                }
            }
        }

        if (!any) return;
        regenManager.save();
        regenManager.scheduleAutoRegenAbove(autoKeys);
    }

    private void destroyResistantBlocks(Location explosionLocation, Set<String> touchedGuilds) {
        int radius = 4;
        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radius * radius) continue;
                    Block block = explosionLocation.getBlock().getRelative(x, y, z);
                    Material type = block.getType();

                    Guild guild = guildManager.getGuildAt(block.getLocation());
                    boolean isInGuildTerritory = (guild != null);
                    boolean isAllowed = isInGuildTerritory;
                    if (!isAllowed) {
                        for (Guild g : guildManager.getAll()) {
                            Location center = g.getCenter();
                            if (center == null) continue;
                            double dx = block.getX() - center.getX(), dz = block.getZ() - center.getZ();
                            if (dx * dx + dz * dz <= Math.pow(g.getRadius() + 5.0, 2)) { isAllowed = true; break; }
                        }
                    }
                    if (!isAllowed) continue;

                    double chance = ThreadLocalRandom.current().nextDouble() * 100.0;
                    boolean shouldDestroy = false;
                    if (type == Material.OBSIDIAN || type == Material.CRYING_OBSIDIAN) shouldDestroy = chance <= 25.0;
                    else if (type == Material.ENDER_CHEST || type == Material.ANVIL || type == Material.CHIPPED_ANVIL
                            || type == Material.DAMAGED_ANVIL || type == Material.ENCHANTING_TABLE) shouldDestroy = chance <= 10.0;

                    if (shouldDestroy) {
                        if (isInGuildTerritory) {
                            touchedGuilds.add(guild.getTag());
                            regenManager.recordBlock(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                                    block.getBlockData().getAsString());
                            if (block.getY() > RegenManager.Y_SPLIT) {
                                regenManager.scheduleAutoRegenAbove(Collections.singletonList(
                                        RegenManager.key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())));
                            }
                            regenManager.save();
                        }
                        block.setType(Material.AIR);
                    }
                }
    }

    private boolean isTnt(EntityType type) {
        String name = type.name();
        return name.equals("PRIMED_TNT") || name.equals("END_CRYSTAL")|| name.equals("TNT") || name.equals("MINECART_TNT") || name.equals("TNT_MINECART");
    }
}
