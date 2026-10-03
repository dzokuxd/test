package pl.dzoku.sectorsystem;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.sectorsystem.common.mysql.MySQLService;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class QueueManager {
    private final SectorProxyPlugin plugin;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<UUID>> waitingQueues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledTask> queueCheckTasks = new ConcurrentHashMap<>();

    public QueueManager(SectorProxyPlugin plugin) {
        this.plugin = plugin;
    }

    public void enqueueAfterAuth(Player player) {
        UUID uuid = player.getUniqueId();
        MySQLService mysql = plugin.getMysql();
        String targetSector = "guild";

        if (mysql != null && mysql.isEnabled()) {
            Optional<String> lastSectorOpt = mysql.getLastSector(uuid);
            if (lastSectorOpt.isPresent() && !lastSectorOpt.get().trim().isEmpty()) {
                targetSector = lastSectorOpt.get().trim();
            }
        }

        plugin.getLogger().info("Gracz " + player.getUsername() + " po autoryzacji kierowany do: " + targetSector);

        // Sprawdź czy sektor istnieje w Velocity
        Optional<RegisteredServer> serverOpt = plugin.getServer().getServer(targetSector);
        if (serverOpt.isEmpty()) {
            player.sendMessage(Component.text("§cBłąd: Sektor §e'" + targetSector + "' §cnie istnieje!", NamedTextColor.RED));
            redirectToLimbo(player, targetSector);
            return;
        }

        // Sprawdź czy sektor jest online
        if (!plugin.getHealthChecker().isSectorOnline(targetSector)) {
            player.sendMessage(Component.text("§cSektor §e'" + targetSector + "' §cjest offline!", NamedTextColor.RED));
            redirectToLimbo(player, targetSector);
            return;
        }

        // Sprawdź sloty
        int maxSlots = getSectorMaxSlots(targetSector);
        int online = getSectorOnlineCount(targetSector);

        if (maxSlots > 0 && online >= maxSlots) {
            player.sendMessage(Component.text("§c§l[QUEUE] §cSektor §e" + targetSector + " §cjest pełny! (" + online + "/" + maxSlots + ")", NamedTextColor.RED));
            addToWaitingQueue(player, targetSector);
            return;
        }

        player.sendMessage(Component.text("§aŁączenie z sektorem: §e" + targetSector + " §a(" + online + "/" + (maxSlots > 0 ? maxSlots : "∞") + ")", NamedTextColor.GREEN));
        if (plugin.getOrchestrator() != null) {
            plugin.getOrchestrator().enqueuePlayer(player, targetSector);
        } else {
            player.sendMessage(Component.text("Błąd: System kolejkowania jest niedostępny.", NamedTextColor.RED));
        }
    }

    private void redirectToLimbo(Player player, String targetSector) {
        // ← NOWE: dodaj gracza do kolejki ORCHESTRATORA, żeby tick() go wyciągnął gdy sektor wróci
        if (plugin.getOrchestrator() != null) {
            plugin.getOrchestrator().enqueuePlayer(player, targetSector);
        }
        Optional<RegisteredServer> limbo = plugin.getServer().getServer(RestartOrchestrator.LIMBO);
        limbo.ifPresent(server -> player.createConnectionRequest(server).connect());
    }

    private int getSectorMaxSlots(String sector) {
        MySQLService mysql = plugin.getMysql();
        if (mysql != null && mysql.isEnabled()) {
            try {
                int slots = mysql.getSectorSlots(sector);
                if (slots > 0) return slots;
            } catch (Exception e) {
                // Tabela nie istnieje
            }
        }
        return 500;
    }

    private int getSectorOnlineCount(String sector) {
        Optional<RegisteredServer> serverOpt = plugin.getServer().getServer(sector);
        return serverOpt.map(s -> s.getPlayersConnected().size()).orElse(0);
    }

    public void addToWaitingQueue(Player player, String sector) {
        waitingQueues.computeIfAbsent(sector, k -> new ConcurrentLinkedQueue<>()).add(player.getUniqueId());
        int pos = getPositionInQueue(player.getUniqueId(), sector);
        player.sendMessage(Component.text("§7Zostałeś dodany do kolejki. Pozycja: §a#" + pos, NamedTextColor.YELLOW));

        queueCheckTasks.computeIfAbsent(sector, s ->
                plugin.getServer().getScheduler()
                        .buildTask(plugin, () -> checkWaitingQueue(s))
                        .repeat(10, TimeUnit.SECONDS)
                        .schedule()
        );
    }

    private int getPositionInQueue(UUID uuid, String sector) {
        ConcurrentLinkedQueue<UUID> queue = waitingQueues.get(sector);
        if (queue == null) return 1;
        int pos = 1;
        for (UUID id : queue) {
            if (id.equals(uuid)) return pos;
            pos++;
        }
        return pos;
    }

    private void checkWaitingQueue(String sector) {
        ConcurrentLinkedQueue<UUID> queue = waitingQueues.get(sector);
        if (queue == null || queue.isEmpty()) {
            ScheduledTask task = queueCheckTasks.remove(sector);
            if (task != null) task.cancel();
            return;
        }

        // Sprawdź czy sektor jest online
        if (!plugin.getHealthChecker().isSectorOnline(sector)) {
            return;
        }

        int maxSlots = getSectorMaxSlots(sector);
        int online = getSectorOnlineCount(sector);
        int freeSlots = maxSlots > 0 ? maxSlots - online : Integer.MAX_VALUE;

        int released = 0;
        while (released < freeSlots && !queue.isEmpty()) {
            UUID nextUuid = queue.poll();
            Player next = plugin.getServer().getPlayer(nextUuid).orElse(null);
            if (next != null && next.isActive()) {
                released++;
                next.sendMessage(Component.text("§a§l[QUEUE] §aJest miejsce! Łączenie z sektorem: §e" + sector, NamedTextColor.GREEN));
                if (plugin.getOrchestrator() != null) {
                    plugin.getOrchestrator().enqueuePlayer(next, sector);
                }
            }
        }

        updateQueuePositions(sector);
    }

    private void updateQueuePositions(String sector) {
        ConcurrentLinkedQueue<UUID> queue = waitingQueues.get(sector);
        if (queue == null) return;
        int pos = 1;
        for (UUID uuid : queue) {
            Player p = plugin.getServer().getPlayer(uuid).orElse(null);
            if (p != null && p.isActive()) {
                p.sendActionBar(Component.text("§7Kolejka do §e" + sector + "§7: pozycja §a#" + pos));
            }
            pos++;
        }
    }

    public void removeFromQueue(UUID uuid) {
        for (ConcurrentLinkedQueue<UUID> queue : waitingQueues.values()) {
            queue.remove(uuid);
        }
    }
}