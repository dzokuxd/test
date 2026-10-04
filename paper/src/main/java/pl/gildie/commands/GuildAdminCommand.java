package pl.gildie.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import pl.gildie.GildieModule;
import pl.gildie.managers.RatingManager;
import pl.gildie.scoreboard.ModularScoreboardManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GuildAdminCommand implements CommandExecutor, TabCompleter {
    private final GildieModule module;
    private final RatingManager ratingManager;
    private final ModularScoreboardManager scoreboardManager;

    public GuildAdminCommand(GildieModule module, RatingManager ratingManager, ModularScoreboardManager scoreboardManager) {
        this.module = module;
        this.ratingManager = ratingManager;
        this.scoreboardManager = scoreboardManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gildie.admin")) {
            sender.sendMessage("§cBrak uprawnień!");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("ocen")) {
            if (args.length < 2) {
                sender.sendMessage("§cUżycie: /ga ocen on|off lub /ga ocen <tag> <ocena>");
                return true;
            }
            if (args[1].equalsIgnoreCase("on")) {
                ratingManager.setRatingActive(true);
                sender.sendMessage("§aSystem oceniania został §2włączony§a!");
                scoreboardManager.forceUpdate();
                return true;
            }
            if (args[1].equalsIgnoreCase("off")) {
                ratingManager.setRatingActive(false);
                sender.sendMessage("§cSystem oceniania został §4wyłączony§c!");
                scoreboardManager.forceUpdate();
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cUżycie: /ga ocen <tag> <ocena 1-5>");
                return true;
            }
            int rating;
            try {
                rating = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cOcena musi być liczbą!");
                return true;
            }

            if (ratingManager.adminRate(args[1], rating)) {
                sender.sendMessage("§aOceniono gildię §e" + args[1].toUpperCase() + " §ana §e" + rating + "§a!");
                scoreboardManager.forceUpdate();
            } else {
                sender.sendMessage("§cNie udało się ocenić gildii.");
            }
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8§m--------------------------------");
        sender.sendMessage("§6§lGildie Admin §7- komendy:");
        sender.sendMessage("§e/ga ocen on|off §7- włącz/wyłącz ocenianie");
        sender.sendMessage("§e/ga ocen <tag> <1-5> §7- oceń gildię");
        sender.sendMessage("§8§m--------------------------------");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        try {
            if (args.length == 1) {
                String in = args[0].toLowerCase();
                return Arrays.asList("ocen").stream()
                        .filter(s -> s.startsWith(in))
                        .collect(Collectors.toList());
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("ocen")) {
                List<String> opts = new ArrayList<>(Arrays.asList("on", "off"));

                // Bezpieczne pobieranie gildii
                if (module != null && module.getGuildManager() != null) {
                    List<?> guilds = Collections.singletonList(module.getGuildManager().getAll());
                    if (guilds != null) {
                        for (Object g : guilds) {
                            try {
                                String tag = (String) g.getClass().getMethod("getTag").invoke(g);
                                if (tag != null) {
                                    opts.add(tag.toLowerCase());
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                String in = args[1].toLowerCase();
                return opts.stream()
                        .filter(s -> s.startsWith(in))
                        .collect(Collectors.toList());
            }

            if (args.length == 3 && args[0].equalsIgnoreCase("ocen")) {
                String in = args[2].toLowerCase();
                return Arrays.asList("1", "2", "3", "4", "5").stream()
                        .filter(s -> s.startsWith(in))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            // W przypadku jakiegokolwiek błędu - zwróć pustą listę
            return new ArrayList<>();
        }

        return new ArrayList<>();
    }
}