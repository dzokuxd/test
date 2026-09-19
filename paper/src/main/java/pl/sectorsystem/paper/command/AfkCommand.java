package pl.sectorsystem.paper.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.sectorsystem.paper.SectorPaperPlugin;

public class AfkCommand implements CommandExecutor {
    private final SectorPaperPlugin plugin;

    public AfkCommand(SectorPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cTylko dla graczy.");
            return true;
        }

        // /afk dostępne z każdego sektora (możesz ograniczyć)
        if (plugin.getTransferManager().hasPending(player)) {
            player.sendMessage("§cMasz już aktywne odliczanie!");
            return true;
        }

        plugin.getTransferManager().startDelayedTransfer(player, "afk", "COMMAND_AFK", 3); // 3s delay dla bezpieczeństwa
        return true;
    }
}
