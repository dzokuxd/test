package pl.sectorsystem.common.redis;

import com.google.gson.Gson;
import pl.sectorsystem.common.PlayerSectorData;
import pl.sectorsystem.common.config.SystemConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RedisService implements AutoCloseable {
    private volatile JedisPool pool;
    private final SystemConfig.RedisConfig config;
    private final Gson gson = new Gson();
    private static final String PLAYER_KEY_PREFIX = "sector:player:";
    private static final String SECTOR_ONLINE_PREFIX = "sector:online:";
    private static final String SECTOR_COUNT_PREFIX = "sector:count:";
    private volatile boolean healthy = true;

    public RedisService(SystemConfig.RedisConfig config) {
        this.config = config;
        this.pool = createPool();
    }

    private JedisPool createPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(32);
        poolConfig.setMaxIdle(16);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            return new JedisPool(poolConfig, config.getHost(), config.getPort(), 2000, config.getPassword(), config.getDatabase());
        }
        return new JedisPool(poolConfig, config.getHost(), config.getPort(), 2000, "", config.getDatabase());
    }

    public Jedis getResource() {
        ensureConnected();
        return pool.getResource();
    }

    public boolean isHealthy() { return healthy; }

    public synchronized void reconnect() {
        try {
            if (pool != null && !pool.isClosed()) pool.close();
            pool = createPool();
            try (Jedis j = pool.getResource()) { j.ping(); }
            healthy = true;
        } catch (Exception e) {
            healthy = false;
            throw new IllegalStateException("Redis reconnect failed", e);
        }
    }

    private void ensureConnected() {
        if (pool == null || pool.isClosed()) reconnect();
    }

    public void savePlayerData(PlayerSectorData data) {
        try (Jedis jedis = getResource()) {
            data.setLastUpdate(System.currentTimeMillis());
            jedis.setex(PLAYER_KEY_PREFIX + data.getUuid(), 86400 * 7, gson.toJson(data));
            healthy = true;
        } catch (Exception e) {
            healthy = false;
            throw e;
        }
    }

    public PlayerSectorData getPlayerData(UUID uuid) {
        try (Jedis jedis = getResource()) {
            String json = jedis.get(PLAYER_KEY_PREFIX + uuid);
            healthy = true;
            return json == null ? null : gson.fromJson(json, PlayerSectorData.class);
        } catch (Exception e) {
            healthy = false;
            throw e;
        }
    }

    public void setPlayerSector(UUID uuid, String sectorId) {
        PlayerSectorData data = getPlayerData(uuid);
        if (data == null) data = new PlayerSectorData(uuid, "unknown");
        data.setLastSectorId(data.getCurrentSectorId());
        data.setCurrentSectorId(sectorId);
        savePlayerData(data);
    }

    public String getPlayerSector(UUID uuid) {
        PlayerSectorData data = getPlayerData(uuid);
        return data != null ? data.getCurrentSectorId() : null;
    }

    public void setSectorOnline(String sectorId, boolean online) {
        try (Jedis jedis = getResource()) {
            if (online) jedis.setex(SECTOR_ONLINE_PREFIX + sectorId, 30, "1");
            else jedis.del(SECTOR_ONLINE_PREFIX + sectorId);
        }
    }

    public boolean isSectorOnline(String sectorId) {
        try (Jedis jedis = getResource()) {
            return "1".equals(jedis.get(SECTOR_ONLINE_PREFIX + sectorId));
        }
    }

    /** Dokładna liczba online na sektorze */
    public void setSectorPlayerCount(String sectorId, int count) {
        try (Jedis jedis = getResource()) {
            jedis.setex(SECTOR_COUNT_PREFIX + sectorId, 30, String.valueOf(Math.max(0, count)));
        }
    }

    public int getSectorPlayerCount(String sectorId) {
        try (Jedis jedis = getResource()) {
            String v = jedis.get(SECTOR_COUNT_PREFIX + sectorId);
            return v == null ? 0 : Integer.parseInt(v);
        } catch (Exception e) {
            return 0;
        }
    }

    public int getGlobalPlayerCount() {
        int total = 0;
        try (Jedis jedis = getResource()) {
            for (String key : jedis.keys(SECTOR_COUNT_PREFIX + "*")) {
                String v = jedis.get(key);
                if (v != null) total += Integer.parseInt(v);
            }
        } catch (Exception ignored) {}
        return total;
    }

    public Map<String, Integer> getAllSectorCounts() {
        Map<String, Integer> map = new HashMap<>();
        try (Jedis jedis = getResource()) {
            for (String key : jedis.keys(SECTOR_COUNT_PREFIX + "*")) {
                String id = key.substring(SECTOR_COUNT_PREFIX.length());
                String v = jedis.get(key);
                map.put(id, v == null ? 0 : Integer.parseInt(v));
            }
        } catch (Exception ignored) {}
        return map;
    }

    @Override
    public void close() {
        if (pool != null && !pool.isClosed()) pool.close();
    }
}
