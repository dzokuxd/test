package pl.dzoku.sectorsystem.service;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import pl.dzoku.sectorsystem.model.TransferState;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class RedisService {
    private static final Logger logger = Logger.getLogger(RedisService.class.getName());

    private final JedisPool pool;
    private final ExecutorService executor;

    private volatile int consecutiveFailures = 0;
    private volatile long lastFailureTime = 0;
    private static final int MAX_FAILURES = 5;
    private static final long COOLDOWN_MS = 30_000;

    public RedisService(String host, int port, int maxConnections) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxConnections);
        poolConfig.setMaxIdle(maxConnections / 2);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRunsMillis(30_000);

        this.pool = new JedisPool(poolConfig, host, port, 2000);
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "redis-async");
            t.setDaemon(true);
            return t;
        });

        logger.info("RedisService initialized: " + host + ":" + port + " (max " + maxConnections + " connections)");
    }

    private void checkCircuitBreaker() {
        if (consecutiveFailures >= MAX_FAILURES) {
            if (System.currentTimeMillis() - lastFailureTime > COOLDOWN_MS) {
                consecutiveFailures = 0;
                logger.info("Circuit breaker reset - Redis available again");
            } else {
                throw new ServiceUnavailableException("Redis unavailable (circuit breaker open)");
            }
        }
    }

    private void onSuccess() { consecutiveFailures = 0; }

    private void onFailure() {
        consecutiveFailures++;
        lastFailureTime = System.currentTimeMillis();
        if (consecutiveFailures == MAX_FAILURES) {
            logger.severe("Circuit breaker OPEN - Redis not responding (" + MAX_FAILURES + " failures)");
        }
    }

    public CompletableFuture<String> getAsync(String key) {
        return CompletableFuture.supplyAsync(() -> {
            checkCircuitBreaker();
            try (Jedis jedis = pool.getResource()) {
                String result = jedis.get(key);
                onSuccess();
                return result;
            } catch (Exception e) {
                onFailure();
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> setAsync(String key, String value) {
        return CompletableFuture.runAsync(() -> {
            checkCircuitBreaker();
            try (Jedis jedis = pool.getResource()) {
                jedis.set(key, value);
                onSuccess();
            } catch (Exception e) {
                onFailure();
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> setWithTtlAsync(String key, String value, int ttlSeconds) {
        return CompletableFuture.runAsync(() -> {
            checkCircuitBreaker();
            try (Jedis jedis = pool.getResource()) {
                jedis.setex(key, ttlSeconds, value);
                onSuccess();
            } catch (Exception e) {
                onFailure();
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> setBytesAsync(String key, byte[] data) {
        return CompletableFuture.runAsync(() -> {
            checkCircuitBreaker();
            try (Jedis jedis = pool.getResource()) {
                jedis.set(key.getBytes(), data);
                onSuccess();
            } catch (Exception e) {
                onFailure();
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<byte[]> getBytesAsync(String key) {
        return CompletableFuture.supplyAsync(() -> {
            checkCircuitBreaker();
            try (Jedis jedis = pool.getResource()) {
                byte[] result = jedis.get(key.getBytes());
                onSuccess();
                return result;
            } catch (Exception e) {
                onFailure();
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> deleteAsync(String key) {
        return CompletableFuture.runAsync(() -> {
            checkCircuitBreaker();
            try (Jedis jedis = pool.getResource()) {
                jedis.del(key);
                onSuccess();
            } catch (Exception e) {
                onFailure();
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> saveTransferState(UUID uuid, TransferState state) {
        return setWithTtlAsync("transfer_state:" + uuid, state.name(), 300);
    }

    public CompletableFuture<TransferState> getTransferState(UUID uuid) {
        return getAsync("transfer_state:" + uuid)
                .thenApply(value -> {
                    if (value == null) return TransferState.IDLE;
                    try {
                        return TransferState.valueOf(value);
                    } catch (IllegalArgumentException e) {
                        return TransferState.IDLE;
                    }
                });
    }

    public CompletableFuture<Void> savePlayerSnapshot(UUID uuid, byte[] data) {
        return setBytesAsync("snapshot:" + uuid, data);
    }

    public CompletableFuture<byte[]> getPlayerSnapshot(UUID uuid) {
        return getBytesAsync("snapshot:" + uuid);
    }

    public CompletableFuture<Void> deletePlayerSnapshot(UUID uuid) {
        return deleteAsync("snapshot:" + uuid);
    }

    public CompletableFuture<Void> setSectorHeartbeat(String sectorId) {
        return setWithTtlAsync("heartbeat:" + sectorId, String.valueOf(System.currentTimeMillis()), 30);
    }

    public CompletableFuture<Long> getSectorHeartbeat(String sectorId) {
        return getAsync("heartbeat:" + sectorId)
                .thenApply(value -> value != null ? Long.parseLong(value) : -1L);
    }

    public CompletableFuture<Void> setPlayerSector(UUID uuid, String sectorId) {
        return setAsync("player_sector:" + uuid, sectorId);
    }

    public CompletableFuture<String> getPlayerSector(UUID uuid) {
        return getAsync("player_sector:" + uuid);
    }

    public CompletableFuture<Void> setTransferCooldown(UUID uuid, String type, long expiresAt) {
        return setWithTtlAsync("cooldown:" + type + ":" + uuid, String.valueOf(expiresAt), 3600);
    }

    public CompletableFuture<Long> getTransferCooldown(UUID uuid, String type) {
        return getAsync("cooldown:" + type + ":" + uuid)
                .thenApply(value -> value != null ? Long.parseLong(value) : 0L);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        pool.close();
        logger.info("RedisService shutdown complete");
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }
}
