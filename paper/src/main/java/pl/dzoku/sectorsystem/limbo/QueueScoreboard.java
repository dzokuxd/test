package pl.dzoku.sectorsystem.limbo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import pl.dzoku.sectorsystem.SectorSystemPlugin;
import pl.dzoku.sectorsystem.service.RedisService;

public class QueueScoreboard {
    private final SectorSystemPlugin plugin;
    private final RedisService redis;

    public QueueScoreboard(SectorSystemPlugin plugin, RedisService redis) {
        this.plugin = plugin;
        this.redis = redis;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refresh, 20L, 40L);
    }

    private void refresh() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            java.util.UUID uuid = player.getUniqueId();
            redis.getAsync("queue-sector:" + uuid).thenAccept(sector -> {
                if (sector == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()));
                    return;
                }
                redis.getAsync("queue-view:" + sector).thenAccept(viewJson -> {
                    redis.getAsync("sector-restarting:" + sector).thenAccept(restarting -> {
                        if (viewJson == null) return;
                        JsonObject view = JsonParser.parseString(viewJson).getAsJsonObject();
                        JsonArray names = view.getAsJsonArray("names");
                        int eta = view.get("eta").getAsInt();
                        Bukkit.getScheduler().runTask(plugin, () ->
                                render(player, sector, names, eta, restarting != null));
                    });
                });
            });
        }
    }

    private void render(Player player, String sector, JsonArray names, int eta, boolean restarting) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("queue", Criteria.DUMMY,
                Component.text("Kolejka: " + sector, NamedTextColor.GOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = 100;
        int myPos = 0;
        for (int i = 0; i < names.size() && i < 10; i++) {
            String name = names.get(i).getAsString();
            if (name.equals(player.getName())) myPos = i + 1;
            String line = "\u00a77" + (i + 1) + ". \u00a7f" + name + (name.equals(player.getName()) ? " \u00a7a<- Ty" : "");
            obj.getScore(line).setScore(score--);
        }
        obj.getScore(" ").setScore(score--);
        if (restarting) {
            obj.getScore("\u00a7eRestart: ~" + eta + "s").setScore(score--);
        } else {
            obj.getScore("\u00a7eWejscie: ~" + eta + "s").setScore(score--);
        }
        if (myPos > 0) {
            obj.getScore("\u00a77Twoja pozycja: \u00a7f" + myPos).setScore(score--);
        }
        player.setScoreboard(board);
    }
}
