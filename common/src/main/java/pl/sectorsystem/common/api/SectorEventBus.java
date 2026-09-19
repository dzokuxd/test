package pl.sectorsystem.common.api;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.UUID;

public class SectorEventBus {
    private final List<SectorEventListener> listeners = new CopyOnWriteArrayList<>();

    public void register(SectorEventListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void unregister(SectorEventListener listener) {
        listeners.remove(listener);
    }

    public void fireTransferRequest(UUID uuid, String from, String to, String reason) {
        for (SectorEventListener l : listeners) {
            try { l.onTransferRequest(uuid, from, to, reason); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public void fireTransferComplete(UUID uuid, String from, String to) {
        for (SectorEventListener l : listeners) {
            try { l.onTransferComplete(uuid, from, to); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public void fireTransferFailed(UUID uuid, String to, String reason) {
        for (SectorEventListener l : listeners) {
            try { l.onTransferFailed(uuid, to, reason); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public void fireSectorStatus(String sectorId, boolean online) {
        for (SectorEventListener l : listeners) {
            try { l.onSectorStatusChange(sectorId, online); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public void fireGlobalChat(String playerName, String message, String sectorId) {
        for (SectorEventListener l : listeners) {
            try { l.onGlobalChat(playerName, message, sectorId); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public void fireFailsafe(String reason) {
        for (SectorEventListener l : listeners) {
            try { l.onFailsafeTriggered(reason); } catch (Exception e) { e.printStackTrace(); }
        }
    }
}
