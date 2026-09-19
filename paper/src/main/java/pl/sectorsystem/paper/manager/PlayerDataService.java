package pl.sectorsystem.paper.manager;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import pl.sectorsystem.common.PlayerSectorData;
import pl.sectorsystem.paper.SectorPaperPlugin;
import pl.sectorsystem.paper.util.ItemSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PlayerDataService {
    private final SectorPaperPlugin plugin;

    public PlayerDataService(SectorPaperPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Zapisuje pełny stan gracza do Redis.
     */
    public void savePlayer(Player player, String targetSectorId) {
        PlayerSectorData data = plugin.getRedis().getPlayerData(player.getUniqueId());
        if (data == null) {
            data = new PlayerSectorData(player.getUniqueId(), player.getName());
        }

        data.setUsername(player.getName());
        data.setLastSectorId(data.getCurrentSectorId());
        data.setCurrentSectorId(targetSectorId != null ? targetSectorId : plugin.getCurrentSectorId());

        // Statystyki
        data.setHealth(player.getHealth());
        data.setMaxHealth(player.getMaxHealth());
        data.setFoodLevel(player.getFoodLevel());
        data.setSaturation(player.getSaturation());
        data.setLevel(player.getLevel());
        data.setExp(player.getExp());
        data.setTotalExperience(player.getTotalExperience());
        data.setGameMode(player.getGameMode().name());
        data.setAllowFlight(player.getAllowFlight());
        data.setFlying(player.isFlying());
        data.setFireTicks(player.getFireTicks());
        data.setRemainingAir(player.getRemainingAir());

        // Pozycja
        data.setWorld(player.getWorld().getName());
        data.setX(player.getLocation().getX());
        data.setY(player.getLocation().getY());
        data.setZ(player.getLocation().getZ());
        data.setYaw(player.getLocation().getYaw());
        data.setPitch(player.getLocation().getPitch());

        // Inventory (zawartość + armor + offhand)
        PlayerInventory inv = player.getInventory();
        ItemStack[] fullInv = new ItemStack[41]; // 36 storage + 4 armor + 1 offhand
        System.arraycopy(inv.getStorageContents(), 0, fullInv, 0, 36);
        System.arraycopy(inv.getArmorContents(), 0, fullInv, 36, 4);
        fullInv[40] = inv.getItemInOffHand();
        data.setInventoryBase64(ItemSerializer.toBase64(fullInv));

        // Ender Chest
        data.setEnderChestBase64(ItemSerializer.toBase64(player.getEnderChest().getContents()));

        // Potion effects – prosta serializacja (nazwa:duration:amplifier:ambient:particles)
        data.setEffectsBase64(serializeEffects(player.getActivePotionEffects()));

        plugin.getRedis().savePlayerData(data);
    }

    /**
     * Wczytuje i aplikuje stan gracza z Redis.
     */
    public void loadAndApply(Player player) {
        PlayerSectorData data = plugin.getRedis().getPlayerData(player.getUniqueId());
        if (data == null) {
            // Pierwszy raz – zapisz aktualny stan
            savePlayer(player, plugin.getCurrentSectorId());
            return;
        }

        // Statystyki
        if (data.getMaxHealth() > 0) {
            player.setMaxHealth(data.getMaxHealth());
        }
        player.setHealth(Math.min(data.getHealth(), player.getMaxHealth()));
        player.setFoodLevel(data.getFoodLevel());
        player.setSaturation(data.getSaturation());
        player.setLevel(data.getLevel());
        player.setExp(data.getExp());
        player.setTotalExperience(data.getTotalExperience());

        try {
            player.setGameMode(GameMode.valueOf(data.getGameMode()));
        } catch (Exception ignored) {}

        player.setAllowFlight(data.isAllowFlight());
        player.setFlying(data.isFlying());
        player.setFireTicks(data.getFireTicks());
        player.setRemainingAir(data.getRemainingAir());

        // Inventory
        if (data.getInventoryBase64() != null) {
            ItemStack[] fullInv = ItemSerializer.fromBase64(data.getInventoryBase64());
            PlayerInventory inv = player.getInventory();
            inv.clear();

            if (fullInv.length >= 36) {
                ItemStack[] storage = new ItemStack[36];
                System.arraycopy(fullInv, 0, storage, 0, 36);
                inv.setStorageContents(storage);
            }
            if (fullInv.length >= 40) {
                ItemStack[] armor = new ItemStack[4];
                System.arraycopy(fullInv, 36, armor, 0, 4);
                inv.setArmorContents(armor);
            }
            if (fullInv.length >= 41) {
                inv.setItemInOffHand(fullInv[40]);
            }
        }

        // Ender Chest
        if (data.getEnderChestBase64() != null) {
            ItemStack[] ender = ItemSerializer.fromBase64(data.getEnderChestBase64());
            player.getEnderChest().setContents(ender);
        }

        // Effects
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        applyEffects(player, data.getEffectsBase64());

        // Zaktualizuj sektor
        data.setCurrentSectorId(plugin.getCurrentSectorId());
        plugin.getRedis().savePlayerData(data);
    }

    private String serializeEffects(Collection<PotionEffect> effects) {
        if (effects == null || effects.isEmpty()) return "";
        List<String> list = new ArrayList<>();
        for (PotionEffect effect : effects) {
            list.add(effect.getType().getKey().getKey() + ":" +
                    effect.getDuration() + ":" +
                    effect.getAmplifier() + ":" +
                    effect.isAmbient() + ":" +
                    effect.hasParticles());
        }
        return String.join(";", list);
    }

    private void applyEffects(Player player, String data) {
        if (data == null || data.isEmpty()) return;
        try {
            for (String part : data.split(";")) {
                String[] p = part.split(":");
                if (p.length < 5) continue;
                var type = org.bukkit.potion.PotionEffectType.getByKey(
                        org.bukkit.NamespacedKey.minecraft(p[0]));
                if (type == null) continue;
                int duration = Integer.parseInt(p[1]);
                int amplifier = Integer.parseInt(p[2]);
                boolean ambient = Boolean.parseBoolean(p[3]);
                boolean particles = Boolean.parseBoolean(p[4]);
                player.addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd wczytywania efektów: " + e.getMessage());
        }
    }
}
