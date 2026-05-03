package com.executor.stress.mq;

import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.mq.MessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mock MQ publisher — returns success instantly (or with configurable delay).
 * Used in Layer 1 to measure pure scheduler throughput without real MQ overhead.
 */
public class MockMQMessagePublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(MockMQMessagePublisher.class);
    private static final int P99_LOG_INTERVAL = 1000;

    private final long delayNs;
    private long count;
    private long lastLogTime = System.currentTimeMillis();

    public MockMQMessagePublisher() {
        this(0);
    }

    /**
     * @param delayMs simulated send delay in milliseconds (0 = instant)
     */
    public MockMQMessagePublisher(long delayMs) {
        this.delayNs = delayMs * 1_000_000L;
    }

    @Override
    public boolean send(ProduceCommonTaskMessage task) {
        if (delayNs > 0) {
            long start = System.nanoTime();
            while (System.nanoTime() - start < delayNs) {
                // busy-wait for precise delay simulation
            }
        }
        count++;
        if (count % P99_LOG_INTERVAL == 0) {
            long elapsed = System.currentTimeMillis() - lastLogTime;
            log.info("MockMQ sent {} total, last {} in {}ms ({}/s)",
                    count, P99_LOG_INTERVAL, elapsed,
                    elapsed > 0 ? P99_LOG_INTERVAL * 1000L / elapsed : 0);
            lastLogTime = System.currentTimeMillis();
        }
        return true;
    }

    public long getCount() {
        return count;
    }
}
