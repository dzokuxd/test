package pl.dzoku.sectorsystem;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.service.NatsService;
import pl.dzoku.sectorsystem.service.RedisService;
import pl.sectorsystem.common.mysql.MySQLService;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class TransferRouter {
    private static final Gson gson = new Gson();
    private final ProxyServer server;
    private final RedisService redisService;
    private final NatsService natsService;
    private final SectorHealthChecker healthChecker;
    private final Logger logger;
    private final SectorProxyPlugin plugin;

    public TransferRouter(ProxyServer server, RedisService redisService,
                          NatsService natsService, SectorHealthChecker healthChecker,
                          Logger logger, SectorProxyPlugin plugin) {
        this.server = server;
        this.redisService = redisService;
        this.natsService = natsService;
        this.healthChecker = healthChecker;
        this.logger = logger;
        this.plugin = plugin;
    }

    public void handleTransferRequest(String message) {
        JsonObject json = gson.fromJson(message, JsonObject.class);
        if (json == null || !json.has("uuid") || !json.has("name") || !json.has("to")) {
            logger.severe("Otrzymano uszkodzoną wiadomość transferu: " + message);
            return;
        }
        String uuidStr = json.get("uuid").getAsString();
        String playerName = json.get("name").getAsString();
        String targetSector = json.get("to").getAsString();
        UUID uuid = UUID.fromString(uuidStr);
        Optional<Player> playerOpt = server.getPlayer(uuid);
        if (playerOpt.isEmpty()) {
            logger.warning("Player " + playerName + " not found on proxy during transfer");
            return;
        }
        Player player = playerOpt.get();

        if (!targetSector.equals(RestartOrchestrator.LIMBO) && !healthChecker.isSectorOnline(targetSector)) {
            logger.warning("Target sector " + targetSector + " is offline for player " + playerName);
            player.sendMessage(Component.text("§cSektor §e'" + targetSector + "' §cjest offline! Przekierowuję do limbo.", NamedTextColor.RED));
            redirectToServer(player, RestartOrchestrator.LIMBO);
            return;
        }

        // Sprawdź sloty przy transferze
        if (!targetSector.equals(RestartOrchestrator.LIMBO)) {
            int maxSlots = getSectorMaxSlots(targetSector);
            if (maxSlots > 0) {
                Optional<RegisteredServer> srv = server.getServer(targetSector);
                int online = srv.map(s -> s.getPlayersConnected().size()).orElse(0);
                if (online >= maxSlots) {
                    logger.warning("Sector " + targetSector + " is full (" + online + "/" + maxSlots + ") - transfer zablokowany");
                    player.sendMessage(Component.text("§c§l[QUEUE] §cSektor §e" + targetSector + " §cjest pełny! (" + online + "/" + maxSlots + ")", NamedTextColor.RED));
                    player.sendMessage(Component.text("§7Zostałeś dodany do kolejki...", NamedTextColor.YELLOW));
                    plugin.getQueueManager().addToWaitingQueue(player, targetSector);
                    return;
                }
            }
        }

        redirectToServer(player, targetSector);
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

    private void redirectToServer(Player player, String serverName) {
        Optional<RegisteredServer> serverOpt = server.getServer(serverName);
        if (serverOpt.isEmpty()) {
            logger.severe("Server " + serverName + " not registered in Velocity!");
            return;
        }
        player.createConnectionRequest(serverOpt.get())
                .connect()
                .thenAccept(result -> {
                    if (result.isSuccessful()) {
                        logger.info("Player " + player.getUsername() + " redirected to " + serverName);
                    } else {
                        logger.severe("Failed to redirect " + player.getUsername() + " to " + serverName);
                    }
                });
    }
}