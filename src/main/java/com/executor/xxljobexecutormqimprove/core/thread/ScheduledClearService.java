package com.executor.xxljobexecutormqimprove.core.thread;

import com.executor.xxljobexecutormqimprove.core.base.CommonTaskBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduledClearService {
    private Logger logger = LoggerFactory.getLogger(ScheduledClearService.class);
    @Autowired
    private CommonTaskBaseService commonTaskBaseService;

    // 每天凌晨2点清理一次
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Shanghai")
    public void clearDisabledTasks() {
        int deleted = commonTaskBaseService.deleteData();
        logger.info("定时清理无效任务，删除数量：" + deleted);
    }
}