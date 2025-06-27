package com.executor.xxljobexecutormqimprove.core.thread;

import com.executor.xxljobexecutormqimprove.core.service.RetryTaskService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@EnableScheduling
@Component
public class RetryTaskScheduler {
    private Logger logger = LoggerFactory.getLogger(RetryTaskService.class);
    @Autowired
    private RetryTaskService retryTaskService;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    private void start() {
        logger.info("------init RetryTaskScheduler-------");
        executor.scheduleWithFixedDelay(
                () -> retryTaskService.retry(),
                0, 1, TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow(); // 应用关闭时终止任务
    }
}