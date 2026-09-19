package pl.sectorsystem.paper.util;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import pl.sectorsystem.common.PlayerSectorData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Collection;

/**
 * Serializacja / deserializacja pełnego stanu gracza do PlayerSectorData.
 */
public final class PlayerDataSerializer {

    private PlayerDataSerializer() {}

    public static PlayerSectorData capture(Player player) {
        PlayerSectorData data = new PlayerSectorData(player.getUniqueId(), player.getName());

        // Statystyki
        data.setHealth(player.getHealth());
        data.setMaxHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
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
        Location loc = player.getLocation();
        data.setWorld(loc.getWorld() != null ? loc.getWorld().getName() : "world");
        data.setX(loc.getX());
        data.setY(loc.getY());
        data.setZ(loc.getZ());
        data.setYaw(loc.getYaw());
        data.setPitch(loc.getPitch());

        // Ekwipunek (zawartość + armor + offhand)
        ItemStack[] contents = player.getInventory().getContents();
        data.setInventoryBase64(itemStackArrayToBase64(contents));

        // Enderchest
        data.setEnderChestBase64(itemStackArrayToBase64(player.getEnderChest().getContents()));

        // Efekty
        data.setEffectsBase64(potionEffectsToBase64(player.getActivePotionEffects()));

        return data;
    }

    public static void apply(Player player, PlayerSectorData data) {
        if (data == null) return;

        // Statystyki
        if (data.getMaxHealth() > 0) {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(data.getMaxHealth());
        }
        player.setHealth(Math.min(data.getHealth(), player.getAttribute(Attribute.MAX_HEALTH).getValue()));
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

        // Ekwipunek
        if (data.getInventoryBase64() != null) {
            ItemStack[] items = itemStackArrayFromBase64(data.getInventoryBase64());
            if (items != null) {
                player.getInventory().setContents(items);
            }
        }

        // Enderchest
        if (data.getEnderChestBase64() != null) {
            ItemStack[] ender = itemStackArrayFromBase64(data.getEnderChestBase64());
            if (ender != null) {
                player.getEnderChest().setContents(ender);
            }
        }

        // Efekty
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        if (data.getEffectsBase64() != null) {
            Collection<PotionEffect> effects = potionEffectsFromBase64(data.getEffectsBase64());
            if (effects != null) {
                player.addPotionEffects(effects);
            }
        }

        player.updateInventory();
    }

    // ---------- Serializacja ItemStack[] ----------

    public static String itemStackArrayToBase64(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ItemStack[] itemStackArrayFromBase64(String base64) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            dataInput.close();
            return items;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ---------- Serializacja PotionEffect ----------

    public static String potionEffectsToBase64(Collection<PotionEffect> effects) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(effects.size());
            for (PotionEffect effect : effects) {
                dataOutput.writeObject(effect);
            }
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Collection<PotionEffect> potionEffectsFromBase64(String base64) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int size = dataInput.readInt();
            java.util.List<PotionEffect> effects = new java.util.ArrayList<>();
            for (int i = 0; i < size; i++) {
                effects.add((PotionEffect) dataInput.readObject());
            }
            dataInput.close();
            return effects;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
