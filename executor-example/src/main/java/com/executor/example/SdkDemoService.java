package com.executor.example;

import com.executor.sdk.ExecutorSdkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * SDK 演示服务 — 应用启动后自动注册示例任务。
 * <p>仅在 ExecutorSdkClient bean 存在（即已配置 RocketMQ）时加载。</p>
 * <p>
 * 业务方只需：
 * <ol>
 *   <li>引入 executor-sdk 依赖</li>
 *   <li>注入 ExecutorSdkClient</li>
 *   <li>通过 Builder 模式注册任务：
 *     <pre>
 *     sdkClient.newTask("任务名")
 *         .biz("业务线", "分组")
 *         .cron("0/30 * * * * ?")
 *         .payload("{\"key\":\"value\"}")
 *         .schedule();
 *     </pre>
 *   </li>
 * </ol>
 */
@Component
@ConditionalOnBean(ExecutorSdkClient.class)
public class SdkDemoService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SdkDemoService.class);

    @Autowired
    private ExecutorSdkClient sdkClient;

    @Override
    public void run(String... args) {
        log.info("=== SDK 演示：注册示例任务 ===");

        // 1. Cron 定时任务 — 每30秒触发
        sdkClient.newTask("demo-cron-task")
                .biz("demo", "example")
                .cron("0/30 * * * * ?")
                .payload("{\"type\":\"cron\",\"description\":\"每30秒执行的示例任务\"}")
                .schedule();
        log.info("已注册 Cron 任务: demo-cron-task");

        // 2. 一次性任务 — 5分钟后执行
        long executeAt = System.currentTimeMillis() + 5 * 60 * 1000;
        sdkClient.newTask("demo-once-task")
                .biz("demo", "example")
                .once(executeAt)
                .payload("{\"type\":\"once\",\"description\":\"5分钟后执行的一次性任务\"}")
                .schedule();
        log.info("已注册一次性任务: demo-once-task (执行时间: {})", executeAt);

        // 3. 带自定义 Topic 的 Cron 任务
        sdkClient.newTask("demo-custom-topic-task")
                .biz("order", "bizA")
                .cron("0 0 9 * * ?")
                .payload("{\"type\":\"cron\",\"description\":\"每天9点执行\",\"action\":\"dailyReport\"}")
                .topic("executorPool")
                .enable(true)
                .schedule();
        log.info("已注册 Cron 任务: demo-custom-topic-task");

        log.info("=== SDK 演示任务注册完成，共 3 个任务 ===");
    }
}
