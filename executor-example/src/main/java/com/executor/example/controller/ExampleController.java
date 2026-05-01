package com.executor.example.controller;

import com.executor.sdk.ExecutorSdkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SDK 手动注册任务测试接口。
 */
@RestController
@RequestMapping("/example")
public class ExampleController {

    private static final Logger log = LoggerFactory.getLogger(ExampleController.class);

    @Autowired(required = false)
    private ExecutorSdkClient sdkClient;

    /** 健康检查 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "app", "executor-example",
                "sdkReady", sdkClient != null
        );
    }

    /** 手动注册任务（支持 Cron 或一次性任务） */
    @PostMapping("/task")
    public Map<String, Object> registerTask(@RequestBody Map<String, Object> body) {
        String taskName = (String) body.get("taskName");
        String bizName = (String) body.get("bizName");
        String bizGroup = (String) body.get("bizGroup");
        String cron = (String) body.get("cron");
        Long executeTime = body.get("executeTime") != null
                ? ((Number) body.get("executeTime")).longValue() : null;
        String payload = (String) body.getOrDefault("payload", "{}");

        if (taskName == null || bizName == null || bizGroup == null) {
            return Map.of("success", false, "error", "taskName/bizName/bizGroup 为必填项");
        }

        try {
            if (cron != null) {
                sdkClient.newTask(taskName)
                        .biz(bizName, bizGroup)
                        .cron(cron)
                        .payload(payload)
                        .schedule();
            } else if (executeTime != null) {
                sdkClient.newTask(taskName)
                        .biz(bizName, bizGroup)
                        .once(executeTime)
                        .payload(payload)
                        .schedule();
            } else {
                return Map.of("success", false, "error", "必须指定 cron 或 executeTime");
            }

            log.info("任务注册成功: taskName={}", taskName);
            return Map.of("success", true, "taskName", taskName);
        } catch (Exception e) {
            log.error("任务注册失败: {}", e.getMessage(), e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
