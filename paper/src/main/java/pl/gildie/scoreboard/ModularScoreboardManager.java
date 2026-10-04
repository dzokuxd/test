package pl.gildie.scoreboard;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModularScoreboardManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Scoreboard> playerBoards = new ConcurrentHashMap<>();
    private final List<ScoreboardModule> modules = new ArrayList<>();
    private final String title;
    private static final String[] HEX_COLORS = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"};

    public ModularScoreboardManager(JavaPlugin plugin, String title) {
        this.plugin = plugin;
        this.title = title;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> registerPlayer(event.getPlayer()), 10L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unregisterPlayer(event.getPlayer());
    }

    public void registerModule(ScoreboardModule module) {
        modules.add(module);
        modules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
    }

    public void registerPlayer(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        player.setScoreboard(board);
        playerBoards.put(player.getUniqueId(), board);
        updatePlayer(player);
    }

    public void unregisterPlayer(Player player) {
        playerBoards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    private void updatePlayer(Player player) {
        Scoreboard board = playerBoards.get(player.getUniqueId());
        if (board == null) {
            registerPlayer(player);
            board = playerBoards.get(player.getUniqueId());
        }
        if (board == null) return;

        // Usuń WSZYSTKIE stare entries
        Set<String> entries = new HashSet<>(board.getEntries());
        for (String entry : entries) {
            board.resetScores(entry);
        }

        Objective oldObj = board.getObjective("modular");
        if (oldObj != null) {
            try { oldObj.unregister(); } catch (Exception ignored) {}
        }

        Objective obj = board.registerNewObjective("modular", Criteria.DUMMY, Component.text(title));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> allLines = new ArrayList<>();
        for (ScoreboardModule module : modules) {
            if (module.isActive(player)) {
                List<String> moduleLines = module.getLines(player);
                if (!allLines.isEmpty() && !moduleLines.isEmpty()) {
                    allLines.add("§8§m-------------------");
                }
                allLines.addAll(moduleLines);
            }
        }

        if (allLines.isEmpty()) {
            allLines.add("§7Brak aktywnych modułów");
        }

        // Ogranicz do 15 linii
        if (allLines.size() > 15) {
            allLines = allLines.subList(0, 15);
        }

        // Ustaw linie - pierwsza (Monument) na górze (wysoki score), ostatnia (Ping) na dole (niski score)
        Set<String> usedEntries = new HashSet<>();
        int score = allLines.size() - 1;
        for (String line : allLines) {
            String entry = line;
            // Dodaj suffix tylko jeśli entry już istnieje (unikaj duplikatów)
            int suffixIndex = 0;
            while (usedEntries.contains(entry)) {
                entry = line + HEX_COLORS[suffixIndex % 16];
                suffixIndex++;
            }
            usedEntries.add(entry);
            obj.getScore(entry).setScore(score);
            score--;
        }
    }

    public void forceUpdate() {
        Bukkit.getScheduler().runTask(plugin, this::updateAll);
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            unregisterPlayer(player);
        }
        playerBoards.clear();
        modules.clear();
    }
}