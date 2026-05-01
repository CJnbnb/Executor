package com.executor.xxljobexecutormqimprove.core.thread;

import com.executor.xxljobexecutormqimprove.core.store.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduledClearService {
    private Logger logger = LoggerFactory.getLogger(ScheduledClearService.class);
    @Autowired
    private TaskStore taskStore;

    private static final int STUCK_MINUTES = 30;
    private static final int STALE_DAYS = 7;

    // 每天凌晨2点清理一次
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Shanghai")
    public void clearDisabledTasks() {
        int deleted = taskStore.deleteDisabledTasks();
        logger.info("定时清理已禁用任务，删除数量：" + deleted);

        int released = taskStore.releaseStaleProcessing(STUCK_MINUTES);
        if (released > 0) {
            logger.warn("释放卡住超过{}分钟的任务，数量：{}", STUCK_MINUTES, released);
        }

        int staleDeleted = taskStore.deleteStaleEnabled(STALE_DAYS);
        if (staleDeleted > 0) {
            logger.warn("清理{}天前到期的仍为启用状态的任务，删除数量：{}", STALE_DAYS, staleDeleted);
        }
    }
}