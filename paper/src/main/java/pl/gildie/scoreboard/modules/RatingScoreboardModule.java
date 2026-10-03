package pl.gildie.scoreboard.modules;
import org.bukkit.entity.Player;
import pl.gildie.managers.RatingManager;
import pl.gildie.scoreboard.ScoreboardModule;
import java.util.ArrayList;
import java.util.List;

public class RatingScoreboardModule implements ScoreboardModule {
    private final RatingManager ratingManager;
    public RatingScoreboardModule(RatingManager ratingManager) { this.ratingManager = ratingManager; }
    @Override public String getName() { return "Rating"; }
    @Override public int getPriority() { return 90; }
    @Override public boolean isActive(Player player) { return ratingManager.isRatingActive(); }
    @Override public List<String> getLines(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("§6§lOcenianie");
        lines.add("§eAdministracja");
        List<RatingManager.GuildRating> adminTop = ratingManager.getTopAdminRatings(3);
        if (adminTop.isEmpty()) lines.add("§7Brak głosów");
        else for (int i = 0; i < adminTop.size(); i++) lines.add("§7Top" + (i + 1) + ": §f" + adminTop.get(i).guildTag + " §7- §a" + (int) adminTop.get(i).score);
        lines.add(" "); lines.add("§eGracze");
        List<RatingManager.GuildRating> playerTop = ratingManager.getTopPlayerRatings(3);
        if (playerTop.isEmpty()) lines.add("§7Brak głosów");
        else for (int i = 0; i < playerTop.size(); i++) lines.add("§7Top" + (i + 1) + ": §f" + playerTop.get(i).guildTag + " §7- §a" + String.format("%.2f", playerTop.get(i).score));
        return lines;
    }
}