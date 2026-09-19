package pl.sectorsystem.paper.api;

import pl.sectorsystem.common.PlayerSectorData;
import pl.sectorsystem.common.SectorDefinition;
import pl.sectorsystem.common.api.SectorAPI;
import pl.sectorsystem.common.api.SectorEventBus;
import pl.sectorsystem.common.api.SectorEventListener;
import pl.sectorsystem.common.messaging.SectorMessage;
import pl.sectorsystem.paper.SectorPaperPlugin;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class PaperSectorAPI implements SectorAPI {

    private final SectorPaperPlugin plugin;
    private final SectorEventBus eventBus = new SectorEventBus();

    public PaperSectorAPI(SectorPaperPlugin plugin) {
        this.plugin = plugin;
    }

    public SectorEventBus getEventBus() { return eventBus; }

    @Override
    public String getCurrentSectorId() {
        return plugin.getCurrentSectorId();
    }

    @Override
    public Optional<SectorDefinition> getSector(String id) {
        return Optional.ofNullable(plugin.getSectorManager().getSector(id));
    }

    @Override
    public Collection<SectorDefinition> getAllSectors() {
        return plugin.getSectorManager().getAllSectors();
    }

    @Override
    public boolean isSectorOnline(String sectorId) {
        return plugin.getRedis().isSectorOnline(sectorId);
    }

    @Override
    public Optional<String> getPlayerSector(UUID playerUuid) {
        return Optional.ofNullable(plugin.getRedis().getPlayerSector(playerUuid));
    }

    @Override
    public Optional<PlayerSectorData> getPlayerData(UUID playerUuid) {
        return Optional.ofNullable(plugin.getRedis().getPlayerData(playerUuid));
    }

    @Override
    public boolean requestTransfer(UUID playerUuid, String targetSector, String reason) {
        var player = plugin.getServer().getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return false;
        return plugin.getTransferManager().requestTransfer(player, targetSector, reason);
    }

    @Override
    public boolean isTransferLocked(UUID playerUuid) {
        return plugin.getTransferManager().getLockService().isLocked(playerUuid);
    }

    @Override
    public void registerListener(SectorEventListener listener) {
        eventBus.register(listener);
    }

    @Override
    public void unregisterListener(SectorEventListener listener) {
        eventBus.unregister(listener);
    }

    @Override
    public void broadcastGlobalChat(String playerName, String message, String sectorId) {
        SectorMessage msg = new SectorMessage(SectorMessage.Type.GLOBAL_CHAT);
        msg.setPlayerName(playerName);
        msg.setMessage(message);
        msg.setFromSector(sectorId != null ? sectorId : plugin.getCurrentSectorId());
        msg.setFormat("§7[§e" + msg.getFromSector() + "§7] §f" + playerName + "§7: §f");
        try {
            plugin.getNats().publish(msg);
        } catch (Exception e) {
            plugin.getLogger().warning("broadcastGlobalChat failed: " + e.getMessage());
        }
    }
}
