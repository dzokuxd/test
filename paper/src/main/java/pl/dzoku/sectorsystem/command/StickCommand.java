package pl.dzoku.sectorsystem.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

import java.util.ArrayList;
import java.util.List;

public class StickCommand implements CommandExecutor {
    private final SectorSystemPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    public StickCommand(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cTylko gracze."));
            return true;
        }
        if (!player.hasPermission("sectorsystem.admin") && !player.hasPermission("sectorsystem.stick")) {
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cBrak uprawnień."));
            return true;
        }
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        String name = plugin.getConfig().getString("spawn-stick.name", "&e&lPatyk Teleportu na Spawn");
        meta.displayName(legacy.deserialize(name));
        List<String> loreCfg = plugin.getConfig().getStringList("spawn-stick.lore");
        if (!loreCfg.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreCfg) lore.add(legacy.deserialize(line));
            meta.lore(lore);
        }
        stick.setItemMeta(meta);
        player.getInventory().addItem(stick);
        player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aOtrzymałeś patyk teleportu na spawn."));
        return true;
    }
}