package pl.dzoku.sectorsystem.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

public class TransferMetrics {
    private static final Logger logger = Logger.getLogger(TransferMetrics.class.getName());

    private final Map<String, LongAdder> transferCounts = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> failureCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> maxTransferTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalTransferTime = new ConcurrentHashMap<>();

    public void recordTransferStart(String from, String to) {
        String key = from + "->" + to;
        transferCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    public void recordTransferComplete(String from, String to, long durationMs) {
        String key = from + "->" + to;

        maxTransferTime.computeIfAbsent(key, k -> new AtomicLong())
                .updateAndGet(current -> Math.max(current, durationMs));

        totalTransferTime.computeIfAbsent(key, k -> new AtomicLong())
                .addAndGet(durationMs);

        if (durationMs > 5000) {
            logger.warning("Slow transfer " + key + ": " + durationMs + "ms");
        }
    }

    public void recordTransferFailure(String from, String to, String reason) {
        String key = from + "->" + to + ":" + reason;
        failureCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
        logger.warning("Transfer failed " + key);
    }

    public void printReport() {
        logger.info("=== Transfer Metrics Report ===");
        transferCounts.forEach((route, count) -> {
            long total = totalTransferTime.getOrDefault(route, new AtomicLong()).get();
            long max = maxTransferTime.getOrDefault(route, new AtomicLong()).get();
            long avg = count.sum() > 0 ? total / count.sum() : 0;
            logger.info(String.format("  %s: %d transfers, avg=%dms, max=%dms", route, count.sum(), avg, max));
        });
        failureCounts.forEach((key, count) -> {
            logger.warning("  FAILURES: " + key + " = " + count.sum());
        });
        logger.info("=== End Report ===");
    }

    public long getAverageTransferTime(String from, String to) {
        String key = from + "->" + to;
        LongAdder count = transferCounts.get(key);
        AtomicLong total = totalTransferTime.get(key);
        if (count == null || total == null || count.sum() == 0) return 0;
        return total.get() / count.sum();
    }
}
