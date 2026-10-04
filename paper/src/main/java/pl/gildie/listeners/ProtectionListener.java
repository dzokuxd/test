package pl.gildie.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import pl.gildie.Const;
import pl.gildie.db.GuildRepository;
import pl.gildie.managers.BuildLockManager;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectionListener implements Listener {
    private final GuildManager guildManager;
    private final BuildLockManager buildLock;
    private final GuildRepository repo;
    private final Map<String, Integer> dispenserHits = new ConcurrentHashMap<>();
    private final Random random = new Random();

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
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        // ── DROP WHEAT: działa ZAWSZE gdy motyka + trawa (niezależnie od terenu) ──
        if (isGrass(b) && isHoe(hand.getType())) {
            // Dla TALL_GRASS tylko dolna część dropi
            if (b.getType() == Material.TALL_GRASS) {
                BlockData data = b.getBlockData();
                if (data instanceof Bisected bisected) {
                    if (bisected.getHalf() == Bisected.Half.TOP) {
                        return;
                    }
                }
            }

            // Anuluj vanilla drop i dropnij wheat
            event.setDropItems(false);

            int fortuneLevel = hand.getEnchantmentLevel(Enchantment.FORTUNE);
            int wheatAmount = 1 + random.nextInt(fortuneLevel + 1);

            b.getWorld().dropItemNaturally(
                    b.getLocation(),
                    new ItemStack(Material.WHEAT, wheatAmount)
            );

            if (random.nextBoolean()) {
                int seedsAmount = 1 + random.nextInt(fortuneLevel + 1);
                b.getWorld().dropItemNaturally(
                        b.getLocation(),
                        new ItemStack(Material.WHEAT_SEEDS, seedsAmount)
                );
            }
        }

        // ── OCHRONA RAID BASE ──
        Guild raid = guildManager.getRaidBaseOwnerAt(b.getLocation());
        if (raid != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie mozesz niszczyć na bazie wypadowej gildii §e" + raid.getTag() + "§c!");
            return;
        }

        // ── POZA TERENEM GILDII ──
        Guild guild = guildManager.getGuildAt(b.getLocation());
        if (guild == null) {
            if (b.getType() == Material.DISPENSER) {
                String owner = repo.getDispenserClaim(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
                Guild breaker = guildManager.getGuildByPlayer(event.getPlayer().getUniqueId());
                String breakerTag = breaker != null ? breaker.getTag() : null;
                if (owner != null && owner.equals(breakerTag)) {
                    repo.removeDispenserClaim(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
                    dispenserHits.remove(key(b));
                    return;
                }
                String k = key(b);
                int hits = dispenserHits.merge(k, 1, Integer::sum);
                if (hits < Const.DISPENSER_HITS_REQUIRED) {
                    event.setCancelled(true);
                    sendDispenserTitle(event.getPlayer(), hits, Const.DISPENSER_HITS_REQUIRED);
                    return;
                }
                dispenserHits.remove(k);
                repo.removeDispenserClaim(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            }
            return;
        }

        // ── NA TERENIE GILDII ──
        if (guild.isEggBlock(b.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (!guild.isMember(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie mozesz niszczyć na terenie gildii §e" + guild.getTag() + "§c!");
        }
    }
    private void sendDispenserTitle(Player player, int current, int required) {
        player.sendTitle(
                "",
                "§7(§F" + current + "§7/§c" + required+"§7)",
                5, 40, 10
        );
    }
    private boolean isGrass(Block b) {
        return b.getType() == Material.SHORT_GRASS || b.getType() == Material.TALL_GRASS;
    }
    private boolean isHoe(Material m) {
        return m == Material.WOODEN_HOE ||
                m == Material.STONE_HOE ||
                m == Material.IRON_HOE ||
                m == Material.GOLDEN_HOE ||
                m == Material.DIAMOND_HOE ||
                m == Material.NETHERITE_HOE;
    }
}
