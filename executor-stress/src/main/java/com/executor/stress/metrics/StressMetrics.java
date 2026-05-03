package com.executor.stress.metrics;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Extended stress-test metrics beyond the core MetricsCollector.
 * Tracks latency percentiles, DB lock wait, and per-round stats.
 */
@Component
public class StressMetrics {

    private final LongAdder totalDbLockMs = new LongAdder();
    private final LongAdder totalDbSelectMs = new LongAdder();
    private final LongAdder totalMqSendMs = new LongAdder();
    private final LongAdder totalRoundMs = new LongAdder();
    private final AtomicLong dbLockCount = new AtomicLong();
    private final AtomicLong dbSelectCount = new AtomicLong();
    private final AtomicLong mqSendCount = new AtomicLong();
    private final AtomicLong roundCount = new AtomicLong();

    // Track the last N MQ send latencies for approximate P99
    private static final int LATENCY_WINDOW = 10000;
    private final long[] sendLatencies = new long[LATENCY_WINDOW];
    private final AtomicLong sendLatencyIdx = new AtomicLong();

    private volatile long minSendMs = Long.MAX_VALUE;
    private volatile long maxSendMs;

    public void recordDbLock(long ms) {
        totalDbLockMs.add(ms);
        dbLockCount.incrementAndGet();
    }

    public void recordDbSelect(long ms) {
        totalDbSelectMs.add(ms);
        dbSelectCount.incrementAndGet();
    }

    public void recordMqSend(long ms) {
        totalMqSendMs.add(ms);
        long idx = sendLatencyIdx.getAndIncrement() % LATENCY_WINDOW;
        sendLatencies[(int) idx] = ms;
        mqSendCount.incrementAndGet();
        synchronized (this) {
            if (ms < minSendMs) minSendMs = ms;
            if (ms > maxSendMs) maxSendMs = ms;
        }
    }

    public void recordRound(long ms) {
        totalRoundMs.add(ms);
        roundCount.incrementAndGet();
    }

    /** Approximate P50/P99 from the sliding window */
    public Map<String, Object> snapshot() {
        long sent = mqSendCount.get();
        long locked = dbLockCount.get();
        long selected = dbSelectCount.get();
        long rounds = roundCount.get();

        Map<String, Object> snap = new ConcurrentHashMap<>();
        snap.put("dbLockAvgMs", locked > 0 ? (double) totalDbLockMs.sum() / locked : 0);
        snap.put("dbSelectAvgMs", selected > 0 ? (double) totalDbSelectMs.sum() / selected : 0);
        snap.put("mqSendAvgMs", sent > 0 ? (double) totalMqSendMs.sum() / sent : 0);
        snap.put("mqSendP50Ms", approxPercentile(50));
        snap.put("mqSendP99Ms", approxPercentile(99));
        snap.put("mqSendMinMs", minSendMs == Long.MAX_VALUE ? 0 : minSendMs);
        snap.put("mqSendMaxMs", maxSendMs);
        snap.put("roundAvgMs", rounds > 0 ? (double) totalRoundMs.sum() / rounds : 0);
        snap.put("totalDbLockCount", locked);
        snap.put("totalMqSendCount", sent);
        snap.put("totalRoundCount", rounds);
        return snap;
    }

    private long approxPercentile(int p) {
        long count = Math.min(sendLatencyIdx.get(), LATENCY_WINDOW);
        if (count == 0) return 0;

        int window = (int) Math.min(count, LATENCY_WINDOW);
        long[] copy = new long[window];
        System.arraycopy(sendLatencies, 0, copy, 0, window);
        java.util.Arrays.sort(copy);

        int idx = (int) ((p / 100.0) * (window - 1));
        return copy[Math.min(idx, window - 1)];
    }

    public void reset() {
        totalDbLockMs.reset();
        totalDbSelectMs.reset();
        totalMqSendMs.reset();
        totalRoundMs.reset();
        dbLockCount.set(0);
        dbSelectCount.set(0);
        mqSendCount.set(0);
        roundCount.set(0);
        sendLatencyIdx.set(0);
        minSendMs = Long.MAX_VALUE;
        maxSendMs = 0;
        java.util.Arrays.fill(sendLatencies, 0);
    }
}
