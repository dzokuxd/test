package pl.gildie.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import pl.gildie.managers.CowStackManager;
import pl.gildie.model.CowStack;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CowStackListener implements Listener {

    private final Plugin plugin;
    private final CowStackManager stackManager;
    private final Random random = new Random();

    /** Stacki w "trybie milosci": StackUUID -> czas nakarmienia (ms) */
    private final Map<UUID, Long> loveMode = new HashMap<>();
    private static final long LOVE_WINDOW_MS = 10_000L; // 10 s na drugie karmienie

    public CowStackListener(Plugin plugin, CowStackManager stackManager) {
        this.plugin = plugin;
        this.stackManager = stackManager;
    }

    // =====================================================
    // SPAWN: tylko krowy i endermany + wylaczone AI (bez NMS)
    // =====================================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        EntityType type = event.getEntityType();

        // Blokada wszystkich innych mobow
        if (type != EntityType.COW && type != EntityType.ENDERMAN) {
            event.setCancelled(true);
            return;
        }

        // Calkowite wylaczenie AI - czyste API Bukkita, nie wymaga NMS
        ((LivingEntity) event.getEntity()).setAI(false);
    }

    // =====================================================
    // Dodanie krowy do stacka po spawnie (bez schedulera Folii)
    // =====================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCowSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.COW) return;
        stackManager.addCowToStack((Cow) event.getEntity());
    }

    // =====================================================
    // Wlasne rozmnażanie + info o stacku (PPM na krowie)
    // =====================================================
    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Cow cow)) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        int stackCount = stackManager.getStackCount(cow.getUniqueId());

        // PPM bez pszenicy = pokaz info o stacku
        if (hand.getType() != Material.WHEAT) {
            if (stackCount > 1) {
                player.sendMessage("§6[Krowa] §eStack: §fx" + stackCount);
            }
            return;
        }

        // Anuluj vanilla interakcje (krowa z NoAI i tak by zmarnowala pszenice)
        event.setCancelled(true);

        CowStack stack = stackManager.getStackForCow(cow.getUniqueId());
        if (stack == null) return;

        UUID stackId = stack.getStackId();
        long now = System.currentTimeMillis();
        Long fedAt = loveMode.get(stackId);

        if (fedAt != null && now - fedAt <= LOVE_WINDOW_MS) {
            // DRUGIE karmienie -> ROZMNOZENIE: stack +1
            loveMode.remove(stackId);
            stackManager.incrementStackCount(cow.getUniqueId());
            cow.getWorld().spawnParticle(Particle.HEART,
                    cow.getLocation().add(0.5, 1.5, 0.5), 6, 0.3, 0.3, 0.3);
        } else {
            // PIERWSZE karmienie -> tryb milosci
            loveMode.put(stackId, now);
            cow.getWorld().spawnParticle(Particle.HEART,
                    cow.getLocation().add(0.5, 1.5, 0.5), 3, 0.2, 0.2, 0.2);

            // Autoczyszczenie po oknie czasowym (Bukkit scheduler, nie Folia)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Long t = loveMode.get(stackId);
                if (t != null && System.currentTimeMillis() - t >= LOVE_WINDOW_MS) {
                    loveMode.remove(stackId);
                }
            }, LOVE_WINDOW_MS / 50L);
        }

        // Zuzycie pszenicy
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    // =====================================================
    // Smierc: usun ze stacka + drop za caly stack
    // =====================================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onCowDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.COW) return;

        Cow cow = (Cow) event.getEntity();
        int stackCount = stackManager.getStackCount(cow.getUniqueId());

        stackManager.removeCowFromStack(cow.getUniqueId());

        if (stackCount > 1) {
            event.getDrops().clear();
            event.getDrops().add(new ItemStack(Material.LEATHER,
                    stackCount * (1 + random.nextInt(3))));
            event.getDrops().add(new ItemStack(Material.BEEF,
                    stackCount * (1 + random.nextInt(3))));
            event.setDroppedExp(stackCount * (1 + random.nextInt(4)));
        }
    }
}