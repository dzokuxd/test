package pl.sectorsystem.paper.scoreboard;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import pl.sectorsystem.paper.SectorPaperPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SectorScoreboard {
    private final SectorPaperPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public SectorScoreboard(SectorPaperPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 40L, 40L);
    }

    public void create(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.registerNewObjective("sector", Criteria.DUMMY, Component.text("§a§lSectorSystem"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        setLine(obj, "§7", 8);
        setLine(obj, "§fSektor: §e" + plugin.getCurrentSectorId(), 7);
        setLine(obj, "§8", 6);
        setLine(obj, "§fOnline (ten): §a0", 5);
        setLine(obj, "§fOnline (all): §a0", 4);
        setLine(obj, "§9", 3);
        setLine(obj, "§7sectorsystem", 2);
        setLine(obj, "§0", 1);
        player.setScoreboard(board);
        boards.put(player.getUniqueId(), board);
    }

    public void remove(Player player) {
        boards.remove(player.getUniqueId());
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) player.setScoreboard(manager.getMainScoreboard());
    }

    private void updateAll() {
        int local = Bukkit.getOnlinePlayers().size();
        try {
            plugin.getRedis().setSectorPlayerCount(plugin.getCurrentSectorId(), local);
        } catch (Exception ignored) {}
        int global = local;
        try {
            global = plugin.getRedis().getGlobalPlayerCount();
            if (global < local) global = local;
        } catch (Exception ignored) {}

        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = boards.get(player.getUniqueId());
            if (board == null) {
                create(player);
                board = boards.get(player.getUniqueId());
            }
            if (board == null) continue;
            Objective obj = board.getObjective("sector");
            if (obj == null) continue;
            setLine(obj, "§fSektor: §e" + plugin.getCurrentSectorId(), 7);
            setLine(obj, "§fOnline (ten): §a" + local, 5);
            setLine(obj, "§fOnline (all): §a" + global, 4);
        }
    }

    private void setLine(Objective obj, String text, int score) {
        Scoreboard board = obj.getScoreboard();
        if (board == null) return;
        for (String entry : new java.util.HashSet<>(board.getEntries())) {
            if (obj.getScore(entry).getScore() == score) board.resetScores(entry);
        }
        String entry = text.length() > 40 ? text.substring(0, 40) : text;
        obj.getScore(entry).setScore(score);
    }
}
