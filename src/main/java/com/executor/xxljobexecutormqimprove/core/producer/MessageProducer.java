package com.executor.xxljobexecutormqimprove.core.producer;

import com.executor.xxljobexecutormqimprove.core.service.RetryTaskService;
import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.model.entity.RocketMQEntity;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 此代码是业务层的生产者
 * 用来计算时间戳然后进行业务发送
 */
@Component
public class MessageProducer {
    @Autowired
    private RocketMQEntity rocketMQEntity;

    @Autowired
    private RetryTaskService retryTaskService;
    private final Logger logger = LoggerFactory.getLogger(MessageProducer.class);

    private DefaultMQProducer producer;
    @PostConstruct
    public void init() throws MQClientException{
        producer = new DefaultMQProducer(rocketMQEntity.getProducerGroup());
        producer.setNamesrvAddr(rocketMQEntity.getAddress());
        producer.start();
        logger.info("---- RocketMQ Producer started ----");
    }

    @Retryable(
            value = {RuntimeException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 2000,multiplier = 2)
    )
    public boolean send(ProduceCommonTaskMessage produceCommonTaskMessage){
        String topic = produceCommonTaskMessage.getTopic();
        String tag = produceCommonTaskMessage.getTaskName();
        String messageBody = produceCommonTaskMessage.getPayload();
        Message message =  new Message(topic,tag,messageBody.getBytes());
        SendResult sendResult = new SendResult();
        try {
            logger.info(String.valueOf(message));
            sendResult = producer.send(message);
            logger.info("result{}",sendResult);
        }catch (Exception e){
            logger.error("业务MQ发送失败,失败消息为{}，信息为{}",e.getMessage(),message);
            throw new RuntimeException();
        }
        return true;
    }

    @Recover
    public void recover(Exception e, ProduceCommonTaskMessage task) {
        logger.error("所有重试失败，任务发送最终失败: {}", task.getTaskName(), e);
        retryTaskService.recordTaskByTask(task);
    }



    @PreDestroy
    public void shutdown() {
        if (producer != null) {
            producer.shutdown();
            logger.info("---- RocketMQ Producer shut down ----");
        }
    }

}
