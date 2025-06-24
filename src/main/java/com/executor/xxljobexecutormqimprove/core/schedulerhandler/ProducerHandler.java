package com.executor.xxljobexecutormqimprove.core.schedulerhandler;

import com.executor.xxljobexecutormqimprove.producer.ProducerMessage;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProducerHandler {
    private static Logger logger = LoggerFactory.getLogger(ProducerHandler.class);

    @Autowired
    private ProducerMessage producerMessage;
    @XxlJob("Executor")
    public void producerMessage(){
        logger.info("发送消息");


    }
}
