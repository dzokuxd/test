package pl.gildie.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ItemCost {
    private final Map<Material, Integer> costs;

    public ItemCost(Map<Material, Integer> costs) { this.costs = costs; }

    public static ItemCost fromConfig(ConfigurationSection section, Map<Material, Integer> defaults) {
        Map<Material, Integer> map = new LinkedHashMap<>(defaults);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    Material mat = Material.matchMaterial(key);
                    if (mat != null && mat.isItem()) {
                        int amount = section.getInt(key, 0);
                        if (amount > 0) map.put(mat, amount);
                        else map.remove(mat);
                    }
                } catch (Exception ignored) { }
            }
        }
        return new ItemCost(map);
    }

    public boolean has(Player player) {
        PlayerInventory inv = player.getInventory();
        for (Map.Entry<Material, Integer> e : costs.entrySet()) {
            if (count(inv, e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    public boolean take(Player player) {
        if (!has(player)) return false;
        PlayerInventory inv = player.getInventory();
        for (Map.Entry<Material, Integer> e : costs.entrySet()) remove(inv, e.getKey(), e.getValue());
        return true;
    }

    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Material, Integer> e : costs.entrySet()) lines.add("\u00a7e" + e.getValue() + "x \u00a7f" + pretty(e.getKey()));
        return lines;
    }

    public String describeInline() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<Material, Integer> e : costs.entrySet()) {
            if (!first) sb.append("\u00a77, ");
            first = false;
            sb.append("\u00a7e").append(e.getValue()).append("x \u00a7f").append(pretty(e.getKey()));
        }
        return sb.toString();
    }

    public boolean isEmpty() { return costs.isEmpty(); }

    private static int count(PlayerInventory inv, Material mat) {
        int n = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.getType() == mat) n += stack.getAmount();
        }
        return n;
    }

    private static void remove(PlayerInventory inv, Material mat, int amount) {
        int left = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != mat) continue;
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) inv.setItem(i, null);
            left -= take;
        }
    }

    private static String pretty(Material mat) { return mat.name().toLowerCase().replace('_', ' '); }
}
