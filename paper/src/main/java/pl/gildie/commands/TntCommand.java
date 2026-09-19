package pl.gildie.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import pl.gildie.war.TntManager;

public class TntCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gildie.admin")) {
            sender.sendMessage("§cBrak uprawnien.");
            return true;
        }
        boolean now;
        if (args.length >= 1 && args[0].equalsIgnoreCase("on")) now = true;
        else if (args.length >= 1 && args[0].equalsIgnoreCase("off")) now = false;
        else now = !TntManager.isTntEnabled();
        TntManager.setEnabled(now);
        sender.sendMessage("§eTNT force: " + (now ? "§awlaczone" : "§cwylaczone"));
        return true;
    }
}
