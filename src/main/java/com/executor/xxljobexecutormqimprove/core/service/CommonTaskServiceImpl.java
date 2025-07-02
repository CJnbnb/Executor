package com.executor.xxljobexecutormqimprove.core.service;

import com.executor.xxljobexecutormqimprove.Enum.ScheduledTypeEnum;
import com.executor.xxljobexecutormqimprove.Enum.TaskEnableEnum;
import com.executor.xxljobexecutormqimprove.core.base.CommonTaskBaseService;
import com.executor.xxljobexecutormqimprove.model.dto.ChangeTaskInfoDTO;
import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.util.CronTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CommonTaskServiceImpl implements CommonTaskService{

    @Autowired
    private CommonTaskBaseService commonTaskBaseService;

    private Logger logger = LoggerFactory.getLogger(CommonTaskServiceImpl.class);

    private void changeTaskInfo(ProduceCommonTaskMessage produceCommonTaskMessage) {
        Long lastTriggerTime = produceCommonTaskMessage.getNextTriggerTime();
        Long nextTriggerTime = null;
        String enable = TaskEnableEnum.TASK_ENABLE;
        if (produceCommonTaskMessage.getScheduledType().equals(ScheduledTypeEnum.SCHEDULED_CRON)){
            try {
                nextTriggerTime = CronTimeUtil.getNextTriggerTime(produceCommonTaskMessage.getScheduledConf(),System.currentTimeMillis());
            }catch (Exception e){
                logger.error("生成时间失败{}",e.getMessage());
            }
        }else {
            enable = TaskEnableEnum.TASK_UNABLE;
        }

        ChangeTaskInfoDTO changeTaskInfoDTO = new ChangeTaskInfoDTO();
        changeTaskInfoDTO.setId(produceCommonTaskMessage.getId());
        changeTaskInfoDTO.setNextTriggerTime(nextTriggerTime);
        changeTaskInfoDTO.setLastTriggerTime(lastTriggerTime);
        changeTaskInfoDTO.setEnable(enable);
        return commonTaskBaseService.changeTaskInfo(changeTaskInfoDTO);

    }

    public void batchChangeTaskInfo(List<ProduceCommonTaskMessage> produceCommonTaskMessageList) {
        if (produceCommonTaskMessageList == null || produceCommonTaskMessageList.isEmpty()) return;
        // 转换为DTO列表
        List<ChangeTaskInfoDTO> dtoList = new ArrayList<>();
        for (ProduceCommonTaskMessage task : produceCommonTaskMessageList) {
            ChangeTaskInfoDTO dto = new ChangeTaskInfoDTO();
            Long lastTriggerTime = task.getNextTriggerTime();
            Long nextTriggerTime = null;
            String enable = TaskEnableEnum.TASK_ENABLE;
            if (task.getScheduledType().equals(ScheduledTypeEnum.SCHEDULED_CRON)){
                try {
                    nextTriggerTime = CronTimeUtil.getNextTriggerTime(task.getScheduledConf(),System.currentTimeMillis());
                }catch (Exception e){
                    logger.error("生成时间失败{}",e.getMessage());
                }
            }else {
                enable = TaskEnableEnum.TASK_UNABLE;
            }
            dto.setId(task.getId());
            dto.setLastTriggerTime(task.getNextTriggerTime());
            dto.setNextTriggerTime(nextTriggerTime);
            dto.setEnable(enable);
            dtoList.add(dto);
        }
        commonTaskBaseService.batchChangeTaskInfo(dtoList);
    }

    @Override
    @Transactional
    public void changeTask(ProduceCommonTaskMessage produceCommonTaskMessage) {
        changeTaskInfo(produceCommonTaskMessage);
        commonTaskBaseService.unlockTaskById(produceCommonTaskMessage.getId());
    }
}
