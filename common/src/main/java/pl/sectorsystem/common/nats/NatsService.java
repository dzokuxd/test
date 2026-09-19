package pl.sectorsystem.common.nats;

import com.google.gson.Gson;
import io.nats.client.*;
import pl.sectorsystem.common.config.SystemConfig;
import pl.sectorsystem.common.messaging.SectorMessage;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * NATS z automatycznym reconnectem i health-checkiem.
 */
public class NatsService implements AutoCloseable {

    private final SystemConfig.NatsConfig config;
    private final Gson gson = new Gson();
    private volatile Connection connection;
    private final AtomicBoolean healthy = new AtomicBoolean(false);
    private static final String SUBJECT_PREFIX = "sectors.";

    public NatsService(SystemConfig.NatsConfig config) throws Exception {
        this.config = config;
        connect();
    }

    private void connect() throws Exception {
        Options options = new Options.Builder()
                .server(config.getUrl())
                .connectionTimeout(Duration.ofSeconds(5))
                .reconnectWait(Duration.ofSeconds(2))
                .maxReconnects(-1) // nieskończone próby
                .connectionListener((conn, type) -> {
                    switch (type) {
                        case CONNECTED, RECONNECTED -> healthy.set(true);
                        case DISCONNECTED, CLOSED -> healthy.set(false);
                    }
                })
                .build();
        this.connection = Nats.connect(options);
        healthy.set(true);
    }

    public boolean isHealthy() {
        return healthy.get() && connection != null && connection.getStatus() == Connection.Status.CONNECTED;
    }

    public synchronized void reconnect() {
        try {
            if (connection != null) {
                try { connection.close(); } catch (Exception ignored) {}
            }
            connect();
        } catch (Exception e) {
            healthy.set(false);
            throw new IllegalStateException("NATS reconnect failed", e);
        }
    }

    public void publish(SectorMessage message) {
        if (!isHealthy()) {
            throw new IllegalStateException("NATS is not healthy");
        }
        String subject = SUBJECT_PREFIX + message.getType().name().toLowerCase();
        String json = gson.toJson(message);
        connection.publish(subject, json.getBytes(StandardCharsets.UTF_8));
    }

    public void publishToSector(String sectorId, SectorMessage message) {
        if (!isHealthy()) {
            throw new IllegalStateException("NATS is not healthy");
        }
        String subject = SUBJECT_PREFIX + "sector." + sectorId;
        String json = gson.toJson(message);
        connection.publish(subject, json.getBytes(StandardCharsets.UTF_8));
    }

    public void publishRaw(String subjectSuffix, String json) {
        if (!isHealthy()) return;
        connection.publish(SUBJECT_PREFIX + subjectSuffix, json.getBytes(StandardCharsets.UTF_8));
    }

    public Dispatcher subscribe(String subjectSuffix, Consumer<SectorMessage> handler) {
        Dispatcher dispatcher = connection.createDispatcher(msg -> {
            try {
                String json = new String(msg.getData(), StandardCharsets.UTF_8);
                SectorMessage message = gson.fromJson(json, SectorMessage.class);
                handler.accept(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        dispatcher.subscribe(SUBJECT_PREFIX + subjectSuffix);
        return dispatcher;
    }

    public Dispatcher subscribeRaw(String subjectSuffix, Consumer<String> handler) {
        Dispatcher dispatcher = connection.createDispatcher(msg -> {
            try {
                handler.accept(new String(msg.getData(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        dispatcher.subscribe(SUBJECT_PREFIX + subjectSuffix);
        return dispatcher;
    }

    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() throws Exception {
        healthy.set(false);
        if (connection != null) {
            connection.close();
        }
    }
}
