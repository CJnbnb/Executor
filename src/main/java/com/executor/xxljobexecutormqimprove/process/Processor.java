package com.executor.xxljobexecutormqimprove.process;

import com.executor.xxljobexecutormqimprove.Enum.TaskEnableEnum;
import com.executor.xxljobexecutormqimprove.core.store.TaskStore;
import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.entity.ProcessCommonTaskDTO;
import com.executor.xxljobexecutormqimprove.mq.MessageHandler;
import com.executor.xxljobexecutormqimprove.mq.MessageSubscriber;
import com.executor.xxljobexecutormqimprove.util.CronTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class Processor implements MessageHandler {

    private final Logger logger = LoggerFactory.getLogger(Processor.class);
    private final TaskStore taskStore;

    public Processor(TaskStore taskStore, MessageSubscriber subscriber) {
        this.taskStore = taskStore;
        subscriber.registerHandler(this);
        logger.info("---- Processor registered as message handler ----");
    }

    @Override
    public boolean handle(ProcessCommonTaskDTO taskDTO) {
        CommonTaskEntity entity = transFormat(taskDTO);
        logger.info("转为任务实体: {}", entity);
        taskStore.upsetTask(entity);
        return true;
    }

    private CommonTaskEntity transFormat(ProcessCommonTaskDTO dto) {
        CommonTaskEntity entity = new CommonTaskEntity();

        entity.setId(System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999));
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
                logger.error("时间戳生成失败，内部错误");
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
