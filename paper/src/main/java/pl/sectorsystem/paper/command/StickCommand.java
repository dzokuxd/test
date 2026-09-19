package pl.sectorsystem.paper.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.sectorsystem.paper.SectorPaperPlugin;

import java.util.ArrayList;
import java.util.List;

public class StickCommand implements CommandExecutor {
    private final SectorPaperPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public StickCommand(SectorPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cTylko gracze.");
            return true;
        }
        if (!player.hasPermission("sectorsystem.admin") && !player.hasPermission("sectorsystem.stick")) {
            player.sendMessage("§cBrak uprawnień.");
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
        player.sendMessage("§aOtrzymałeś patyk teleportu na spawn.");
        return true;
    }
}
