package com.executor.xxljobexecutormqimprove.core.thread;

import com.executor.xxljobexecutormqimprove.core.base.RealtimeTaskBaseService;
import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.mapper.RealtimeTaskMapper;
import com.executor.xxljobexecutormqimprove.producer.ProducerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
@Component
public class ExecutorTrigger {
    private Logger logger = LoggerFactory.getLogger(ExecutorTrigger.class);
    @Autowired
    private ProducerMessage producerMessage;
    @Autowired
    private RealtimeTaskBaseService realtimeTaskBaseService;
    public void trigger(String jobId){
        ProduceCommonTaskMessage produceCommonTaskMessage = realtimeTaskBaseService.loadById(jobId);
        boolean isSuccess = producerMessage.send(produceCommonTaskMessage);
    }
}
