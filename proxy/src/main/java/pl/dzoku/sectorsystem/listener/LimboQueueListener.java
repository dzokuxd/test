package pl.dzoku.sectorsystem.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import pl.dzoku.sectorsystem.RestartOrchestrator;
import pl.dzoku.sectorsystem.SectorProxyPlugin;
import pl.dzoku.sectorsystem.managers.AuthManager;
import java.util.Optional;

public class LimboQueueListener {
    private final SectorProxyPlugin plugin;

    public LimboQueueListener(SectorProxyPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();

        player.getCurrentServer().ifPresent(conn -> {
            String currentName = conn.getServerInfo().getName();
            if (!RestartOrchestrator.LIMBO.equalsIgnoreCase(currentName)) return;

            AuthManager.AuthSession session = AuthManager.getSession(player.getUsername());
            if (session == null || !session.isLoggedIn) return;  // czeka na /login albo /register

            // Deduplikacja: jeśli orchestrator już go zakolejkował, nic nie rób
            if (plugin.getOrchestrator() != null && plugin.getOrchestrator().isInQueue(player.getUniqueId())) {
                plugin.getLogger().fine(player.getUsername() + " na limbo, ale juz w kolejce orchestratora - skip");
                return;
            }

            String targetSector = resolveTargetSector(player, event);

            if (plugin.getOrchestrator() != null) {
                plugin.getOrchestrator().enqueuePlayer(player, targetSector);
                plugin.getLogger().info(player.getUsername() + " -> limbo (zautoryzowany) -> kolejka na sektor " + targetSector);
            }
        });
    }

    /**
     * Rozwiązuje docelowy sektor w kolejności:
     * 1. previousServer (serwer, z którego gracz przyszedł na limbo) — pomijamy jeśli to był limbo
     * 2. lastSector z bazy MySQL
     * 3. fallback: "guild"
     */
    private String resolveTargetSector(Player player, ServerPostConnectEvent event) {
        // 1. Poprzedni serwer (skąd gracz przyszedł na limbo)
        RegisteredServer prev = event.getPreviousServer();
        if (prev != null) {
            String prevName = prev.getServerInfo().getName();
            if (!RestartOrchestrator.LIMBO.equalsIgnoreCase(prevName)) {
                return prevName;
            }
        }

        // 2. Last sector z bazy MySQL
        try {
            var mysql = plugin.getMysql();
            if (mysql != null && mysql.isEnabled()) {
                Optional<String> lastOpt = mysql.getLastSector(player.getUniqueId());
                if (lastOpt.isPresent()) {
                    String last = lastOpt.get().trim();
                    if (!last.isEmpty()) return last;
                }
            }
        } catch (Exception ignored) {}

        // 3. Fallback
        return "guild";
    }
}