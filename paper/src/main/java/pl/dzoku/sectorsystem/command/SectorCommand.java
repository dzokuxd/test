package pl.dzoku.sectorsystem.command;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
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
            case "border" -> handleBorder(sender, args);
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
        sender.sendMessage(legacy.deserialize("§e/sector border <promien> §7- Ustaw border sektora"));
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
        plugin.getConfigManager().reload();
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

    // ── /sector border <promien> ──────────────────────────────────────────
    private void handleBorder(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(legacy.deserialize("§cUżycie: §f/sector border <promien>"));
            return;
        }

        int radius;
        try {
            radius = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(legacy.deserialize("§cPromień musi być liczbą, np. §f/sector border 1000"));
            return;
        }
        if (radius < 16) {
            sender.sendMessage(legacy.deserialize("§cMinimalny promień to 16."));
            return;
        }

        String currentSector = plugin.getConfigManager().getCurrentSector();
        FileConfiguration cfg = plugin.getConfig();
        String base = "sectors." + currentSector;

        // Y bierzemy z configu (jeśli jest) albo domyślne -64..320
        int minY = cfg.getInt(base + ".bounds.min-y", -64);
        int maxY = cfg.getInt(base + ".bounds.max-y", 320);

        // Zapis do config.yml
        cfg.set(base + ".bounded", true);
        cfg.set(base + ".bounds.min-x", -radius);
        cfg.set(base + ".bounds.max-x", radius);
        cfg.set(base + ".bounds.min-z", -radius);
        cfg.set(base + ".bounds.max-z", radius);
        cfg.set(base + ".bounds.min-y", minY);
        cfg.set(base + ".bounds.max-y", maxY);
        plugin.saveConfig();

        // Przeładuj ConfigManager, żeby SectorBorderListener widział nowe wartości
        plugin.getConfigManager().reload();

        // Ustaw worldborder od razu (bez restartu)
        for (World w : Bukkit.getWorlds()) {
            WorldBorder wb = w.getWorldBorder();
            wb.setCenter(0.0, 0.0);
            wb.setSize(radius * 2.0D);
            wb.setWarningDistance(5);
            wb.setDamageAmount(0.0);
            wb.setDamageBuffer(Double.MAX_VALUE);
        }

        sender.sendMessage(legacy.deserialize("§a§l[SECTOR] §aBorder sektora §e" + currentSector
                + " §austawiony na §e" + radius + " §abloków (X/Z ±" + radius + ", Y " + minY + ".." + maxY + ")."));
        sender.sendMessage(legacy.deserialize("§7Zapisano w §fconfig.yml §7+ worldborder ustawiony od razu."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("list", "info", "tp", "reload", "restart", "border");
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