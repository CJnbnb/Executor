package com.executor.xxljobexecutormqimprove.core.scheduled;

import com.executor.xxljobexecutormqimprove.core.base.CommonTaskBaseService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ScheduledUnlockService {
    private Logger logger = LoggerFactory.getLogger(ScheduledUnlockService.class);

    private volatile boolean toStop = false;

    @Autowired
    private CommonTaskBaseService commonTaskBaseService;

    @PostConstruct
    public void start() {
        Thread t = new Thread(() -> {
            while (!toStop) {
                try {
                    // 1. 查询超时未解锁的任务
                    List<String> timeoutIds = commonTaskBaseService.findTimeoutProcessingTaskIds(System.currentTimeMillis());
                    if (!timeoutIds.isEmpty()) {
                        // 2. 批量解锁
                        commonTaskBaseService.unlockExceptionTasks(timeoutIds);
                        logger.info("补偿时间为{}", System.currentTimeMillis());
                    }
                } catch (Exception e) {
                    logger.error("补偿任务失败{}", e);
                }
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        });
        t.setDaemon(true);
        t.setName("unlockTimoutTask");
        t.start();
    }

    @PreDestroy
    private void stop() {
        toStop = true;
    }



}
