package pl.sectorsystem.paper.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.sectorsystem.paper.SectorPaperPlugin;

public class SpawnCommand implements CommandExecutor {
    private final SectorPaperPlugin plugin;

    public SpawnCommand(SectorPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cTylko dla graczy.");
            return true;
        }

        if (!plugin.getSectorManager().isGuildSector()) {
            player.sendMessage("§cKomendę /spawn możesz użyć tylko na sektorze gildii!");
            return true;
        }

        if (plugin.getTransferManager().hasPending(player)) {
            player.sendMessage("§cMasz już aktywne odliczanie!");
            return true;
        }

        // Natychmiastowy transfer (lub z małym delayem jeśli chcesz)
        plugin.getTransferManager().startDelayedTransfer(player, "spawn", "COMMAND_SPAWN", 0);
        return true;
    }
}
