package pl.sectorsystem.paper.api;

import pl.sectorsystem.common.PlayerSectorData;
import pl.sectorsystem.common.SectorDefinition;
import pl.sectorsystem.common.api.SectorAPI;
import pl.sectorsystem.common.api.SectorEventListener;
import pl.sectorsystem.common.messaging.SectorMessage;
import pl.sectorsystem.paper.SectorPaperPlugin;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class SectorAPIImpl implements SectorAPI {

    private final SectorPaperPlugin plugin;
    private final List<SectorEventListener> listeners = new CopyOnWriteArrayList<>();

    public SectorAPIImpl(SectorPaperPlugin plugin) {
        this.plugin = plugin;
    }

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
        listeners.add(listener);
    }

    @Override
    public void unregisterListener(SectorEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void broadcastGlobalChat(String playerName, String message, String sectorId) {
        SectorMessage msg = new SectorMessage(SectorMessage.Type.GLOBAL_CHAT);
        msg.setPlayerName(playerName);
        msg.setMessage(message);
        msg.setFromSector(sectorId);
        msg.setFormat("§7[§e" + sectorId + "§7] §f" + playerName + "§7: §f");
        try {
            plugin.getNats().publish(msg);
        } catch (Exception ignored) {}
    }

    // --- fire events ---
    public void fireTransferRequest(UUID uuid, String from, String to, String reason) {
        for (SectorEventListener l : listeners) {
            try { l.onTransferRequest(uuid, from, to, reason); } catch (Exception ignored) {}
        }
    }

    public void fireTransferComplete(UUID uuid, String from, String to) {
        for (SectorEventListener l : listeners) {
            try { l.onTransferComplete(uuid, from, to); } catch (Exception ignored) {}
        }
    }

    public void fireTransferFailed(UUID uuid, String to, String reason) {
        for (SectorEventListener l : listeners) {
            try { l.onTransferFailed(uuid, to, reason); } catch (Exception ignored) {}
        }
    }

    public void fireSectorStatus(String sectorId, boolean online) {
        for (SectorEventListener l : listeners) {
            try { l.onSectorStatusChange(sectorId, online); } catch (Exception ignored) {}
        }
    }

    public void fireGlobalChat(String playerName, String message, String sectorId) {
        for (SectorEventListener l : listeners) {
            try { l.onGlobalChat(playerName, message, sectorId); } catch (Exception ignored) {}
        }
    }

    public void fireFailsafe(String reason) {
        for (SectorEventListener l : listeners) {
            try { l.onFailsafeTriggered(reason); } catch (Exception ignored) {}
        }
    }
}
