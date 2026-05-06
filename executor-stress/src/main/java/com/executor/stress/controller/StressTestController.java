package com.executor.stress.controller;

import com.executor.xxljobexecutormqimprove.Enum.ProcessEnum;
import com.executor.xxljobexecutormqimprove.Enum.ScheduledTypeEnum;
import com.executor.xxljobexecutormqimprove.Enum.TaskEnableEnum;
import com.executor.xxljobexecutormqimprove.core.base.TaskEventLogBaseService;
import com.executor.xxljobexecutormqimprove.core.service.CommonTaskService;
import com.executor.xxljobexecutormqimprove.core.store.TaskStore;
import com.executor.xxljobexecutormqimprove.mapper.RealtimeTaskMapper;
import com.executor.xxljobexecutormqimprove.metrics.MetricsCollector;
import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.model.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.model.entity.RealTimeTaskEntity;
import com.executor.xxljobexecutormqimprove.model.entity.TaskEventLog;
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
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/stress")
public class StressTestController {

    private static final Logger log = LoggerFactory.getLogger(StressTestController.class);
    private static final int LIMIT_COUNT = 200;

    @Autowired
    private TaskStore taskStore;

    @Autowired
    private MessagePublisher messagePublisher;

    @Autowired
    private CommonTaskService commonTaskService;

    @Autowired
    private TaskEventLogBaseService logService;

    @Autowired
    private MetricsCollector metricsCollector;

    @Autowired
    private RealtimeTaskMapper realtimeTaskMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private ExecutorService virtualThreadExecutor;

    {
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }


