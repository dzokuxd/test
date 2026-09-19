package pl.sectorsystem.common.api;

import java.util.UUID;

/**
 * Listener eventów sektorowych – inne pluginy mogą implementować.
 */
public interface SectorEventListener {

    default void onTransferRequest(UUID playerUuid, String fromSector, String toSector, String reason) {}

    default void onTransferComplete(UUID playerUuid, String fromSector, String toSector) {}

    default void onTransferFailed(UUID playerUuid, String toSector, String reason) {}

    default void onSectorStatusChange(String sectorId, boolean online) {}

    default void onGlobalChat(String playerName, String message, String sectorId) {}

    default void onFailsafeTriggered(String reason) {}
}
