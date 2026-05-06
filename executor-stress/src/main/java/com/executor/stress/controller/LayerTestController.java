package com.executor.stress.controller;

import com.executor.xxljobexecutormqimprove.Enum.ProcessEnum;
import com.executor.xxljobexecutormqimprove.Enum.ScheduledTypeEnum;
import com.executor.xxljobexecutormqimprove.Enum.TaskEnableEnum;
import com.executor.xxljobexecutormqimprove.core.base.TaskEventLogBaseService;
import com.executor.xxljobexecutormqimprove.core.service.CommonTaskService;
import com.executor.xxljobexecutormqimprove.core.store.TaskStore;
import com.executor.xxljobexecutormqimprove.metrics.MetricsCollector;
import com.executor.xxljobexecutormqimprove.mapper.RealtimeTaskMapper;
import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.model.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.model.entity.RealTimeTaskEntity;
import com.executor.xxljobexecutormqimprove.mq.MessagePublisher;
import com.executor.xxljobexecutormqimprove.util.CronTimeUtil;
import com.executor.xxljobexecutormqimprove.util.ValidateParamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.lang.management.*;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Layered stress test controller — implements the 3-layer methodology:
 *   Layer 1: Mock MQ — pure scheduler kernel throughput
 *   Layer 2: Local MQ — app + local RocketMQ throughput
 *   Layer 3: Full-chain burst + soak stability
 */
@RestController
@RequestMapping("/stress/layer")
public class LayerTestController {

    private static final Logger log = LoggerFactory.getLogger(LayerTestController.class);
    private static final int LIMIT_COUNT = 200;

    @Autowired private TaskStore taskStore;
    @Autowired private MessagePublisher messagePublisher;
    @Autowired private CommonTaskService commonTaskService;
    @Autowired private TaskEventLogBaseService logService;
    @Autowired private MetricsCollector metricsCollector;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private RealtimeTaskMapper realtimeTaskMapper;

    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private volatile boolean soakRunning = false;
    private volatile boolean stairRunning = false;

