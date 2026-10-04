package pl.gildie.scoreboard.modules;
import org.bukkit.entity.Player;
import pl.gildie.db.MonumentRepository;
import pl.gildie.managers.MonumentManager;
import pl.gildie.scoreboard.ScoreboardModule;
import java.util.ArrayList;
import java.util.List;

public class MonumentScoreboardModule implements ScoreboardModule {
    private final MonumentRepository repo;
    private final MonumentManager manager;
    public MonumentScoreboardModule(MonumentRepository repo, MonumentManager manager) {
        this.repo = repo; this.manager = manager;
    }
    @Override public String getName() { return "Monument"; }
    @Override public int getPriority() { return 100; }
    @Override public boolean isActive(Player player) { return manager.isCenterActive(); }
    @Override public List<String> getLines(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("§6§lMONUMENT");
        List<MonumentRepository.Hit> top = repo.getTopCenterHits(5);
        if (top.isEmpty()) lines.add("§7brak danych");
        else for (int i = 0; i < top.size(); i++) lines.add("§7" + (i + 1) + ". §f" + top.get(i).name + " §7- §a" + top.get(i).hits);
        lines.add(" "); lines.add("§ePozostalo:"); lines.add(manager.describeCenterHp());
        return lines;
    }
}