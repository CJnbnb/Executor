package com.executor.xxljobexecutormqimprove.producer;

import com.executor.xxljobexecutormqimprove.entity.ProcessCommonTaskDTO;
import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.entity.RocketMQEntity;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 此代码是业务层的生产者
 * 用来计算时间戳然后进行业务发送
 */
@Component
public class ProducerMessage {
    @Autowired
    private RocketMQEntity rocketMQEntity;
    private final Logger logger = LoggerFactory.getLogger(ProducerMessage.class);

    private DefaultMQProducer producer;
    @PostConstruct
    public void init() throws MQClientException{
        producer = new DefaultMQProducer(rocketMQEntity.getProducerGroup());
        producer.setNamesrvAddr(rocketMQEntity.getAddress());
        producer.start();
        logger.info("---- RocketMQ Producer started ----");
    }

    public boolean send(ProduceCommonTaskMessage produceCommonTaskMessage){
        String topic = produceCommonTaskMessage.getTopic();
        String tag = produceCommonTaskMessage.getTaskName();
        String messageBody = produceCommonTaskMessage.getPayload();
        Message message =  new Message(topic,tag,messageBody.getBytes());
        try {
            SendResult result = producer.send(message);
        }catch (Exception e){
            logger.error("业务MQ发送失败");
            return false;
        }
        return true;
    }


    @PreDestroy
    public void shutdown() {
        if (producer != null) {
            producer.shutdown();
            logger.info("---- RocketMQ Producer shut down ----");
        }
    }

}
