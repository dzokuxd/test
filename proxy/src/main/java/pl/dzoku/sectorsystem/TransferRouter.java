package pl.dzoku.sectorsystem;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import pl.dzoku.sectorsystem.service.NatsService;
import pl.dzoku.sectorsystem.service.RedisService;

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

    public TransferRouter(ProxyServer server, RedisService redisService,
                          NatsService natsService, SectorHealthChecker healthChecker, Logger logger) {
        this.server = server;
        this.redisService = redisService;
        this.natsService = natsService;
        this.healthChecker = healthChecker;
        this.logger = logger;
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
            redirectToServer(player, "spawn");
            return;
        }

        redirectToServer(player, targetSector);
    }

    private void redirectToServer(Player player, String serverName) {
        Optional<RegisteredServer> serverOpt = server.getServer(serverName);
        if (serverOpt.isEmpty()) {
            logger.severe("Server " + serverName + " not registered in Velocity!");
            return;
        }

        // connect() zwraca CompletableFuture<Result> - mozna przeczytac wynik
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