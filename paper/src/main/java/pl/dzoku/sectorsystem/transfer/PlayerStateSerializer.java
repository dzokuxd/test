package pl.dzoku.sectorsystem.transfer;

import com.google.gson.Gson;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import pl.dzoku.sectorsystem.model.PlayerSnapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public class PlayerStateSerializer {
    private static final Gson gson = new Gson();
    private final SectorSystemPlugin plugin;

    public PlayerStateSerializer(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    // ── ZDJECIE STANU ──────────────────────────────────────────────────────
    public PlayerSnapshot takeSnapshot(Player player) {
        PlayerSnapshot s = new PlayerSnapshot();
        s.uuid = player.getUniqueId();
        s.timestamp = System.currentTimeMillis();

        ItemStack[] all = new ItemStack[41];
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < 36 && i < storage.length; i++) all[i] = storage[i];
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < 4; i++) all[36 + i] = (i < armor.length) ? armor[i] : null;
        all[40] = player.getInventory().getItemInOffHand();
        s.inventoryBase64 = toBase64(all);

        s.health = player.getHealth();
        s.maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() : 20.0;
        s.food = player.getFoodLevel();
        s.saturation = player.getSaturation();
        s.level = player.getLevel();
        s.exp = player.getExp();
        s.totalExp = player.getTotalExperience();

        for (PotionEffect ef : player.getActivePotionEffects()) {
            s.effects.add(new PlayerSnapshot.EffectEntry(
                    ef.getType().getKey().toString(), ef.getAmplifier(), ef.getDuration()));
        }
        return s;
    }

    public CompletableFuture<String> serializeAsync(PlayerSnapshot s) {
        return CompletableFuture.supplyAsync(() -> gson.toJson(s));
    }

    public CompletableFuture<PlayerSnapshot> deserializeAsync(String json) {
        return CompletableFuture.supplyAsync(() -> gson.fromJson(json, PlayerSnapshot.class));
    }

    // ── ZASTOSOWANIE STANU ─────────────────────────────────────────────────
    public void applySnapshot(Player player, PlayerSnapshot s) {
        if (s == null) return;

        // Ekwipunek
        if (s.inventoryBase64 != null && !s.inventoryBase64.isEmpty()) {
            ItemStack[] all = fromBase64(s.inventoryBase64);
            if (all != null && all.length >= 41) {
                player.getInventory().setContents(Arrays.copyOfRange(all, 0, 36));
                player.getInventory().setArmorContents(Arrays.copyOfRange(all, 36, 40));
                player.getInventory().setItemInOffHand(all[40]);
            }
        }

        // Statystyki
        player.setFoodLevel(s.food);
        player.setSaturation(s.saturation);
        double max = s.maxHealth > 0 ? s.maxHealth : 20.0;
        if (player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
            player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(max);
        }
        player.setHealth(Math.min(s.health, max));
        player.setLevel(s.level);
        player.setExp(s.exp);
        player.setTotalExperience(s.totalExp);

        // Efekty mikstur
        player.clearActivePotionEffects();
        for (PlayerSnapshot.EffectEntry e : s.effects) {
            PotionEffectType type = org.bukkit.Registry.EFFECT.get(
                    org.bukkit.NamespacedKey.fromString(e.type));
            if (type == null) {
                // fallback dla starszych nazw
                type = PotionEffectType.getByName(e.type);
            }
            if (type != null && e.duration > 0) {
                player.addPotionEffect(new PotionEffect(type, e.duration, e.amplifier));
            }
        }

        player.updateInventory();
    }

    // ── BASE64 ─────────────────────────────────────────────────────────────
    private String toBase64(ItemStack[] items) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
            oos.writeInt(items.length);
            for (ItemStack it : items) oos.writeObject(it);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().severe("Snapshot serialize fail: " + e.getMessage());
            return null;
        }
    }

    private ItemStack[] fromBase64(String b64) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(b64));
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bis)) {
            int len = ois.readInt();
            ItemStack[] items = new ItemStack[len];
            for (int i = 0; i < len; i++) items[i] = (ItemStack) ois.readObject();
            return items;
        } catch (Exception e) {
            plugin.getLogger().severe("Snapshot deserialize fail: " + e.getMessage());
            return null;
        }
    }
}