    // ════════════════════════════════════════════════════════════════════
    // Health
    // ════════════════════════════════════════════════════════════════════

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("app", "executor-stress");
        try (Connection c = dataSource.getConnection()) {
            result.put("db", c.isValid(3) ? "UP" : "DOWN");
        } catch (Exception e) {
            result.put("db", "DOWN: " + e.getMessage());
        }
        return result;
    }



    // ════════════════════════════════════════════════════════════════════
    // Low-Frequency Stress Test (CommonTask / XXL-Job 调度模拟)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Setup low-frequency tasks. Tasks are inserted as cron-type with
     * scheduledConf = "0/1 * * * * ?" (every second), so they are immediately
     * eligible on the next trigger cycle. Multiple bizName/bizGroup combinations
     * are created equally to simulate sharding.
     */
    @PostMapping("/low-freq/setup")
    public Map<String, Object> setupLowFreq(@RequestBody Map<String, Object> body) {
        int numTasks = body.get("numTasks") != null ? ((Number) body.get("numTasks")).intValue() : 1000;
        int numBizGroups = body.get("numBizGroups") != null ? ((Number) body.get("numBizGroups")).intValue() : 3;
        String cronExpr = (String) body.getOrDefault("cronExpr", "0/1 * * * * ?");
        String baseBizName = (String) body.getOrDefault("baseBizName", "stress-test");
        String topic = (String) body.getOrDefault("topic", "executorConsumeTask");

        long now = System.currentTimeMillis();
        LocalDateTime nowDt = LocalDateTime.now();
        long nextTrigger;
        try {
            nextTrigger = CronTimeUtil.getNextTriggerTime(cronExpr, now);
        } catch (Exception e) {
            return Map.of("success", false, "error", "Invalid cron: " + e.getMessage());
        }

        int tasksPerGroup = numTasks / numBizGroups;
        int totalCreated = 0;
        List<String> bizCombinations = new ArrayList<>();

        for (int g = 0; g < numBizGroups; g++) {
            String bizGroup = "group-" + g;
            bizCombinations.add(baseBizName + "," + bizGroup);

            for (int i = 0; i < tasksPerGroup; i++) {
                String id = UUID.randomUUID().toString().replace("-", "");
                CommonTaskEntity entity = new CommonTaskEntity();
                entity.setId(id);
                entity.setTaskId(baseBizName + "-" + bizGroup + "-task-" + i);
                entity.setTaskName("stress-low-" + g + "-" + i);
                entity.setBizName(baseBizName);
                entity.setBizGroup(bizGroup);
                entity.setNextTriggerTime(nextTrigger);
                entity.setScheduledConf(cronExpr);
                entity.setScheduledType(ScheduledTypeEnum.SCHEDULED_CRON);
                entity.setCreateAt(nowDt);
                entity.setUpdateAt(nowDt);
                entity.setEnable(TaskEnableEnum.TASK_ENABLE);
                entity.setPayload("{\"stressTest\":true,\"index\":" + i + "}");
                entity.setTopic(topic);
                entity.setProcess(ProcessEnum.PENDING);

                taskStore.upsetTask(entity);
                totalCreated++;
            }
        }

        log.info("Low-freq setup complete: {} tasks across {} biz groups", totalCreated, numBizGroups);
        return Map.of(
                "success", true,
                "totalCreated", totalCreated,
                "numBizGroups", numBizGroups,
                "tasksPerGroup", tasksPerGroup,
                "bizCombinations", bizCombinations,
                "cronExpr", cronExpr
        );
    }

    /**
     * Trigger low-frequency processing for a specific bizName/bizGroup.
     * This directly replicates ProducerHandler logic without requiring XXL-Job Admin.
     */
    @PostMapping("/low-freq/trigger")
    public Map<String, Object> triggerLowFreq(@RequestBody Map<String, Object> body) {
        String bizParam = (String) body.get("bizParam");
        if (bizParam == null || !bizParam.contains(",")) {
            return Map.of("success", false, "error", "bizParam required, format: bizName,bizGroup");
        }

        String[] parts = ValidateParamUtil.validateAndParseJobParam(bizParam);
        String bizName = parts[0];
        String bizGroup = parts[1];
        int shardIndex = body.get("shardIndex") != null ? ((Number) body.get("shardIndex")).intValue() : -1;
        int shardTotal = body.get("shardTotal") != null ? ((Number) body.get("shardTotal")).intValue() : -1;

        long startTime = System.currentTimeMillis();
        long now = startTime;

        // Transaction: lock and select
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("stressLockData");
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = transactionManager.getTransaction(def);

        List<ProduceCommonTaskMessage> tasks;
        List<String> ids;
        try {
            if (shardIndex == -1 || shardTotal == -1) {
                tasks = taskStore.lockAndSelectTasks(bizName, bizGroup, now, LIMIT_COUNT);
            } else {
                tasks = taskStore.lockAndSelectTasksByShard(bizName, bizGroup, now, LIMIT_COUNT, shardTotal, shardIndex);
            }

            if (tasks.isEmpty()) {
                transactionManager.rollback(status);
                return Map.of("success", true, "processed", 0, "message", "No pending tasks");
            }

            ids = tasks.stream().map(ProduceCommonTaskMessage::getId).collect(Collectors.toList());
            taskStore.lockTaskById(ids);
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
            log.error("Low-freq trigger transaction failed", e);
            return Map.of("success", false, "error", e.getMessage());
        }

        // Send to MQ via virtual threads
        List<Future<Boolean>> futures = new ArrayList<>();
        List<String> attemptedIds = new ArrayList<>();
        for (ProduceCommonTaskMessage task : tasks) {
            attemptedIds.add(task.getId());
            futures.add(virtualThreadExecutor.submit(() -> {
                boolean isSuccess = messagePublisher.send(task);
                metricsCollector.recordProduced(isSuccess);
                return isSuccess;
            }));
        }

        // Wait for all sends
        int successCount = 0;
        int failCount = 0;
        List<ProduceCommonTaskMessage> successTasks = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                if (futures.get(i).get()) {
                    successTasks.add(tasks.get(i));
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                failCount++;
                log.error("MQ send failed", e);
            }
        }
        if (!successTasks.isEmpty()) {
            commonTaskService.batchChangeTaskInfo(successTasks);
        }

        // Unlock
        taskStore.unlockTasks(attemptedIds);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Low-freq trigger complete: biz={},{} processed={} success={} fail={} elapsed={}ms",
                bizName, bizGroup, tasks.size(), successCount, failCount, elapsed);

        return Map.of(
                "success", true,
                "bizName", bizName,
                "bizGroup", bizGroup,
                "processed", tasks.size(),
                "mqSuccess", successCount,
                "mqFail", failCount,
                "elapsedMs", elapsed
        );
    }

    /**
     * Batch trigger all tasks in a loop until no more pending tasks remain.
     * This simulates continuous XXL-Job Admin scheduling.
     */
    @PostMapping("/low-freq/run")
    public Map<String, Object> runLowFreq(@RequestBody Map<String, Object> body) {
        String bizParam = (String) body.get("bizParam");
        if (bizParam == null || !bizParam.contains(",")) {
            return Map.of("success", false, "error", "bizParam required, format: bizName,bizGroup");
        }

        String[] parts = ValidateParamUtil.validateAndParseJobParam(bizParam);
        String bizName = parts[0];
        String bizGroup = parts[1];
        int maxRounds = body.get("maxRounds") != null ? ((Number) body.get("maxRounds")).intValue() : 100;

        long totalStart = System.currentTimeMillis();
        int totalProcessed = 0;
        int totalSuccess = 0;
        int totalFail = 0;
        int rounds = 0;

        for (int r = 0; r < maxRounds; r++) {
            // Use a simpler path: directly lock-select-send-unlock as a single flow
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
                log.error("Run round {} failed", r, e);
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
                        totalSuccess++;
                    } else {
                        totalFail++;
                    }
                } catch (Exception e) {
                    totalFail++;
                }
            }
            if (!successTasks.isEmpty()) {
                commonTaskService.batchChangeTaskInfo(successTasks);
            }

            taskStore.unlockTasks(attemptedIds);
            totalProcessed += tasks.size();
            rounds++;
        }

        long totalElapsed = System.currentTimeMillis() - totalStart;
        double tps = totalElapsed > 0 ? (totalProcessed * 1000.0 / totalElapsed) : 0;

        log.info("Low-freq run complete: {} tasks in {} rounds, {}ms, TPS={}", totalProcessed, rounds, totalElapsed, tps);

        return Map.of(
                "success", true,
                "bizName", bizName,
                "bizGroup", bizGroup,
                "totalProcessed", totalProcessed,
                "mqSuccess", totalSuccess,
                "mqFail", totalFail,
                "rounds", rounds,
                "elapsedMs", totalElapsed,
                "tps", Math.round(tps * 100.0) / 100.0
        );
    }

    /**
     * Low-freq status: count tasks by process status for each biz combination.
     */
    @GetMapping("/low-freq/status")
    public Map<String, Object> lowFreqStatus(
            @RequestParam(defaultValue = "stress-test") String baseBizName,
            @RequestParam(defaultValue = "5") int numBizGroups) {

        List<Map<String, Object>> groupDetails = new ArrayList<>();
        int total = 0;

        for (int g = 0; g < numBizGroups; g++) {
            String bizGroup = "group-" + g;
            // Count pending tasks for this biz combination
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            TransactionStatus status = transactionManager.getTransaction(def);
            try {
                List<ProduceCommonTaskMessage> pending = taskStore.lockAndSelectTasks(
                        baseBizName, bizGroup, System.currentTimeMillis() + 60000, LIMIT_COUNT);
                transactionManager.rollback(status);
                int count = pending.size();
                total += count;
                groupDetails.add(Map.of("bizName", baseBizName, "bizGroup", bizGroup, "pendingTasks", count));
            } catch (Exception e) {
                transactionManager.rollback(status);
                groupDetails.add(Map.of("bizName", baseBizName, "bizGroup", bizGroup, "error", e.getMessage()));
            }
        }

        return Map.of("totalPending", total, "groups", groupDetails);
    }

    // ════════════════════════════════════════════════════════════════════
    // High-Frequency Stress Test (RealtimeTask / Time Wheel)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Setup high-frequency realtime tasks. These are inserted directly into
     * user_scheduled_realtime_task and will be automatically picked up by
     * SchedulerRealtimeService's time wheel (no XXL-Job Admin needed).
     */
    @PostMapping("/high-freq/setup")
    public Map<String, Object> setupHighFreq(@RequestBody Map<String, Object> body) {
        int numTasks = body.get("numTasks") != null ? ((Number) body.get("numTasks")).intValue() : 1000;
        String cronExpr = (String) body.getOrDefault("cronExpr", "0/1 * * * * ?");
        String bizName = (String) body.getOrDefault("bizName", "stress-realtime");
        String bizGroup = (String) body.getOrDefault("bizGroup", "hft");
        String topic = (String) body.getOrDefault("topic", "executorConsumeTask");

        long now = System.currentTimeMillis();
        LocalDateTime nowDt = LocalDateTime.now();
        long nextTrigger;
        try {
            nextTrigger = CronTimeUtil.getNextTriggerTime(cronExpr, now);
        } catch (Exception e) {
            return Map.of("success", false, "error", "Invalid cron: " + e.getMessage());
        }

        int totalCreated = 0;
        for (int i = 0; i < numTasks; i++) {
            String id = UUID.randomUUID().toString().replace("-", "");
            RealTimeTaskEntity entity = new RealTimeTaskEntity();
            entity.setId(id);
            entity.setTaskId(bizName + "-realtime-" + i);
            entity.setTaskName("stress-high-" + i);
            entity.setBizName(bizName);
            entity.setBizGroup(bizGroup);
            entity.setNextTriggerTime(nextTrigger);
            entity.setScheduledConf(cronExpr);
            entity.setScheduledType(ScheduledTypeEnum.SCHEDULED_CRON);
            entity.setCreateAt(nowDt);
            entity.setUpdateAt(nowDt);
            entity.setEnable(TaskEnableEnum.TASK_ENABLE);
            entity.setPayload("{\"stressTest\":true,\"type\":\"realtime\",\"index\":" + i + "}");
            entity.setTopic(topic);
            entity.setProcess(ProcessEnum.PENDING);

            realtimeTaskMapper.upsetTaskInfo(entity);
            totalCreated++;
        }

        log.info("High-freq setup complete: {} realtime tasks, cron={}", totalCreated, cronExpr);
        return Map.of(
                "success", true,
                "totalCreated", totalCreated,
                "cronExpr", cronExpr,
                "bizName", bizName,
                "bizGroup", bizGroup
        );
    }

    /**
     * High-freq status: check how many realtime tasks are still pending.
     */
    @GetMapping("/high-freq/status")
    public Map<String, Object> highFreqStatus(
            @RequestParam(defaultValue = "stress-realtime") String bizName,
            @RequestParam(defaultValue = "hft") String bizGroup) {

        // Query realtime task count by scanning (mimics the time wheel scan)
        List<RealTimeTaskEntity> schedulable = realtimeTaskMapper.selectSchedulableTasks(
                System.currentTimeMillis() + 60000, 5000);

        long pendingCount = schedulable.stream()
                .filter(t -> bizName.equals(t.getBizName()) && bizGroup.equals(t.getBizGroup()))
                .count();

        return Map.of(
                "bizName", bizName,
                "bizGroup", bizGroup,
                "pendingTasks", pendingCount
        );
    }

    // ════════════════════════════════════════════════════════════════════
    // Metrics
    // ════════════════════════════════════════════════════════════════════

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Long> snapshot = metricsCollector.snapshot();
        Map<String, Object> result = new HashMap<>(snapshot);
        result.put("app", "executor-stress");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    // ════════════════════════════════════════════════════════════════════
    // Cleanup
    // ════════════════════════════════════════════════════════════════════

    @DeleteMapping("/cleanup")
    public Map<String, Object> cleanup(@RequestParam(defaultValue = "stress-test") String lowFreqBizName,
                                       @RequestParam(defaultValue = "5") int numLowFreqGroups,
                                       @RequestParam(defaultValue = "stress-realtime") String highFreqBizName,
                                       @RequestParam(defaultValue = "hft") String highFreqBizGroup) {
        // Use JDBC directly for bulk cleanup
        int lowFreqDeleted = 0;
        int highFreqDeleted = 0;

        try (Connection conn = dataSource.getConnection()) {
            // Clean low-freq tasks
            for (int g = 0; g < numLowFreqGroups; g++) {
                String bizGroup = "group-" + g;
                try (var ps = conn.prepareStatement(
                        "DELETE FROM user_scheduled_common_task WHERE biz_name=? AND biz_group=?")) {
                    ps.setString(1, lowFreqBizName);
                    ps.setString(2, bizGroup);
                    lowFreqDeleted += ps.executeUpdate();
                }
            }

            // Clean high-freq tasks
            try (var ps = conn.prepareStatement(
                    "DELETE FROM user_scheduled_realtime_task WHERE biz_name=? AND biz_group=?")) {
                ps.setString(1, highFreqBizName);
                ps.setString(2, highFreqBizGroup);
                highFreqDeleted += ps.executeUpdate();
            }

            // Clean event logs for stress test tasks
            try (var ps = conn.prepareStatement(
                    "DELETE FROM task_event_log WHERE task_name LIKE 'stress-%'")) {
                ps.executeUpdate();
            }
        } catch (Exception e) {
            log.error("Cleanup failed", e);
            return Map.of("success", false, "error", e.getMessage());
        }

        log.info("Cleanup: lowFreq={}, highFreq={}", lowFreqDeleted, highFreqDeleted);
        return Map.of(
                "success", true,
                "lowFreqDeleted", lowFreqDeleted,
                "highFreqDeleted", highFreqDeleted
        );
    }
}
