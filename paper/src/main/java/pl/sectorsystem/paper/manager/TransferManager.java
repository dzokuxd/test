package pl.sectorsystem.paper.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.sectorsystem.common.PlayerSectorData;
import pl.sectorsystem.common.lock.TransferLockService;
import pl.sectorsystem.common.messaging.SectorMessage;
import pl.sectorsystem.common.queue.QueueService;
import pl.sectorsystem.paper.SectorPaperPlugin;
import pl.sectorsystem.paper.util.PlayerDataSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TransferManager {
    private final SectorPaperPlugin plugin;
    private final TransferLockService lockService;
    private final QueueService queueService;
    private final Map<UUID, Long> pendingTeleports = new HashMap<>();

    public TransferManager(SectorPaperPlugin plugin) {
        this.plugin = plugin;
        this.lockService = new TransferLockService(plugin.getRedis());
        this.queueService = new QueueService(plugin.getRedis());
    }

    public void startDelayedTransfer(Player player, String targetSector, String reason, int delaySeconds) {
        UUID uuid = player.getUniqueId();
        if (pendingTeleports.containsKey(uuid)) {
            player.sendMessage("§cMasz już aktywne odliczanie teleportu!");
            return;
        }
        if (lockService.isLocked(uuid)) {
            player.sendMessage("§cTrwa już transfer Twojej postaci!");
            return;
        }
        if (delaySeconds <= 0) {
            requestTransfer(player, targetSector, reason);
            return;
        }
        pendingTeleports.put(uuid, System.currentTimeMillis());
        player.sendMessage("§eTeleport za §c" + delaySeconds + " §esekund... Nie ruszaj się!");
        for (int i = delaySeconds; i >= 1; i--) {
            final int sec = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!pendingTeleports.containsKey(uuid)) return;
                if (player.isOnline()) player.sendActionBar("§eTeleport za §c" + sec + " §es...");
            }, 20L * (delaySeconds - i));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!pendingTeleports.containsKey(uuid)) return;
            pendingTeleports.remove(uuid);
            if (player.isOnline()) requestTransfer(player, targetSector, reason);
        }, 20L * delaySeconds);
    }

    public void cancelPending(Player player) {
        pendingTeleports.remove(player.getUniqueId());
    }

    public boolean hasPending(Player player) {
        return pendingTeleports.containsKey(player.getUniqueId());
    }

    public boolean requestTransfer(Player player, String targetSector, String reason) {
        UUID uuid = player.getUniqueId();

        if (!plugin.getSectorManager().sectorExists(targetSector)) {
            player.sendMessage("§cSektor docelowy nie istnieje!");
            fireFailed(uuid, targetSector, "SECTOR_NOT_FOUND");
            return false;
        }
        if (!plugin.getRedis().isSectorOnline(targetSector)) {
            player.sendMessage("§cSektor §e" + targetSector + " §cjest niedostępny!");
            fireFailed(uuid, targetSector, "SECTOR_OFFLINE");
            return false;
        }

        // Limit graczy + kolejka
        int max = plugin.getSectorManager().getMaxPlayers(targetSector);
        int current = plugin.getRedis().getSectorPlayerCount(targetSector);
        if (max > 0 && current >= max) {
            long pos = queueService.joinQueue(targetSector, uuid);
            player.sendMessage("§eSektor pełny! Jesteś §c#" + pos + " §ew kolejce do §e" + targetSector);
            return false;
        }

        if (!lockService.tryLock(uuid)) {
            player.sendMessage("§cTransfer w toku – spróbuj za chwilę!");
            fireFailed(uuid, targetSector, "LOCKED");
            return false;
        }

        try {
            PlayerSectorData data = PlayerDataSerializer.capture(player);
            data.setLastSectorId(plugin.getCurrentSectorId());
            data.setCurrentSectorId(targetSector);
            plugin.getRedis().savePlayerData(data);

            if (plugin.getApi() != null) {
                plugin.getApi().getEventBus().fireTransferRequest(
                        uuid, plugin.getCurrentSectorId(), targetSector, reason);
            }

            SectorMessage msg = new SectorMessage(SectorMessage.Type.TRANSFER_REQUEST);
            msg.setPlayerUuid(uuid);
            msg.setPlayerName(player.getName());
            msg.setFromSector(plugin.getCurrentSectorId());
            msg.setToSector(targetSector);
            msg.setReason(reason);
            plugin.getNats().publish(msg);

            if (plugin.getMysql() != null && plugin.getMysql().isEnabled()) {
                plugin.getMysql().incrementTransfers(uuid);
            }
            queueService.leaveQueue(targetSector, uuid);
            player.sendMessage("§aPrzenoszenie na sektor §e" + targetSector + "§a...");
            return true;
        } catch (Exception e) {
            lockService.unlock(uuid);
            fireFailed(uuid, targetSector, e.getMessage());
            player.sendMessage("§cBłąd transferu: " + e.getMessage());
            plugin.getLogger().severe("Transfer error: " + e.getMessage());
            return false;
        }
    }

    public void forceTransfer(Player player, String targetSector, String reason) {
        requestTransfer(player, targetSector, reason);
    }

    /** Wywoływane na sektorze docelowym po joinie – zwalnia lock */
    public void unlockAfterJoin(UUID uuid, String fromSector) {
        lockService.unlock(uuid);
        if (plugin.getApi() != null) {
            plugin.getApi().getEventBus().fireTransferComplete(uuid, fromSector, plugin.getCurrentSectorId());
        }
        // Spróbuj wpuścić następnego z kolejki
        tryPopQueue(plugin.getCurrentSectorId());
    }

    private void tryPopQueue(String sectorId) {
        int max = plugin.getSectorManager().getMaxPlayers(sectorId);
        int current = plugin.getRedis().getSectorPlayerCount(sectorId);
        if (max > 0 && current >= max) return;
        queueService.popNext(sectorId).ifPresent(nextUuid -> {
            plugin.getLogger().info("Kolejka: następny gracz " + nextUuid + " może wejść na " + sectorId);
            // Proxy/admin musi to obsłużyć – publikujemy sygnał
            SectorMessage msg = new SectorMessage(SectorMessage.Type.QUEUE_UPDATE);
            msg.setPlayerUuid(nextUuid);
            msg.setToSector(sectorId);
            msg.setReason("SLOT_AVAILABLE");
            try { plugin.getNats().publish(msg); } catch (Exception ignored) {}
        });
    }

    private void fireFailed(UUID uuid, String to, String reason) {
        if (plugin.getApi() != null) {
            plugin.getApi().getEventBus().fireTransferFailed(uuid, to, reason);
        }
    }

    public TransferLockService getLockService() { return lockService; }
    public QueueService getQueueService() { return queueService; }
}
