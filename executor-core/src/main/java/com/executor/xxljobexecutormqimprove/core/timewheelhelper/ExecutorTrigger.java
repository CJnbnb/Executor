package com.executor.xxljobexecutormqimprove.core.timewheelhelper;

import com.executor.xxljobexecutormqimprove.core.base.RealtimeTaskBaseService;
import com.executor.xxljobexecutormqimprove.metrics.MetricsCollector;
import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.mq.MessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExecutorTrigger {
    private Logger logger = LoggerFactory.getLogger(ExecutorTrigger.class);
    @Autowired
    private MessagePublisher messagePublisher;
    @Autowired
    private RealtimeTaskBaseService realtimeTaskBaseService;
    @Autowired
    private MetricsCollector metricsCollector;

    public void trigger(String jobId){
        ProduceCommonTaskMessage produceCommonTaskMessage = realtimeTaskBaseService.loadById(jobId);
        boolean isSuccess = messagePublisher.send(produceCommonTaskMessage);
        metricsCollector.recordProduced(isSuccess);
        if (!Boolean.TRUE.equals(isSuccess)){
            logger.error("realtime消息发送失败");
        }
    }
}
