package pl.sectorsystem.common.queue;

import com.google.gson.Gson;
import pl.sectorsystem.common.redis.RedisService;
import redis.clients.jedis.Jedis;

import java.util.*;

/**
 * Prosty system kolejek per sektor (gdy sektor jest pełny).
 */
public class QueueService {

    private static final String QUEUE_PREFIX = "sector:queue:";
    private final RedisService redis;
    private final Gson gson = new Gson();

    public QueueService(RedisService redis) {
        this.redis = redis;
    }

    public long joinQueue(String sectorId, UUID playerUuid) {
        try (Jedis jedis = redis.getResource()) {
            String key = QUEUE_PREFIX + sectorId;
            // Usuń poprzednie wpisy gracza (na wypadek)
            jedis.lrem(key, 0, playerUuid.toString());
            jedis.rpush(key, playerUuid.toString());
            return jedis.llen(key);
        }
    }

    public void leaveQueue(String sectorId, UUID playerUuid) {
        try (Jedis jedis = redis.getResource()) {
            jedis.lrem(QUEUE_PREFIX + sectorId, 0, playerUuid.toString());
        }
    }

    public Optional<UUID> popNext(String sectorId) {
        try (Jedis jedis = redis.getResource()) {
            String val = jedis.lpop(QUEUE_PREFIX + sectorId);
            if (val == null) return Optional.empty();
            return Optional.of(UUID.fromString(val));
        }
    }

    public long getPosition(String sectorId, UUID playerUuid) {
        try (Jedis jedis = redis.getResource()) {
            List<String> list = jedis.lrange(QUEUE_PREFIX + sectorId, 0, -1);
            int idx = list.indexOf(playerUuid.toString());
            return idx >= 0 ? idx + 1 : -1;
        }
    }

    public long getQueueSize(String sectorId) {
        try (Jedis jedis = redis.getResource()) {
            return jedis.llen(QUEUE_PREFIX + sectorId);
        }
    }

    public void clearQueue(String sectorId) {
        try (Jedis jedis = redis.getResource()) {
            jedis.del(QUEUE_PREFIX + sectorId);
        }
    }
}
