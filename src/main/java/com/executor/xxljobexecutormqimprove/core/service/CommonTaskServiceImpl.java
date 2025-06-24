package com.executor.xxljobexecutormqimprove.core.service;

import com.executor.xxljobexecutormqimprove.Enum.ScheduledTypeEnum;
import com.executor.xxljobexecutormqimprove.Enum.TaskEnableEnum;
import com.executor.xxljobexecutormqimprove.core.base.CommonTaskBaseService;
import com.executor.xxljobexecutormqimprove.entity.ChangeTaskInfoDTO;
import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.util.CronTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CommonTaskServiceImpl implements CommonTaskService{

    @Autowired
    private CommonTaskBaseService commonTaskBaseService;

    private Logger logger = LoggerFactory.getLogger(CommonTaskServiceImpl.class);

    @Override
    public void changeTaskInfo(ProduceCommonTaskMessage produceCommonTaskMessage) {
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
        commonTaskBaseService.changeTaskInfo(changeTaskInfoDTO);

    }
}
