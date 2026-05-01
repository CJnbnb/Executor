package com.executor.xxljobexecutormqimprove.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MockProducerHandler {
    private static final int LIMIT_COUNT = 200;

    // 线程池模拟
    private static final ThreadPoolExecutor mqSendPoolExecutor =
            new ThreadPoolExecutor(
                    Runtime.getRuntime().availableProcessors() * 2,
                    Runtime.getRuntime().availableProcessors() * 2,
                    0L, TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>());

    public void producerMessage() {
        // 模拟查库、加锁
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < LIMIT_COUNT; i++) {
            ids.add("id-" + i);
        }

        // 用线程池模拟并发发送
        List<Future<Boolean>> futures = new ArrayList<>();
        for (String id : ids) {
            futures.add(mqSendPoolExecutor.submit(() -> {
                // Mock MQ发送（可加Thread.sleep(1)模拟耗时）
                Thread.sleep(1);
                // producerMessage.send(task);
                // Mock 业务回写
                // commonTaskService.changeTaskInfo(task);
                return true;
            }));
        }
        // 等待所有异步任务完成
        for (Future<Boolean> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                // 忽略
            }
        }

        // Mock 解锁
        // commonTaskBaseService.unlockTasks(ids);
    }
}
