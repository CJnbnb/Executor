package com.executor.xxljobexecutormqimprove.core.service;

import com.executor.xxljobexecutormqimprove.Enum.ScheduledTypeEnum;
import com.executor.xxljobexecutormqimprove.Enum.TaskEnableEnum;
import com.executor.xxljobexecutormqimprove.core.store.TaskStore;
import com.executor.xxljobexecutormqimprove.entity.ChangeTaskInfoDTO;
import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.util.CronTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CommonTaskServiceImpl implements CommonTaskService{

    @Autowired
    private TaskStore taskStore;

    private Logger logger = LoggerFactory.getLogger(CommonTaskServiceImpl.class);

    @Override
    public boolean changeTaskInfo(ProduceCommonTaskMessage task) {
        ChangeTaskInfoDTO dto = buildChangeTaskInfoDTO(task);
        return taskStore.updateTaskTriggerInfo(dto);
    }

    public void batchChangeTaskInfo(List<ProduceCommonTaskMessage> produceCommonTaskMessageList) {
        if (produceCommonTaskMessageList == null || produceCommonTaskMessageList.isEmpty()) return;
        List<ChangeTaskInfoDTO> dtoList = new ArrayList<>();
        for (ProduceCommonTaskMessage task : produceCommonTaskMessageList) {
            dtoList.add(buildChangeTaskInfoDTO(task));
        }
        taskStore.batchUpdateTaskTriggerInfo(dtoList);
    }

    private ChangeTaskInfoDTO buildChangeTaskInfoDTO(ProduceCommonTaskMessage task) {
        ChangeTaskInfoDTO dto = new ChangeTaskInfoDTO();
        dto.setId(task.getId());
        dto.setLastTriggerTime(task.getNextTriggerTime());

        if (ScheduledTypeEnum.SCHEDULED_CRON.equals(task.getScheduledType())) {
            try {
                long next = CronTimeUtil.getNextTriggerTime(task.getScheduledConf(), System.currentTimeMillis());
                if (next < 0) {
                    // Cron 表达式没有未来匹配时间（已过期），禁用任务
                    logger.warn("Cron表达式已过期，禁用任务: taskId={}, cron={}", task.getId(), task.getScheduledConf());
                    dto.setEnable(TaskEnableEnum.TASK_UNABLE);
                } else {
                    dto.setNextTriggerTime(next);
                    dto.setEnable(TaskEnableEnum.TASK_ENABLE);
                }
            } catch (Exception e) {
                logger.error("Cron解析失败，1分钟后重试: taskId={}", task.getId(), e);
                dto.setNextTriggerTime(System.currentTimeMillis() + 60_000);
                dto.setEnable(TaskEnableEnum.TASK_ENABLE);
            }
        } else {
            dto.setEnable(TaskEnableEnum.TASK_UNABLE);
        }
        return dto;
    }
}
