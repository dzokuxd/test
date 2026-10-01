package pl.dzoku.sectorsystem.command;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SectorCommand implements CommandExecutor, TabCompleter {
    private final SectorSystemPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    // Domyślne sektory (można rozszerzyć o konfigurację)
    private static final List<String> SECTORS = Arrays.asList("guild", "spawn", "afk");

    public SectorCommand(SectorSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sectorsystem.admin")) {
            sender.sendMessage(legacy.deserialize("§cBrak uprawnień."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender);
            case "tp" -> handleTp(sender, args);
            case "reload" -> handleReload(sender);
            case "restart" -> handleRestart(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(legacy.deserialize("§6=== SectorSystem ==="));
        sender.sendMessage(legacy.deserialize("§e/sector list §7- Lista sektorów"));
        sender.sendMessage(legacy.deserialize("§e/sector info §7- Info o aktualnym sektorze"));
        sender.sendMessage(legacy.deserialize("§e/sector tp <gracz> <sektor> §7- Force transfer"));
        sender.sendMessage(legacy.deserialize("§e/sector reload §7- Przeładuj config"));
        sender.sendMessage(legacy.deserialize("§e/sector restart <sektor> <sekundy> §7- Zaplanuj restart sektora"));
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(legacy.deserialize("§6=== Sektory ==="));
        for (String sectorId : SECTORS) {
            boolean online = plugin.getRedisService().isSectorOnline(sectorId);
            int count = plugin.getRedisService().getSectorPlayerCount(sectorId);
            sender.sendMessage(legacy.deserialize("§e" + sectorId + " §7"
                    + (online ? "§aONLINE" : "§cOFFLINE")
                    + " §7" + count + " graczy"));
        }
        sender.sendMessage(legacy.deserialize("§7Global online: §a" + plugin.getRedisService().getGlobalPlayerCount()));
    }

    private void handleInfo(CommandSender sender) {
        String currentSector = plugin.getConfigManager().getCurrentSector();
        sender.sendMessage(legacy.deserialize("§6Sektor: §e" + currentSector));
        sender.sendMessage(legacy.deserialize("§7Online tu: §a" + Bukkit.getOnlinePlayers().size()));
    }

    private void handleTp(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(legacy.deserialize("§cUżycie: /sector tp <gracz> <sektor>"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(legacy.deserialize("§cGracz offline."));
            return;
        }
        String sector = args[2].toLowerCase();
        if (!SECTORS.contains(sector)) {
            sender.sendMessage(legacy.deserialize("§cNieznany sektor. Dostępne: " + String.join(", ", SECTORS)));
            return;
        }
        plugin.getTransferStateMachine().initiateTransfer(target, sector);
        sender.sendMessage(legacy.deserialize("§aPrzeniesiono §e" + target.getName() + " §ana §e" + sector));
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(legacy.deserialize("§aConfig przeładowany."));
    }

    private void handleRestart(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(legacy.deserialize("§eUżycie: /sector restart <sektor> <sekundy>"));
            return;
        }
        String target = args[1].toLowerCase();
        if (!SECTORS.contains(target)) {
            sender.sendMessage(legacy.deserialize("§cNieznany sektor. Dostępne: " + String.join(", ", SECTORS)));
            return;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(legacy.deserialize("§cCzas musi być liczbą sekund"));
            return;
        }
        if (seconds < 1 || seconds > 3600) {
            sender.sendMessage(legacy.deserialize("§cCzas musi być między 1 a 3600 sekund"));
            return;
        }
        plugin.getRestartManager().requestRestart(target, seconds);
        sender.sendMessage(legacy.deserialize("§aZaplanowano restart sektora §e" + target + " §aza §e" + seconds + "s"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("list", "info", "tp", "reload", "restart");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("restart"))) {
            return SECTORS.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("tp")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
        }
        return new ArrayList<>();
    }
}