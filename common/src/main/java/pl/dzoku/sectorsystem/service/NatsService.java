package pl.dzoku.sectorsystem.service;

import io.nats.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class NatsService {
    private static final Logger logger = Logger.getLogger(NatsService.class.getName());

    private Connection connection;
    private final String natsUrl;
    private final Map<String, Consumer<String>> subscriptions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = true;

    public NatsService(String natsUrl) {
        this.natsUrl = natsUrl;
        connect();
    }

    private void connect() {
        try {
            Options options = new Options.Builder()
                    .server(natsUrl)
                    .connectionName("SectorSystem")
                    .connectionTimeout(Duration.ofSeconds(5))
                    .reconnectWait(Duration.ofSeconds(2))
                    .maxReconnects(-1)
                    .connectionListener((conn, type) -> {
                        switch (type) {
                            case CONNECTED -> logger.info("NATS connected: " + conn.getConnectedUrl());
                            case DISCONNECTED -> logger.warning("NATS disconnected - attempting reconnect...");
                            case RECONNECTED -> logger.info("NATS reconnected: " + conn.getConnectedUrl());
                            case CLOSED -> logger.info("NATS connection closed");
                        }
                    })
                    .build();

            this.connection = Nats.connect(options);
            logger.info("NATS connected to " + natsUrl);
            resubscribe();

        } catch (IOException | InterruptedException e) {
            logger.severe("Failed to connect to NATS: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running) return;
        reconnectScheduler.schedule(() -> {
            logger.info("Attempting NATS reconnect...");
            connect();
        }, 5, TimeUnit.SECONDS);
    }

    public void publish(String subject, String message) {
        if (connection == null || connection.getStatus() != Connection.Status.CONNECTED) {
            logger.warning("NATS not connected - message dropped: " + subject);
            return;
        }
        connection.publish(subject, message.getBytes(StandardCharsets.UTF_8));
    }

    public void subscribe(String subject, Consumer<String> handler) {
        subscriptions.put(subject, handler);

        if (connection != null && connection.getStatus() == Connection.Status.CONNECTED) {
            connection.createDispatcher(msg -> {
                String data = new String(msg.getData(), StandardCharsets.UTF_8);
                handler.accept(data);
            }).subscribe(subject);
        }
    }

    private void resubscribe() {
        subscriptions.forEach((subject, handler) -> {
            connection.createDispatcher(msg -> {
                String data = new String(msg.getData(), StandardCharsets.UTF_8);
                handler.accept(data);
            }).subscribe(subject);
            logger.info("Re-subscribed to: " + subject);
        });
    }

    public boolean isConnected() {
        return connection != null && connection.getStatus() == Connection.Status.CONNECTED;
    }

    public void shutdown() {
        running = false;
        reconnectScheduler.shutdown();
        if (connection != null) {
            try {
                connection.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        logger.info("NatsService shutdown complete");
    }
}
