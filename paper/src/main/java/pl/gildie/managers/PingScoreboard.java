package pl.gildie.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import pl.gildie.Const;
import pl.gildie.model.Guild;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PingScoreboard {
    private static Scoreboard board;
    private static Objective obj;
    private static long activeUntil = 0;
    private static final Set<UUID> viewers = new HashSet<>();
    private static String lineName = "", lineTag = "", lineWorld = "", lineCoords = "";

    public static void init(JavaPlugin plugin) {
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        Bukkit.getScheduler().runTaskTimer(plugin, PingScoreboard::tick, 20L, 20L);
    }

    public static void ping(Player player, Guild guild) {
        lineName = player.getName();
        lineTag = guild.getTag();
        lineWorld = player.getWorld().getName();
        lineCoords = player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ();
        activeUntil = System.currentTimeMillis() + Const.PING_SCOREBOARD_SECONDS * 1000L;
        viewers.clear();
        for (UUID id : guild.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) viewers.add(id);
        }
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        if (now < activeUntil) {
            rebuild((activeUntil - now) / 1000);
            for (UUID id : viewers) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline() && !p.getScoreboard().equals(board)) p.setScoreboard(board);
            }
        } else if (!viewers.isEmpty()) {
            Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
            for (UUID id : viewers) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline() && p.getScoreboard().equals(board)) p.setScoreboard(main);
            }
            viewers.clear();
            if (obj != null) { try { obj.unregister(); } catch (Exception ignored) { } obj = null; }
        }
    }

    private static void rebuild(long sec) {
        if (obj != null) { try { obj.unregister(); } catch (Exception ignored) { } }
        obj = board.registerNewObjective("ping", Criteria.DUMMY, Component.text("§6§l⚠ PING POMOCY", NamedTextColor.GOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        int s = 15;
        obj.getScore("§8§m------------------").setScore(s--);
        obj.getScore("§7Gracz: §f" + lineName).setScore(s--);
        obj.getScore("§7Gildia: §e" + lineTag).setScore(s--);
        obj.getScore("§7Swiat: §f" + lineWorld).setScore(s--);
        obj.getScore("§7Kordy: §f" + lineCoords).setScore(s--);
        obj.getScore("§8§m------------------").setScore(s--);
        obj.getScore("§cWygasa za: §e" + sec + "s").setScore(s--);
    }
}
