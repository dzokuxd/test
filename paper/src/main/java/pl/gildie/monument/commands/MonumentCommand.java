package pl.gildie.monument.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import pl.gildie.Const;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;
import pl.gildie.monument.GuildBonusManager;
import pl.gildie.monument.MonumentManager;
import pl.gildie.monument.PointsManager;
import pl.gildie.monument.gui.MonumentShopGui;
import pl.gildie.monument.util.MonumentMsg;

import java.util.List;

public final class MonumentCommand {
    private MonumentCommand() {}

    private static void sendHelp(CommandSender s, String cmd, String desc) {
        s.sendMessage(MonumentMsg.cmd(cmd).append(Component.text(" — " + desc, MonumentMsg.DESC)));
    }

    private static boolean isAdmin(CommandSourceStack s) {
        return s.getSender().hasPermission("gildie.admin");
    }

    public static void register(Plugin plugin, MonumentManager mm, GuildManager gm) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            LiteralCommandNode<CommandSourceStack> sklepNode = Commands.literal("sklep")
                    .requires(s -> s.getSender() instanceof Player)
                    .executes(ctx -> {
                        Player p = (Player) ctx.getSource().getSender();
                        Location c = mm.getCenterLocation();
                        if (c == null) { p.sendMessage(MonumentMsg.error("Monument nie ma srodka!")); return Command.SINGLE_SUCCESS; }
                        if (p.getWorld().equals(c.getWorld()) && p.getLocation().distanceSquared(c) <= (double) Const.MONUMENT_SHOP_RADIUS * Const.MONUMENT_SHOP_RADIUS) {
                            new MonumentShopGui().open(p);
                        } else p.sendMessage(MonumentMsg.error("Musisz byc przy monumencie!"));
                        return Command.SINGLE_SUCCESS;
                    }).build();

            LiteralCommandNode<CommandSourceStack> punktyNode = Commands.literal("punkty")
                    .requires(s -> s.getSender() instanceof Player)
                    .executes(ctx -> {
                        Player p = (Player) ctx.getSource().getSender();
                        p.sendMessage(MonumentMsg.desc("Twoje punkty monumentu: ").append(MonumentMsg.cmd(String.valueOf(PointsManager.getPoints(p.getUniqueId())))));
                        return Command.SINGLE_SUCCESS;
                    }).build();

            LiteralCommandNode<CommandSourceStack> efektyNode = Commands.literal("efekty")
                    .requires(s -> s.getSender() instanceof Player)
                    .executes(ctx -> {
                        Player p = (Player) ctx.getSource().getSender();
                        Guild g = gm.getGuildByPlayer(p.getUniqueId());
                        if (g == null) { p.sendMessage(MonumentMsg.error("Nie jestes w gildii!")); return Command.SINGLE_SUCCESS; }
                        p.sendMessage(MonumentMsg.desc("Efekty gildii " + g.getTag() + ": ").append(MonumentMsg.cmd(GuildBonusManager.describeActive(g.getTag()))));
                        return Command.SINGLE_SUCCESS;
                    }).build();

            LiteralCommandNode<CommandSourceStack> setCenter = Commands.literal("setcenter")
                    .requires(MonumentCommand::isAdmin)
                    .executes(ctx -> {
                        if (!(ctx.getSource().getSender() instanceof Player p)) return Command.SINGLE_SUCCESS;
                        mm.setCenterLocation(p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5));
                        p.sendMessage(MonumentMsg.cmd("Ustawiono srodkowy crystal!"));
                        return Command.SINGLE_SUCCESS;
                    }).build();

            LiteralCommandNode<CommandSourceStack> setCorner = Commands.literal("setcorner")
                    .requires(MonumentCommand::isAdmin)
                    .then(Commands.argument("id", IntegerArgumentType.integer(1, 4))
                            .executes(ctx -> {
                                if (!(ctx.getSource().getSender() instanceof Player p)) return Command.SINGLE_SUCCESS;
                                int id = IntegerArgumentType.getInteger(ctx, "id");
                                mm.setCornerLocation(id, p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5));
                                p.sendMessage(MonumentMsg.cmd("Ustawiono naroznik " + id + "!"));
                                return Command.SINGLE_SUCCESS;
                            })).build();

            // /monument start <center|corner|all>
            LiteralCommandNode<CommandSourceStack> startNode = Commands.literal("start")
                    .requires(MonumentCommand::isAdmin)
                    .then(Commands.argument("what", StringArgumentType.word())
                            .executes(ctx -> {
                                String what = StringArgumentType.getString(ctx, "what").toLowerCase();
                                CommandSender s = ctx.getSource().getSender();
                                switch (what) {
                                    case "center" -> { mm.startCenter(); s.sendMessage(MonumentMsg.cmd("Wlaczono srodkowy monument i wszystko co z nim zwiazane.")); }
                                    case "corner", "corners" -> { mm.startCorners(); s.sendMessage(MonumentMsg.cmd("Wlaczono narozne monumenty i wszystko co z nimi zwiazane.")); }
                                    case "all" -> { mm.startAll(); s.sendMessage(MonumentMsg.cmd("Wlaczono wszystkie monumenty.")); }
                                    default -> s.sendMessage(MonumentMsg.error("Uzycie: /monument start <center|corner|all>"));
                                }
                                return Command.SINGLE_SUCCESS;
                            })).build();

            // /monument stop <center|corner|all>
            LiteralCommandNode<CommandSourceStack> stopNode = Commands.literal("stop")
                    .requires(MonumentCommand::isAdmin)
                    .then(Commands.argument("what", StringArgumentType.word())
                            .executes(ctx -> {
                                String what = StringArgumentType.getString(ctx, "what").toLowerCase();
                                CommandSender s = ctx.getSource().getSender();
                                switch (what) {
                                    case "center" -> { mm.stopCenter(); s.sendMessage(MonumentMsg.error("Wylaczono srodkowy monument i wszystko co z nim zwiazane.")); }
                                    case "corner", "corners" -> { mm.stopCorners(); s.sendMessage(MonumentMsg.error("Wylaczono narozne monumenty i wszystko co z nimi zwiazane.")); }
                                    case "all" -> { mm.stopAll(); s.sendMessage(MonumentMsg.error("Wylaczono wszystkie monumenty.")); }
                                    default -> s.sendMessage(MonumentMsg.error("Uzycie: /monument stop <center|corner|all>"));
                                }
                                return Command.SINGLE_SUCCESS;
                            })).build();

            LiteralCommandNode<CommandSourceStack> root = Commands.literal("monument")
                    .executes(ctx -> {
                        CommandSender s = ctx.getSource().getSender();
                        s.sendMessage(Component.text("MonumentCrystals", TextColor.color(0xFFAA00)).append(Component.text(" — Komendy:", NamedTextColor.WHITE)));
                        sendHelp(s, "/monument sklep", "otwiera sklep monumentu");
                        sendHelp(s, "/monument punkty", "Twoje punkty");
                        sendHelp(s, "/monument efekty", "aktywne efekty gildii");
                        sendHelp(s, "/monument start <center|corner|all>", "wlacza monument (admin)");
                        sendHelp(s, "/monument stop <center|corner|all>", "wylacza monument (admin)");
                        sendHelp(s, "/monument setcenter / setcorner <1-4>", "ustawia lokalizacje (admin)");
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(sklepNode).then(punktyNode).then(efektyNode)
                    .then(setCenter).then(setCorner)
                    .then(startNode).then(stopNode)
                    .build();

            commands.register(plugin.getPluginMeta(), root, "Komendy monumentu", List.of("crystals", "mon"));
        });
    }
}
