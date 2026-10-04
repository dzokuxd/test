package pl.gildie.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import pl.gildie.managers.CowStackManager;
import pl.gildie.model.CowStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CowStackListener implements Listener {

    private final Plugin plugin;
    private final CowStackManager manager;
    private final Random random = new Random();

    /** Stacki w trybie miłości: repUUID -> czas nakarmienia */
    private final Map<UUID, Long> loveMode = new HashMap<>();
    private static final long LOVE_WINDOW_MS = 10_000L;
    private static final long BREED_COOLDOWN_MS = 240_000L;

    public CowStackListener(Plugin plugin, CowStackManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ── Filtr mobów + wyłączone AI ─────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }
        EntityType type = e.getEntityType();
        if (type != EntityType.COW && type != EntityType.ENDERMAN) {
            e.setCancelled(true);
            return;
        }
        (e.getEntity()).setAI(false);
    }

    // ── Nowa krowa -> merge do stacka lub nowy reprezentant ────
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCowSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() != EntityType.COW) return;
        manager.handleCow((Cow) e.getEntity());
    }

    // ── Wczytanie chunka = odbudowa stacków po restarcie ───────
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity ent : e.getChunk().getEntities()) {
                if (ent instanceof Cow cow) manager.handleCow(cow);
            }
        });
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!(e.getRightClicked() instanceof Cow cow)) return;

        CowStack stack = manager.getStackByRep(cow.getUniqueId());
        if (stack == null) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.WHEAT) {
            if (stack.getCount() > 1) {
                String msg = "§6[Krowa] §eStack: §fx" + stack.getCount();
                if (stack.isBreedCooldown()) {
                    msg += " §7(cooldown §e" + stack.getBreedCooldownSeconds() + "s§7)";
                }
                p.sendMessage(msg);
            }
            return;
        }
        e.setCancelled(true);
        if (stack.isBreedCooldown()) {
            p.sendMessage("§cStack x" + stack.getCount()
                    + " §codpoczywa po rozmnozeniu: §e" + stack.getBreedCooldownSeconds() + "s");
            return;
        }

        consumeWheat(p);

        UUID rep = stack.getRepUUID();
        long now = System.currentTimeMillis();
        Long fed = loveMode.get(rep);

        if (fed != null && now - fed <= LOVE_WINDOW_MS) {
            // ── Drugie karmienie = ROZMNOŻENIE: stack +1 + start cooldownu ──
            loveMode.remove(rep);
            stack.addCount(1);
            stack.startBreedCooldown(BREED_COOLDOWN_MS);
            cow.getWorld().spawnParticle(Particle.HEART, cow.getLocation().add(0.5, 1.5, 0.5), 6, 0.3, 0.3, 0.3);
        } else {
            // ── Pierwsze karmienie = love mode ──
            loveMode.put(rep, now);
            cow.getWorld().spawnParticle(Particle.HEART, cow.getLocation().add(0.5, 1.5, 0.5), 3, 0.2, 0.2, 0.2);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Long t = loveMode.get(rep);
                if (t != null && System.currentTimeMillis() - t >= LOVE_WINDOW_MS) loveMode.remove(rep);
            }, LOVE_WINDOW_MS / 50L);
        }
    }

    // ── Śmierć reprezentanta = śmierć całego stacka, loot xN ───
    @EventHandler(priority = EventPriority.HIGH)
    public void onCowDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Cow cow)) return;

        CowStack stack = manager.getStackByRep(cow.getUniqueId());
        if (stack == null) return;              // niezstackowana krowa = vanilla drop

        int n = stack.getCount();
        manager.removeStack(stack);             // stary stack + stary hologram precz

        if (n <= 1) return;                     // ostatnia krowa: normalny drop, koniec

        // ── Drop jak za JEDNĄ krowę ──
        e.getDrops().clear();
        e.getDrops().add(new ItemStack(Material.LEATHER, 1 + random.nextInt(3)));
        e.getDrops().add(new ItemStack(Material.BEEF, 1 + random.nextInt(3)));
        e.setDroppedExp(1 + random.nextInt(4));

        Location loc = cow.getLocation().clone();
        int remaining = n - 1;

        // ── Nowy reprezentant dla reszty stacka (tick później, po evencie śmierci) ──
        Bukkit.getScheduler().runTask(plugin, () -> {
            Cow rep = loc.getWorld().spawn(loc, Cow.class, c -> {
                c.setAI(false);
                c.setMetadata(CowStackManager.RESPAWN_META, new FixedMetadataValue(plugin, 1));
            });

            CowStack newStack = new CowStack(rep);
            if (remaining > 1) newStack.addCount(remaining - 1);  // konstruktor daje count=1
            manager.addStack(newStack);

            rep.removeMetadata(CowStackManager.RESPAWN_META, plugin);
        });
    }

    private void consumeWheat(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) {
            p.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
    }
}