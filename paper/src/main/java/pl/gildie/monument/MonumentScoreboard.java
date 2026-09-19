package pl.gildie.monument;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import pl.gildie.db.MonumentRepository;

import java.util.List;

public class MonumentScoreboard {
    private static MonumentRepository repo;
    private static MonumentManager manager;
    private static Scoreboard board;
    private static Objective obj;
    private static boolean showing = false;

    public static void init(JavaPlugin plugin, MonumentRepository r, MonumentManager m) {
        repo = r;
        manager = m;
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        Bukkit.getScheduler().runTaskTimer(plugin, MonumentScoreboard::update, 20L, 20L * 5);
        update();
    }

    private static void update() {
        // Srodkowy krysztaL NIE zyje -> schowaj scoreboard i wroc do main
        if (!manager.isCenterActive()) {
            if (showing) {
                Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getScoreboard().equals(main)) p.setScoreboard(main);
                }
                if (obj != null) {
                    try { obj.unregister(); } catch (Exception ignored) { }
                    obj = null;
                }
                showing = false;
            }
            return;
        }

        // Srodkowy krysztaL zyje -> buduj / odswiez sidebar
        if (obj != null) {
            try { obj.unregister(); } catch (Exception ignored) { }
        }
        obj = board.registerNewObjective("monument", Criteria.DUMMY,
                Component.text("MONUMENT", NamedTextColor.GOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = 15;
        obj.getScore("§eKorona:").setScore(score--);
        List<MonumentRepository.Hit> top = repo.getTopCenterHits(5);
        if (top.isEmpty()) {
            obj.getScore("§7brak danych").setScore(score--);
        } else {
            for (int i = 0; i < top.size(); i++) {
                MonumentRepository.Hit h = top.get(i);
                obj.getScore("§7" + (i + 1) + ". §f" + h.name + " §7- §a" + h.hits).setScore(score--);
            }
        }
        score--;
        obj.getScore(" ").setScore(score--);
        obj.getScore("§ePozostalo:").setScore(score--);
        obj.getScore(manager.describeCenterHp()).setScore(score--);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getScoreboard().equals(board)) p.setScoreboard(board);
        }
        showing = true;
    }
}
