package pl.dzoku.sectorsystem.command;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

public class AfkCommand implements CommandExecutor {
    private final SectorSystemPlugin plugin;

    public AfkCommand(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cTylko dla graczy."));
            return true;
        }
        // Sprawdź czy gracz nie jest w trakcie transferu
        if (plugin.getTransferStateMachine().isInTransfer(player.getUniqueId())) {
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cMasz już aktywne odliczanie!"));
            return true;
        }
        // Opóźnienie 3 sekundy dla bezpieczeństwa
        player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eTeleport na AFK za 3 sekundy... Nie ruszaj się!"));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getTransferStateMachine().initiateTransfer(player, "afk");
            }
        }, 20L * 3);
        return true;
    }
}