    // ═══════════════════════════════════════════════════════════════════════
    // Layer 1: Mock MQ / Pure Scheduler Kernel Benchmark
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Benchmark the pure scheduler kernel end-to-end latency and throughput.
     * Creates tasks, then continuously triggers processing. All MQ sends are mocked
     * (instant success) so the result reflects ONLY the scheduler + DB performance.
     *
     * Parameters:
     *   numTasks: total tasks to create (default 5000)
     *   numBizGroups: number of shards (default 5)
     *   cronExpr: cron expression (default "0/1 * * * * ?")
     *   maxRounds: max trigger rounds (default 200)
     */
    @PostMapping("/mock-run")
    public Map<String, Object> mockRun(@RequestBody Map<String, Object> body) {
        int numTasks = getInt(body, "numTasks", 5000);
        int numBizGroups = getInt(body, "numBizGroups", 5);
        String cronExpr = (String) body.getOrDefault("cronExpr", "0/1 * * * * ?");
        String baseBizName = (String) body.getOrDefault("baseBizName", "layer-mock");
        int maxRounds = getInt(body, "maxRounds", 200);

        // Setup phase
        long setupStart = System.currentTimeMillis();
        int created = setupLowFreqTasks(numTasks, numBizGroups, cronExpr, baseBizName, "executorConsumeTask");
        long setupMs = System.currentTimeMillis() - setupStart;

        // Benchmark phase — run all groups
        long benchStart = System.currentTimeMillis();
        int totalProcessed = 0;
        int totalSuccess = 0;
        int totalFail = 0;
        int totalRounds = 0;
        List<Map<String, Object>> groupResults = new ArrayList<>();

        for (int g = 0; g < numBizGroups; g++) {
            String bizParam = baseBizName + "," + "group-" + g;
            Map<String, Object> gr = runSingleGroup(bizParam, maxRounds);
            totalProcessed += (int) gr.get("processed");
            totalSuccess += (int) gr.get("success");
            totalFail += (int) gr.get("fail");
            totalRounds += (int) gr.get("rounds");
            groupResults.add(Map.of(
                    "bizGroup", "group-" + g,
                    "processed", gr.get("processed"),
                    "rounds", gr.get("rounds"),
                    "elapsedMs", gr.get("elapsedMs")
            ));
        }

        long benchMs = System.currentTimeMillis() - benchStart;
        double tps = benchMs > 0 ? (totalProcessed * 1000.0 / benchMs) : 0;

        Map<String, Long> metrics = metricsCollector.snapshot();
        Map<String, Object> sysStatus = collectSystemStatus();

        log.info("Mock-run complete: {} tasks in {}ms, TPS={}", totalProcessed, benchMs, Math.round(tps));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("layer", "Mock MQ — Scheduler Kernel Only");
        result.put("created", created);
        result.put("setupMs", setupMs);
        result.put("benchMs", benchMs);
        result.put("totalProcessed", totalProcessed);
        result.put("mqSuccess", totalSuccess);
        result.put("mqFail", totalFail);
        result.put("totalRounds", totalRounds);
        result.put("tps", Math.round(tps * 100.0) / 100.0);
        result.put("groupResults", groupResults);
        result.put("metrics", metrics);
        result.put("system", sysStatus);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Layer 2: Local MQ Benchmark (real RocketMQ, same machine)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Benchmark with real MQ (local/docker deployment).
     * Same logic as mock-run but uses real RocketMQ sends, measuring full
     * app + local MQ throughput.
     */
    @PostMapping("/local-mq-run")
    public Map<String, Object> localMqRun(@RequestBody Map<String, Object> body) {
        int numTasks = getInt(body, "numTasks", 2000);
        int numBizGroups = getInt(body, "numBizGroups", 3);
        String cronExpr = (String) body.getOrDefault("cronExpr", "0/1 * * * * ?");
        String baseBizName = (String) body.getOrDefault("baseBizName", "layer-local");
        int maxRounds = getInt(body, "maxRounds", 100);

        long setupStart = System.currentTimeMillis();
        int created = setupLowFreqTasks(numTasks, numBizGroups, cronExpr, baseBizName, "executorConsumeTask");
        long setupMs = System.currentTimeMillis() - setupStart;

        long benchStart = System.currentTimeMillis();
        int totalProcessed = 0;
        int totalSuccess = 0;
        int totalFail = 0;
        int totalRounds = 0;
        List<Map<String, Object>> groupResults = new ArrayList<>();

        for (int g = 0; g < numBizGroups; g++) {
            String bizParam = baseBizName + "," + "group-" + g;
            Map<String, Object> gr = runSingleGroup(bizParam, maxRounds);
            totalProcessed += (int) gr.get("processed");
            totalSuccess += (int) gr.get("success");
            totalFail += (int) gr.get("fail");
            totalRounds += (int) gr.get("rounds");
            groupResults.add(Map.of(
                    "bizGroup", "group-" + g,
                    "processed", gr.get("processed"),
                    "rounds", gr.get("rounds"),
                    "elapsedMs", gr.get("elapsedMs")
            ));
        }

        long benchMs = System.currentTimeMillis() - benchStart;
        double tps = benchMs > 0 ? (totalProcessed * 1000.0 / benchMs) : 0;

        Map<String, Long> metrics = metricsCollector.snapshot();
        Map<String, Object> sysStatus = collectSystemStatus();

        log.info("Local-MQ run complete: {} tasks in {}ms, TPS={}, fail={}", totalProcessed, benchMs, Math.round(tps), totalFail);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("layer", "Local MQ — Real RocketMQ (local Docker)");
        result.put("created", created);
        result.put("setupMs", setupMs);
        result.put("benchMs", benchMs);
        result.put("totalProcessed", totalProcessed);
        result.put("mqSuccess", totalSuccess);
        result.put("mqFail", totalFail);
        result.put("totalRounds", totalRounds);
        result.put("tps", Math.round(tps * 100.0) / 100.0);
        result.put("groupResults", groupResults);
        result.put("metrics", metrics);
        result.put("system", sysStatus);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Layer 3a: Stair-Step Pressure Test (find the knee point)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Graduated pressure increase — starts at startTasks and adds stepSize tasks
     * every stepIntervalSeconds. Each step runs a full benchmark round.
     * Stops when errorRate > 1% or when maxTasks is reached.
     *
     * This finds the exact "knee point" where the system starts degrading.
     */
    @PostMapping("/stair-step")
    public Map<String, Object> stairStep(@RequestBody Map<String, Object> body) {
        int startTasks = getInt(body, "startTasks", 100);
        int stepSize = getInt(body, "stepSize", 100);
        int stepIntervalSec = getInt(body, "stepIntervalSeconds", 10);
        int maxTasks = getInt(body, "maxTasks", 5000);
        int numBizGroups = getInt(body, "numBizGroups", 3);
        String cronExpr = (String) body.getOrDefault("cronExpr", "0/1 * * * * ?");
        String baseBizName = (String) body.getOrDefault("baseBizName", "layer-stair");
        int maxRoundsPerStep = getInt(body, "maxRoundsPerStep", 20);
        double failThreshold = body.get("failThreshold") != null
                ? ((Number) body.get("failThreshold")).doubleValue() : 0.01;

        stairRunning = true;

        List<Map<String, Object>> steps = new ArrayList<>();
        int currentStep = 0;
        int cumulativeCreated = 0;
        long totalStart = System.currentTimeMillis();

        for (int tasks = startTasks; tasks <= maxTasks && stairRunning; tasks += stepSize) {
            currentStep++;
            long stepStart = System.currentTimeMillis();

            // Create additional tasks
            int toCreate = (currentStep == 1) ? tasks : stepSize;
            int created = setupLowFreqTasks(toCreate, numBizGroups, cronExpr, baseBizName, "executorConsumeTask");

            // Run one round per group
            int stepProcessed = 0;
            int stepSuccess = 0;
            int stepFail = 0;
            for (int g = 0; g < numBizGroups; g++) {
                String bizParam = baseBizName + "," + "group-" + g;
                Map<String, Object> gr = runSingleGroup(bizParam, maxRoundsPerStep);
                stepProcessed += (int) gr.get("processed");
                stepSuccess += (int) gr.get("success");
                stepFail += (int) gr.get("fail");
            }

            cumulativeCreated += toCreate;
            long stepMs = System.currentTimeMillis() - stepStart;
            double stepTps = stepMs > 0 ? (stepProcessed * 1000.0 / stepMs) : 0;
            double failRate = stepProcessed > 0 ? (double) stepFail / stepProcessed : 0;

            Map<String, Long> snap = metricsCollector.snapshot();
            Map<String, Object> stepData = new LinkedHashMap<>();
            stepData.put("step", currentStep);
            stepData.put("cumulativeTasks", cumulativeCreated);
            stepData.put("stepTasks", toCreate);
            stepData.put("processed", stepProcessed);
            stepData.put("success", stepSuccess);
            stepData.put("fail", stepFail);
            stepData.put("failRate", Math.round(failRate * 10000.0) / 100.0);
            stepData.put("tps", Math.round(stepTps * 100.0) / 100.0);
            stepData.put("elapsedMs", stepMs);
            stepData.put("metrics", snap);
            steps.add(stepData);

            log.info("Stair step {}: {} tasks, TPS={}, failRate={}%",
                    currentStep, cumulativeCreated, Math.round(stepTps), Math.round(failRate * 10000.0) / 100.0);

            // Check stop conditions
            if (failRate > failThreshold) {
                log.warn("Stair-step stopped: failRate {}% > threshold {}%",
                        Math.round(failRate * 10000.0) / 100.0, failThreshold * 100);
                break;
            }

            // Sleep between steps to let system stabilize
            if (tasks + stepSize <= maxTasks && stairRunning) {
                try {
                    Thread.sleep(stepIntervalSec * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        stairRunning = false;
        long totalMs = System.currentTimeMillis() - totalStart;

        // Find the knee point (where TPS stops scaling linearly)
        int kneeStep = findKneePoint(steps);

        return Map.of(
                "layer", "Stair-Step — Find System Knee Point",
                "totalSteps", currentStep,
                "cumulativeTasks", cumulativeCreated,
                "totalMs", totalMs,
                "kneePointStep", kneeStep,
                "kneePointTasks", kneeStep > 0 ? steps.get(kneeStep - 1).get("cumulativeTasks") : 0,
                "steps", steps
        );
    }

    @PostMapping("/stair-step/stop")
    public Map<String, Object> stopStairStep() {
        stairRunning = false;
        return Map.of("success", true, "message", "Stair-step stopped");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Layer 3b: Soak Test (long-duration stability)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Long-duration stability test. Runs at a sustained load for a target duration,
     * periodically reporting metrics. Good for detecting memory leaks, connection
     * pool exhaustion, GC issues.
     *
     * Parameters:
     *   numTasks: tasks to create (default 3000)
     *   numBizGroups: shard count (default 3)
     *   durationMinutes: how long to run (default 30)
     *   reportIntervalSeconds: metrics snapshot interval (default 30)
     *   cronExpr: cron expression
     */
    @PostMapping("/soak")
    public Map<String, Object> soakTest(@RequestBody Map<String, Object> body) {
        int numTasks = getInt(body, "numTasks", 3000);
        int numBizGroups = getInt(body, "numBizGroups", 3);
        int durationMin = getInt(body, "durationMinutes", 30);
        int reportIntervalSec = getInt(body, "reportIntervalSeconds", 30);
        String cronExpr = (String) body.getOrDefault("cronExpr", "0/1 * * * * ?");
        String baseBizName = (String) body.getOrDefault("baseBizName", "layer-soak");

        // Setup
        long setupStart = System.currentTimeMillis();
        int created = setupLowFreqTasks(numTasks, numBizGroups, cronExpr, baseBizName, "executorConsumeTask");
        long setupMs = System.currentTimeMillis() - setupStart;

        soakRunning = true;
        long totalStart = System.currentTimeMillis();
        long durationMs = durationMin * 60_000L;
        long endTime = totalStart + durationMs;

        List<Map<String, Object>> snapshots = new ArrayList<>();
        int totalProcessed = 0;
        int totalSuccess = 0;
        int totalFail = 0;

        Map<String, Long> initialMetrics = metricsCollector.snapshot();
        Map<String, Object> initialSys = collectSystemStatus();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long initialHeapUsed = memoryMXBean.getHeapMemoryUsage().getUsed();

        while (soakRunning && System.currentTimeMillis() < endTime) {
            long snapshotStart = System.currentTimeMillis();

            // Process one round per group
            int batchProcessed = 0;
            int batchSuccess = 0;
            int batchFail = 0;
            for (int g = 0; g < numBizGroups; g++) {
                String bizParam = baseBizName + "," + "group-" + g;
                Map<String, Object> gr = runSingleGroup(bizParam, 5); // small batches for steady flow
                batchProcessed += (int) gr.get("processed");
                batchSuccess += (int) gr.get("success");
                batchFail += (int) gr.get("fail");
            }

            totalProcessed += batchProcessed;
            totalSuccess += batchSuccess;
            totalFail += batchFail;

            long elapsed = System.currentTimeMillis() - totalStart;
            double currentTps = elapsed > 0 ? (totalProcessed * 1000.0 / elapsed) : 0;
            long heapUsed = memoryMXBean.getHeapMemoryUsage().getUsed();

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("elapsedMin", Math.round(elapsed / 60000.0 * 100.0) / 100.0);
            snapshot.put("batchProcessed", batchProcessed);
            snapshot.put("batchSuccess", batchSuccess);
            snapshot.put("batchFail", batchFail);
            snapshot.put("cumulativeProcessed", totalProcessed);
            snapshot.put("cumulativeSuccess", totalSuccess);
            snapshot.put("cumulativeFail", totalFail);
            snapshot.put("currentTps", Math.round(currentTps * 100.0) / 100.0);
            snapshot.put("heapUsedMB", Math.round(heapUsed / 1048576.0 * 100.0) / 100.0);
            snapshot.put("heapDeltaMB", Math.round((heapUsed - initialHeapUsed) / 1048576.0 * 100.0) / 100.0);
            snapshot.put("dbConnections", getDbConnectionCount());
            snapshot.put("threadCount", Thread.activeCount());
            snapshots.add(snapshot);

            log.info("Soak [{}min]: processed={}, success={}, fail={}, TPS={}, heap={}MB",
                    Math.round(elapsed / 60000.0), totalProcessed, totalSuccess, totalFail,
                    Math.round(currentTps), Math.round(heapUsed / 1048576.0));

            // Wait for next report interval, minus the processing time
            long processMs = System.currentTimeMillis() - snapshotStart;
            long sleepMs = reportIntervalSec * 1000L - processMs;
            if (sleepMs > 0 && System.currentTimeMillis() < endTime && soakRunning) {
                try {
                    Thread.sleep(Math.min(sleepMs, 5000)); // max 5s check intervals
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        soakRunning = false;
        long totalMs = System.currentTimeMillis() - totalStart;
        double avgTps = totalMs > 0 ? (totalProcessed * 1000.0 / totalMs) : 0;
        long finalHeapUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
        long heapGrowth = finalHeapUsed - initialHeapUsed;

        Map<String, Long> finalMetrics = metricsCollector.snapshot();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("layer", "Soak Test — Long-Duration Stability");
        result.put("created", created);
        result.put("setupMs", setupMs);
        result.put("durationTargetMin", durationMin);
        result.put("actualDurationMs", totalMs);
        result.put("totalProcessed", totalProcessed);
        result.put("totalSuccess", totalSuccess);
        result.put("totalFail", totalFail);
        result.put("failRate", Math.round((totalFail * 10000.0 / Math.max(totalProcessed, 1))) / 100.0);
        result.put("avgTps", Math.round(avgTps * 100.0) / 100.0);
        result.put("initialMetrics", initialMetrics);
        result.put("finalMetrics", finalMetrics);
        result.put("heapGrowthMB", Math.round(heapGrowth / 1048576.0 * 100.0) / 100.0);
        result.put("initialSystem", initialSys);
        result.put("snapshots", snapshots);
        return result;
    }

    @PostMapping("/soak/stop")
    public Map<String, Object> stopSoak() {
        soakRunning = false;
        return Map.of("success", true, "message", "Soak test stopped");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Burst Test (one-shot high-volume injection)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Burst test — inject a large number of tasks at once and observe system
     * behavior (queue buildup, MQ throttling, DB deadlock, misfire rate).
     */
    @PostMapping("/burst")
    public Map<String, Object> burstTest(@RequestBody Map<String, Object> body) {
        int numTasks = getInt(body, "numTasks", 50000);
        int numBizGroups = getInt(body, "numBizGroups", 10);
        String cronExpr = (String) body.getOrDefault("cronExpr", "0/1 * * * * ?");
        String baseBizName = (String) body.getOrDefault("baseBizName", "layer-burst");
        int maxRounds = getInt(body, "maxRounds", 500);

        long setupStart = System.currentTimeMillis();
        int created = setupLowFreqTasks(numTasks, numBizGroups, cronExpr, baseBizName, "executorConsumeTask");
        long setupMs = System.currentTimeMillis() - setupStart;

        // Immediately run all groups concurrently
        long runStart = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(numBizGroups);
        AtomicInteger totalProc = new AtomicInteger();
        AtomicInteger totalSucc = new AtomicInteger();
        AtomicInteger totalF = new AtomicInteger();
        ConcurrentLinkedQueue<Map<String, Object>> concurrentResults = new ConcurrentLinkedQueue<>();

        for (int g = 0; g < numBizGroups; g++) {
            final int groupIdx = g;
            virtualThreadExecutor.submit(() -> {
                try {
                    String bizParam = baseBizName + "," + "group-" + groupIdx;
                    Map<String, Object> gr = runSingleGroup(bizParam, maxRounds);
                    totalProc.addAndGet((int) gr.get("processed"));
                    totalSucc.addAndGet((int) gr.get("success"));
                    totalF.addAndGet((int) gr.get("fail"));
                    concurrentResults.add(Map.of(
                            "bizGroup", "group-" + groupIdx,
                            "processed", gr.get("processed"),
                            "rounds", gr.get("rounds"),
                            "elapsedMs", gr.get("elapsedMs")
                    ));
                } catch (Exception e) {
                    log.error("Burst group-{} failed", groupIdx, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long runMs = System.currentTimeMillis() - runStart;
        int processed = totalProc.get();
        int success = totalSucc.get();
        int fail = totalF.get();
        double tps = runMs > 0 ? (processed * 1000.0 / runMs) : 0;
        double failRate = processed > 0 ? (fail * 100.0 / processed) : 0;

        Map<String, Long> metrics = metricsCollector.snapshot();
        Map<String, Object> sysStatus = collectSystemStatus();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("layer", "Burst Test — High-Volume Injection");
        result.put("created", created);
        result.put("setupMs", setupMs);
        result.put("runMs", runMs);
        result.put("totalProcessed", processed);
        result.put("success", success);
        result.put("fail", fail);
        result.put("failRate", Math.round(failRate * 100.0) / 100.0);
        result.put("tps", Math.round(tps * 100.0) / 100.0);
        result.put("groupResults", new ArrayList<>(concurrentResults));
        result.put("metrics", metrics);
        result.put("system", sysStatus);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // System Status (richer than /stress/metrics)
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/status")
    public Map<String, Object> layerStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("app", "executor-stress");
        status.put("timestamp", System.currentTimeMillis());

        // DB health
        try (Connection c = dataSource.getConnection()) {
            status.put("db", c.isValid(3) ? "UP" : "DOWN");
        } catch (Exception e) {
            status.put("db", "DOWN: " + e.getMessage());
        }

        // MQ mode
        status.put("mqMode", messagePublisher.getClass().getSimpleName());

        // JVM metrics
        status.put("system", collectSystemStatus());

        // Business metrics
        status.put("metrics", metricsCollector.snapshot());

        // Soak/stair status
        status.put("soakRunning", soakRunning);
        status.put("stairRunning", stairRunning);

        return status;
    }

    // ════════════════════════════════════════════════
    // Time-Wheel (RealtimeTask / 高频) Tests
    // ════════════════════════════════════════════════

    @PostMapping("/time-wheel/mock-run")
    public Map<String, Object> timeWheelMockRun(@RequestBody Map<String, Object> body) {
        int numTasks = getInt(body, "numTasks", 500);
        String cronExpr = (String) body.getOrDefault("cronExpr", "0/1 * * * * ?");
        String bizName = (String) body.getOrDefault("bizName", "tw-mock");
        String bizGroup = (String) body.getOrDefault("bizGroup", "hft");
        int observeSeconds = getInt(body, "observeSeconds", 30);
        return runTimeWheelBenchmark(numTasks, cronExpr, bizName, bizGroup, observeSeconds,
                "Time-Wheel Mock MQ — Ring Scheduling Only");
    }

    @PostMapping("/time-wheel/local-mq-run")
    public Map<String, Object> timeWheelLocalMqRun(@RequestBody Map<String, Object> body) {
        int numTasks = getInt(body, "numTasks", 500);
        String cronExpr = (String) body.getOrDefault("cronExpr", "0/1 * * * * ?");
        String bizName = (String) body.getOrDefault("bizName", "tw-local");
        String bizGroup = (String) body.getOrDefault("bizGroup", "hft");
        int observeSeconds = getInt(body, "observeSeconds", 30);
        return runTimeWheelBenchmark(numTasks, cronExpr, bizName, bizGroup, observeSeconds,
                "Time-Wheel Local MQ — Ring + Real RocketMQ");
    }

    @PostMapping("/time-wheel/burst")
    public Map<String, Object> timeWheelBurst(@RequestBody Map<String, Object> body) {
        int numTasks = getInt(body, "numTasks", 10000);
        String cronExpr = (String) body.getOrDefault("cronExpr", "0/1 * * * * ?");
        String bizName = (String) body.getOrDefault("bizName", "tw-burst");
        String bizGroup = (String) body.getOrDefault("bizGroup", "hft");
        int observeSeconds = getInt(body, "observeSeconds", 60);
        return runTimeWheelBenchmark(numTasks, cronExpr, bizName, bizGroup, observeSeconds,
                "Time-Wheel Burst — High-Volume Ring Injection");
    }

    @GetMapping("/time-wheel/status")
    public Map<String, Object> timeWheelStatus(
            @RequestParam(defaultValue = "tw-mock") String bizName,
            @RequestParam(defaultValue = "hft") String bizGroup) {
        long now = System.currentTimeMillis();
        List<RealTimeTaskEntity> schedulable = realtimeTaskMapper.selectSchedulableTasks(
                now + 60000, 10000);
        long pending = schedulable.stream()
                .filter(t -> bizName.equals(t.getBizName()) && bizGroup.equals(t.getBizGroup()))
                .count();
        return Map.of("bizName", bizName, "bizGroup", bizGroup,
                "pendingTasks", pending, "metrics", metricsCollector.snapshot(),
                "system", collectSystemStatus());
    }

    private Map<String, Object> runTimeWheelBenchmark(int numTasks, String cronExpr,
                                                       String bizName, String bizGroup,
                                                       int observeSeconds, String layerLabel) {
        Map<String, Long> baselineMetrics = metricsCollector.snapshot();
        long setupStart = System.currentTimeMillis();
        LocalDateTime nowDt = LocalDateTime.now();
        long nextTrigger;
        try {
            nextTrigger = CronTimeUtil.getNextTriggerTime(cronExpr, setupStart);
        } catch (java.text.ParseException e) {
            return Map.of("success", false, "error", "Invalid cron: " + e.getMessage());
        }

        for (int i = 0; i < numTasks; i++) {
            RealTimeTaskEntity e = new RealTimeTaskEntity();
            e.setId(UUID.randomUUID().toString().replace("-", ""));
            e.setTaskId(bizName + "-tw-" + i);
            e.setTaskName(bizName + "-" + i);
            e.setBizName(bizName);
            e.setBizGroup(bizGroup);
            e.setNextTriggerTime(nextTrigger);
            e.setScheduledConf(cronExpr);
            e.setScheduledType(ScheduledTypeEnum.SCHEDULED_CRON);
            e.setCreateAt(nowDt);
            e.setUpdateAt(nowDt);
            e.setEnable(TaskEnableEnum.TASK_ENABLE);
            e.setPayload("{\"type\":\"timewheel\",\"index\":" + i + "}");
            e.setTopic("executorConsumeTask");
            e.setProcess(ProcessEnum.PENDING);
            realtimeTaskMapper.upsetTaskInfo(e);
        }
        long setupMs = System.currentTimeMillis() - setupStart;
        log.info("TW setup: {} tasks in {}ms", numTasks, setupMs);

        List<Map<String, Object>> timeline = new ArrayList<>();
        long observeStart = System.currentTimeMillis();
        long observeEnd = observeStart + observeSeconds * 1000L;
        long prevProduced = baselineMetrics.getOrDefault("tasksProduced", 0L);
        long prevTime = observeStart;
        int sampleInterval = observeSeconds <= 30 ? 3 : 5;

        while (System.currentTimeMillis() < observeEnd) {
            try { Thread.sleep(sampleInterval * 1000L); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
            long sampleTime = System.currentTimeMillis();
            Map<String, Long> snap = metricsCollector.snapshot();
            long curProduced = snap.getOrDefault("tasksProduced", 0L);
            long deltaProduced = curProduced - prevProduced;
            double deltaSec = (sampleTime - prevTime) / 1000.0;
            double instantTps = deltaSec > 0 ? deltaProduced / deltaSec : 0;

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("elapsedSec", (int) ((sampleTime - observeStart) / 1000));
            point.put("deltaProduced", deltaProduced);
            point.put("instantTps", Math.round(instantTps * 100.0) / 100.0);
            point.put("cumulativeProduced", curProduced);
            timeline.add(point);
            prevProduced = curProduced;
            prevTime = sampleTime;
            log.info("TW [{}s]: +{} produced, TPS={}", point.get("elapsedSec"), deltaProduced, Math.round(instantTps));
        }

        long totalMs = System.currentTimeMillis() - observeStart;
        Map<String, Long> finalMetrics = metricsCollector.snapshot();
        long totalProduced = finalMetrics.getOrDefault("tasksProduced", 0L) - baselineMetrics.getOrDefault("tasksProduced", 0L);
        long totalFailed = finalMetrics.getOrDefault("tasksProducedFailed", 0L) - baselineMetrics.getOrDefault("tasksProducedFailed", 0L);
        double avgTps = totalMs > 0 ? (totalProduced * 1000.0 / totalMs) : 0;
        double peakTps = timeline.stream().mapToDouble(p -> ((Number) p.get("instantTps")).doubleValue()).max().orElse(0);

        List<RealTimeTaskEntity> remaining = realtimeTaskMapper.selectSchedulableTasks(
                System.currentTimeMillis() + 60000, 10000);
        long pendingCount = remaining.stream()
                .filter(t -> bizName.equals(t.getBizName()) && bizGroup.equals(t.getBizGroup())).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("layer", layerLabel);
        result.put("created", numTasks);
        result.put("setupMs", setupMs);
        result.put("observeSec", observeSeconds);
        result.put("actualObserveMs", totalMs);
        result.put("totalProduced", totalProduced);
        result.put("totalFailed", totalFailed);
        result.put("failRate", totalProduced > 0 ? Math.round(totalFailed * 10000.0 / totalProduced) / 100.0 : 0);
        result.put("avgTps", Math.round(avgTps * 100.0) / 100.0);
        result.put("peakTps", Math.round(peakTps * 100.0) / 100.0);
        result.put("pendingRemaining", pendingCount);
        result.put("system", collectSystemStatus());
        result.put("timeline", timeline);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Cleanup for layer tests
    // ═══════════════════════════════════════════════════════════════════════

    @DeleteMapping("/cleanup")
    public Map<String, Object> layerCleanup(
            @RequestParam(defaultValue = "5") int numBizGroups,
            @RequestBody(required = false) Map<String, Object> body) {
        String[] prefixes = {"layer-mock", "layer-local", "layer-stair", "layer-soak", "layer-burst",
                "tw-mock", "tw-local", "tw-burst", "mq-test"};
        int totalDeleted = 0;

        try (Connection conn = dataSource.getConnection()) {
            for (String prefix : prefixes) {
                for (int g = 0; g < numBizGroups; g++) {
                    String bizGroup = "group-" + g;
                    try (var ps = conn.prepareStatement(
                            "DELETE FROM user_scheduled_common_task WHERE biz_name=? AND biz_group=?")) {
                        ps.setString(1, prefix);
                        ps.setString(2, bizGroup);
                        totalDeleted += ps.executeUpdate();
                    }
                }
            }
            // Clean event logs for layer tests
            try (var ps = conn.prepareStatement(
                    "DELETE FROM task_event_log WHERE task_name LIKE 'layer-%' OR task_name LIKE 'tw-%' OR task_name LIKE 'mq-test%'")) {
                ps.executeUpdate();
            }
            // Clean time-wheel tasks
            try (var ps = conn.prepareStatement(
                    "DELETE FROM user_scheduled_realtime_task WHERE biz_name LIKE 'tw-%'")) {
                ps.executeUpdate();
            }
        } catch (Exception e) {
            log.error("Layer cleanup failed", e);
            return Map.of("success", false, "error", e.getMessage());
        }

        return Map.of("success", true, "deletedTasks", totalDeleted);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═══════════════════════════════════════════════════════════════════════

    private int setupLowFreqTasks(int numTasks, int numBizGroups, String cronExpr,
                                   String baseBizName, String topic) {
        long now = System.currentTimeMillis();
        LocalDateTime nowDt = LocalDateTime.now();
        long nextTrigger;
        try {
            nextTrigger = CronTimeUtil.getNextTriggerTime(cronExpr, now);
        } catch (java.text.ParseException e) {
            log.error("Invalid cron expression: {}", cronExpr, e);
            return 0;
        }

        int tasksPerGroup = numTasks / numBizGroups;
        int totalCreated = 0;

        for (int g = 0; g < numBizGroups; g++) {
            String bizGroup = "group-" + g;
            for (int i = 0; i < tasksPerGroup; i++) {
                String id = UUID.randomUUID().toString().replace("-", "");
                CommonTaskEntity entity = new CommonTaskEntity();
                entity.setId(id);
                entity.setTaskId(baseBizName + "-" + bizGroup + "-task-" + i);
                entity.setTaskName(baseBizName + "-" + g + "-" + i);
                entity.setBizName(baseBizName);
                entity.setBizGroup(bizGroup);
                entity.setNextTriggerTime(nextTrigger);
                entity.setScheduledConf(cronExpr);
                entity.setScheduledType(ScheduledTypeEnum.SCHEDULED_CRON);
                entity.setCreateAt(nowDt);
                entity.setUpdateAt(nowDt);
                entity.setEnable(TaskEnableEnum.TASK_ENABLE);
                entity.setPayload("{\"stressTest\":true,\"type\":\"layer\",\"index\":" + i + "}");
                entity.setTopic(topic);
                entity.setProcess(ProcessEnum.PENDING);
                taskStore.upsetTask(entity);
                totalCreated++;
            }
        }
        log.info("Layer setup: {} tasks ({} groups × {} each)", totalCreated, numBizGroups, tasksPerGroup);
        return totalCreated;
    }

    /**
     * Run a single biz group continuously until no more pending tasks or maxRounds reached.
     */
    private Map<String, Object> runSingleGroup(String bizParam, int maxRounds) {
        String[] parts = ValidateParamUtil.validateAndParseJobParam(bizParam);
        String bizName = parts[0];
        String bizGroup = parts[1];

        long start = System.currentTimeMillis();
        int processed = 0;
        int success = 0;
        int fail = 0;
        int rounds = 0;

        for (int r = 0; r < maxRounds; r++) {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setName("stressLockData");
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            TransactionStatus status = transactionManager.getTransaction(def);

            List<ProduceCommonTaskMessage> tasks;
            List<String> ids;
            try {
                tasks = taskStore.lockAndSelectTasks(bizName, bizGroup, System.currentTimeMillis(), LIMIT_COUNT);
                if (tasks.isEmpty()) {
                    transactionManager.rollback(status);
                    break;
                }
                ids = tasks.stream().map(ProduceCommonTaskMessage::getId).collect(Collectors.toList());
                taskStore.lockTaskById(ids);
                transactionManager.commit(status);
            } catch (Exception e) {
                transactionManager.rollback(status);
                log.error("Layer run round {} failed for {}/{}", r, bizName, bizGroup, e);
                break;
            }

            List<Future<Boolean>> futures = new ArrayList<>();
            List<String> attemptedIds = new ArrayList<>();
            for (ProduceCommonTaskMessage task : tasks) {
                attemptedIds.add(task.getId());
                futures.add(virtualThreadExecutor.submit(() -> {
                    boolean ok = messagePublisher.send(task);
                    metricsCollector.recordProduced(ok);
                    return ok;
                }));
            }

            List<ProduceCommonTaskMessage> successTasks = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    if (futures.get(i).get()) {
                        successTasks.add(tasks.get(i));
                        success++;
                    } else {
                        fail++;
                    }
                } catch (Exception e) {
                    fail++;
                }
            }
            if (!successTasks.isEmpty()) {
                commonTaskService.batchChangeTaskInfo(successTasks);
            }

            taskStore.unlockTasks(attemptedIds);
            processed += tasks.size();
            rounds++;
        }

        long elapsed = System.currentTimeMillis() - start;
        return Map.of("processed", processed, "success", success, "fail", fail,
                "rounds", rounds, "elapsedMs", elapsed);
    }

    private Map<String, Object> collectSystemStatus() {
        Map<String, Object> sys = new LinkedHashMap<>();
        Runtime rt = Runtime.getRuntime();
        long totalMem = rt.totalMemory();
        long freeMem = rt.freeMemory();
        sys.put("availableProcessors", rt.availableProcessors());
        sys.put("heapUsedMB", (totalMem - freeMem) / 1048576L);
        sys.put("heapMaxMB", rt.maxMemory() / 1048576L);
        sys.put("threadCount", Thread.activeCount());
        sys.put("dbConnections", getDbConnectionCount());

        // GC stats
        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += gcBean.getCollectionCount();
            gcTime += gcBean.getCollectionTime();
        }
        sys.put("gcCount", gcCount);
        sys.put("gcTimeMs", gcTime);
        return sys;
    }

    private int getDbConnectionCount() {
        try {
            if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikari) {
                return hikari.getHikariPoolMXBean().getActiveConnections();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private int getInt(Map<String, Object> body, String key, int defaultValue) {
        return body.get(key) != null ? ((Number) body.get(key)).intValue() : defaultValue;
    }

    /**
     * Find the knee point in stair-step results — the step where TPS
     * stops scaling linearly with task count (elbow detection).
     */
    private int findKneePoint(List<Map<String, Object>> steps) {
        if (steps.size() < 3) return 0;

        // Find where TPS increment drops below 50% of previous increment
        for (int i = 2; i < steps.size(); i++) {
            double tps1 = ((Number) steps.get(i - 2).get("tps")).doubleValue();
            double tps2 = ((Number) steps.get(i - 1).get("tps")).doubleValue();
            double tps3 = ((Number) steps.get(i).get("tps")).doubleValue();

            double delta1 = tps2 - tps1;
            double delta2 = tps3 - tps2;

            // TPS stopped growing or started declining while load increases
            if (delta1 > 0 && delta2 < delta1 * 0.3) {
                return i; // knee at step i
            }
        }
        return 0; // no clear knee — system didn't saturate
    }
}
