package com.executor.xxljobexecutormqimprove.metrics;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MetricsCollector {

    private final AtomicLong tasksProduced = new AtomicLong();
    private final AtomicLong tasksProducedFailed = new AtomicLong();
    private final AtomicLong tasksConsumed = new AtomicLong();
    private final AtomicLong tasksConsumedFailed = new AtomicLong();

    public void recordProduced(boolean success) {
        if (success) {
            tasksProduced.incrementAndGet();
        } else {
            tasksProducedFailed.incrementAndGet();
        }
    }

    public void recordProducedFailed() {
        tasksProducedFailed.incrementAndGet();
    }

    public void recordConsumed(boolean success) {
        if (success) {
            tasksConsumed.incrementAndGet();
        } else {
            tasksConsumedFailed.incrementAndGet();
        }
    }

    public Map<String, Long> snapshot() {
        return Map.of(
                "tasksProduced", tasksProduced.get(),
                "tasksProducedFailed", tasksProducedFailed.get(),
                "tasksConsumed", tasksConsumed.get(),
                "tasksConsumedFailed", tasksConsumedFailed.get()
        );
    }
}
