package pl.gildie.scoreboard;
import org.bukkit.entity.Player;
import java.util.List;

public interface ScoreboardModule {
    String getName();
    int getPriority();
    boolean isActive(Player player);
    List<String> getLines(Player player);
}