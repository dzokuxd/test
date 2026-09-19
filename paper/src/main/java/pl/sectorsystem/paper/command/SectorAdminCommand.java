package pl.sectorsystem.paper.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.sectorsystem.common.SectorDefinition;
import pl.sectorsystem.paper.SectorPaperPlugin;

public class SectorAdminCommand implements CommandExecutor {
    private final SectorPaperPlugin plugin;

    public SectorAdminCommand(SectorPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sectorsystem.admin")) {
            sender.sendMessage("§cBrak uprawnień.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/sector list|info|tp|reload|stick");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "list" -> {
                sender.sendMessage("§6=== Sektory ===");
                for (SectorDefinition def : plugin.getSectorManager().getAllSectors()) {
                    boolean online = plugin.getRedis().isSectorOnline(def.getId());
                    int count = plugin.getRedis().getSectorPlayerCount(def.getId());
                    int max = plugin.getSectorManager().getMaxPlayers(def.getId());
                    sender.sendMessage("§e" + def.getId() + " §7(" + def.getType() + ") "
                            + (online ? "§aONLINE" : "§cOFFLINE")
                            + " §7" + count + (max > 0 ? "/" + max : ""));
                }
                sender.sendMessage("§7Global online: §a" + plugin.getRedis().getGlobalPlayerCount());
            }
            case "info" -> {
                sender.sendMessage("§6Sektor: §e" + plugin.getCurrentSectorId());
                SectorDefinition def = plugin.getSectorManager().getCurrentSector();
                if (def != null) {
                    sender.sendMessage("§7Typ: §f" + def.getType());
                    sender.sendMessage("§7Max: §f" + plugin.getSectorManager().getMaxPlayers(def.getId()));
                }
                sender.sendMessage("§7Online tu: §a" + Bukkit.getOnlinePlayers().size());
                sender.sendMessage("§7MySQL: §f" + (plugin.getMysql() != null && plugin.getMysql().isEnabled()));
            }
            case "tp" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUżycie: /sector tp <gracz> <sektor>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cGracz offline.");
                    return true;
                }
                String sector = args[2].toLowerCase();
                if (!plugin.getSectorManager().sectorExists(sector)) {
                    sender.sendMessage("§cNieznany sektor.");
                    return true;
                }
                plugin.getTransferManager().forceTransfer(target, sector, "ADMIN");
                sender.sendMessage("§aPrzeniesiono §e" + target.getName() + " §ana §e" + sector);
            }
            case "reload" -> {
                plugin.reloadPluginConfig();
                sender.sendMessage("§aConfig przeładowany.");
            }
            case "stick" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cTylko gracz.");
                    return true;
                }
                return new StickCommand(plugin).onCommand(sender, command, label, args);
            }
            default -> sender.sendMessage("§cNieznana podkomenda.");
        }
        return true;
    }
}
