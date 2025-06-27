package com.executor.xxljobexecutormqimprove.core.thread;

import com.executor.xxljobexecutormqimprove.core.base.CommonTaskBaseService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScheduledUnlockService {
    private Logger logger = LoggerFactory.getLogger(ScheduledUnlockService.class);

    private volatile boolean toStop = false;

    @Autowired
    private CommonTaskBaseService commonTaskBaseService;

    @PostConstruct
    private void start(){
        while (!toStop) {
            try {
                // 1. 查询超时未解锁的任务
                List<String> timeoutIds = commonTaskBaseService.findTimeoutProcessingTaskIds(System.currentTimeMillis());
                if (!timeoutIds.isEmpty()) {
                    // 2. 批量解锁
                    commonTaskBaseService.unlockExceptionTasks(timeoutIds);
                }
                logger.info("补偿时间为{}",System.currentTimeMillis());
                Thread.sleep(60_000 * 10);
            } catch (Exception e) {
                logger.error("补偿任务失败{}",e.getMessage());
            }
        }
    }

    @PreDestroy
    private void stop(){
        toStop = true;
    }



}