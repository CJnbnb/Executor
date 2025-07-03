package com.executor.xxljobexecutormqimprove.core.service;

import com.executor.xxljobexecutormqimprove.core.base.RetryTaskBaseService;
import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.model.entity.RetryTaskEntity;
import com.executor.xxljobexecutormqimprove.model.dto.RetryTaskUpdateDTO;
import com.executor.xxljobexecutormqimprove.core.producer.MessageProducer;
import com.executor.xxljobexecutormqimprove.util.CalculateRetryTaskUtil;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RetryTaskService {
    private Logger logger = LoggerFactory.getLogger(RetryTaskService.class);
    @Autowired
    private RetryTaskBaseService retryTaskBaseService;

    @Autowired
    private Gson gson;

    /*
    构造函数避免Spring的ReTask代理
    改一下发送
     */
    private static final MessageProducer messageProducer = new MessageProducer();

    public void recordTaskByTask(ProduceCommonTaskMessage produceCommonTaskMessage){
        RetryTaskEntity retryTaskEntity = new RetryTaskEntity();
        retryTaskEntity.setId(produceCommonTaskMessage.getId());
        retryTaskEntity.setRetryCount(2);
        retryTaskEntity.setCreateAt(LocalDateTime.now());
        Long nextTime = CalculateRetryTaskUtil.calculateNextTriggerTimestamp(0,produceCommonTaskMessage.getNextTriggerTime());
        retryTaskEntity.setNextTriggerTime(nextTime);
        retryTaskEntity.setArgs(gson.toJson(produceCommonTaskMessage));
        retryTaskBaseService.record(retryTaskEntity);
    }

    public void retry(){
        Long now = System.currentTimeMillis();
        List<RetryTaskEntity> retryTaskEntities = retryTaskBaseService.getRetryableTask(now);
        if (retryTaskEntities.isEmpty()){
            return;
        }
        for (RetryTaskEntity retryTaskEntity : retryTaskEntities){
            try {
                boolean isSuccess = messageProducer.send(gson.fromJson(retryTaskEntity.getArgs(), ProduceCommonTaskMessage.class));
                if (isSuccess){
                    retryTaskBaseService.removeRetryTask(retryTaskEntity.getId());
                }
            }catch (Exception e){
                RetryTaskUpdateDTO retryTaskUpdateDTO = new RetryTaskUpdateDTO();
                int retryCount = retryTaskEntity.getRetryCount() + 1;
                retryTaskUpdateDTO.setRetryCount(retryCount);
                retryTaskUpdateDTO.setNextTriggerTime(CalculateRetryTaskUtil.calculateNextTriggerTimestamp(retryCount,retryTaskEntity.getNextTriggerTime()));
                retryTaskUpdateDTO.setId(retryTaskUpdateDTO.getId());
                retryTaskBaseService.updateRecord(retryTaskUpdateDTO);
            }
        }
    }
}
