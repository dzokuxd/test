package pl.sectorsystem.common.lock;

import pl.sectorsystem.common.redis.RedisService;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Anty-dupe lock przy transferze gracza.
 * Używa Redis SET NX EX – atomowy lock.
 */
public class TransferLockService {

    private static final String LOCK_PREFIX = "sector:transfer_lock:";
    private static final int DEFAULT_LOCK_SECONDS = 15;

    private final RedisService redis;

    public TransferLockService(RedisService redis) {
        this.redis = redis;
    }

    /**
     * Próbuje zająć lock na gracza.
     * @return true jeśli udało się zająć lock, false jeśli gracz już jest w trakcie transferu
     */
    public boolean tryLock(UUID playerUuid) {
        return tryLock(playerUuid, DEFAULT_LOCK_SECONDS);
    }

    public boolean tryLock(UUID playerUuid, int seconds) {
        try (Jedis jedis = redis.getResource()) {
            String key = LOCK_PREFIX + playerUuid.toString();
            String result = jedis.set(key, "1", SetParams.setParams().nx().ex(seconds));
            return "OK".equalsIgnoreCase(result);
        } catch (Exception e) {
            // W razie problemu z Redis – lepiej zablokować transfer niż ryzykować dupe
            return false;
        }
    }

    public void unlock(UUID playerUuid) {
        try (Jedis jedis = redis.getResource()) {
            jedis.del(LOCK_PREFIX + playerUuid.toString());
        } catch (Exception ignored) {}
    }

    public boolean isLocked(UUID playerUuid) {
        try (Jedis jedis = redis.getResource()) {
            return jedis.exists(LOCK_PREFIX + playerUuid.toString());
        } catch (Exception e) {
            return true; // fail-safe: traktuj jako zablokowany
        }
    }
}
