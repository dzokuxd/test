package pl.sectorsystem.common.api;

import pl.sectorsystem.common.PlayerSectorData;
import pl.sectorsystem.common.SectorDefinition;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Proste API dla innych pluginów.
 */
public interface SectorAPI {

    /** Aktualny sektor tej instancji */
    String getCurrentSectorId();

    Optional<SectorDefinition> getSector(String id);

    Collection<SectorDefinition> getAllSectors();

    boolean isSectorOnline(String sectorId);

    Optional<String> getPlayerSector(UUID playerUuid);

    Optional<PlayerSectorData> getPlayerData(UUID playerUuid);

    /**
     * Żąda transferu gracza. Zwraca false jeśli nie można (lock, sektor offline, brak uprawnień itd.)
     */
    boolean requestTransfer(UUID playerUuid, String targetSector, String reason);

    /** Czy gracz ma aktywny lock transferu */
    boolean isTransferLocked(UUID playerUuid);

    /** Rejestracja listenera eventów sektorowych */
    void registerListener(SectorEventListener listener);

    void unregisterListener(SectorEventListener listener);

    /** Publikacja globalnego chatu */
    void broadcastGlobalChat(String playerName, String message, String sectorId);
}
