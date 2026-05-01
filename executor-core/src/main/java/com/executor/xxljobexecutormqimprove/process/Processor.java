package com.executor.xxljobexecutormqimprove.process;

import com.executor.xxljobexecutormqimprove.Enum.TaskEnableEnum;
import com.executor.xxljobexecutormqimprove.core.store.TaskStore;
import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.entity.ProcessCommonTaskDTO;
import com.executor.xxljobexecutormqimprove.metrics.MetricsCollector;
import com.executor.xxljobexecutormqimprove.mq.MessageHandler;
import com.executor.xxljobexecutormqimprove.mq.MessageSubscriber;
import com.executor.xxljobexecutormqimprove.util.CronTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class Processor implements MessageHandler {

    private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis());

    private final Logger logger = LoggerFactory.getLogger(Processor.class);
    private final TaskStore taskStore;
    private final MetricsCollector metricsCollector;

    public Processor(TaskStore taskStore, MessageSubscriber subscriber, MetricsCollector metricsCollector) {
        this.taskStore = taskStore;
        this.metricsCollector = metricsCollector;
        subscriber.registerHandler(this);
        logger.info("---- Processor registered as message handler ----");
    }

    @Override
    public boolean handle(ProcessCommonTaskDTO taskDTO) {
        try {
            CommonTaskEntity entity = transFormat(taskDTO);
            logger.info("转为任务实体: {}", entity);
            taskStore.upsetTask(entity);
            metricsCollector.recordConsumed(true);
            return true;
        } catch (Exception e) {
            logger.error("处理消息失败", e);
            metricsCollector.recordConsumed(false);
            return false;
        }
    }

    private CommonTaskEntity transFormat(ProcessCommonTaskDTO dto) {
        CommonTaskEntity entity = new CommonTaskEntity();

        String generatedId = COUNTER.incrementAndGet() + "-" + ThreadLocalRandom.current().nextInt(10000, 99999);
        entity.setId(generatedId);
        entity.setTaskId(generatedId);
        entity.setTaskName(dto.getTaskName());
        entity.setBizName(dto.getBizName());
        entity.setBizGroup(dto.getBizGroup());
        entity.setScheduledConf(dto.getScheduledConf());
        if (dto.getTopic() == null || dto.getTopic().isEmpty()){
            entity.setTopic("executorPool");
        }else {
            entity.setTopic(dto.getTopic());
        }
        if (dto.getScheduledConf() == null){
            entity.setNextTriggerTime(dto.getExecuteTime());
        }else{
            try {
                entity.setNextTriggerTime(CronTimeUtil.getNextTriggerTime(dto.getScheduledConf(),System.currentTimeMillis()));
            }catch (Exception e){
                logger.error("时间戳生成失败，任务将被注册为禁用状态: taskName={}", dto.getTaskName(), e);
                // Cron 解析失败时降级：有 executeTime 就用，否则禁用任务等待清理
                if (dto.getExecuteTime() != null) {
                    entity.setNextTriggerTime(dto.getExecuteTime());
                } else {
                    entity.setEnable(TaskEnableEnum.TASK_UNABLE);
                }
            }
        }
        entity.setScheduledType(dto.getScheduledType());
        entity.setEnable(dto.getEnable() != null && dto.getEnable() ? TaskEnableEnum.TASK_ENABLE : TaskEnableEnum.TASK_UNABLE);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        entity.setCreateAt(now);
        entity.setUpdateAt(now);
        entity.setPayload(dto.getPayload());

        return entity;
    }
}
