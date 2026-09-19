package pl.sectorsystem.paper.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import pl.sectorsystem.common.PlayerSectorData;
import pl.sectorsystem.paper.SectorPaperPlugin;
import pl.sectorsystem.paper.util.PlayerDataSerializer;

public class JoinListener implements Listener {
    private final SectorPaperPlugin plugin;

    public JoinListener(SectorPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.getScoreboard() != null) {
            plugin.getScoreboard().create(player);
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerSectorData data = null;
            try {
                data = plugin.getRedis().getPlayerData(player.getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().warning("Redis getPlayerData failed: " + e.getMessage());
            }

            final PlayerSectorData finalData = data;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                if (finalData != null) {
                    String expected = finalData.getCurrentSectorId();
                    if (expected != null && !expected.equals(plugin.getCurrentSectorId())) {
                        plugin.getLogger().warning("Gracz " + player.getName() + " na złym sektorze (oczekiwano " + expected + ")");
                    }
                    PlayerDataSerializer.apply(player, finalData);
                    player.sendMessage("§aDane zsynchronizowane.");

                    // UNLOCK po udanym wejściu na sektor docelowy
                    plugin.getTransferManager().unlockAfterJoin(
                            player.getUniqueId(),
                            finalData.getLastSectorId()
                    );
                } else {
                    PlayerSectorData newData = PlayerDataSerializer.capture(player);
                    newData.setCurrentSectorId(plugin.getCurrentSectorId());
                    try {
                        plugin.getRedis().savePlayerData(newData);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Redis save failed: " + e.getMessage());
                    }
                }

                if (plugin.getMysql() != null && plugin.getMysql().isEnabled()) {
                    plugin.getMysql().upsertPlayer(
                            player.getUniqueId(), player.getName(),
                            plugin.getCurrentSectorId(),
                            finalData != null ? finalData.getLastSectorId() : null
                    );
                }

                // Aktualizuj online count
                plugin.getRedis().setSectorPlayerCount(
                        plugin.getCurrentSectorId(),
                        plugin.getServer().getOnlinePlayers().size()
                );
            });
        });
    }
}
