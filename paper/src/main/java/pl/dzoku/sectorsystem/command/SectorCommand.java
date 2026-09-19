package pl.dzoku.sectorsystem.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

public class SectorCommand implements CommandExecutor {
    private final SectorSystemPlugin plugin;

    public SectorCommand(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko dla graczy");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("\u00a7eUzycie: /sector <sektor|list|info|restart>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> {
                player.sendMessage("\u00a76Sektory:");
                plugin.getConfigManager().getAllSectors().forEach((id, sc) ->
                        player.sendMessage("\u00a77- \u00a7a" + id + " \u00a78(" + sc.getDisplayName() + ")"));
            }
            case "info" -> player.sendMessage("\u00a76Aktualny sektor: \u00a7a" + plugin.getConfigManager().getCurrentSector());
            case "restart" -> {
                if (!player.hasPermission("sectorsystem.admin")) {
                    player.sendMessage("\u00a7cBrak uprawnien");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage("\u00a7eUzycie: /sector restart <sektor> <sekundy>");
                    return true;
                }
                String target = args[1].toLowerCase();
                int seconds;
                try {
                    seconds = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    player.sendMessage("\u00a7cCzas musi byc liczba sekund");
                    return true;
                }
                plugin.getRestartManager().requestRestart(target, seconds);
                player.sendMessage("\u00a7aZaplanowano restart sektora " + target + " za " + seconds + "s");
            }
            default -> {
                String targetSector = args[0].toLowerCase();
                if (plugin.getConfigManager().getSectorConfig(targetSector) != null) {
                    plugin.getTransferStateMachine().initiateTransfer(player, targetSector);
                } else {
                    player.sendMessage("\u00a7cNieznany sektor: " + targetSector + ". Uzyj /sector list");
                }
            }
        }
        return true;
    }
}
