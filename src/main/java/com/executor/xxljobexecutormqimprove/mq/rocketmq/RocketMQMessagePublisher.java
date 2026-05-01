package com.executor.xxljobexecutormqimprove.mq.rocketmq;

import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.mq.MessagePublisher;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

public class RocketMQMessagePublisher implements MessagePublisher {

    private final Logger logger = LoggerFactory.getLogger(RocketMQMessagePublisher.class);

    private final String nameServer;
    private final String producerGroup;

    private DefaultMQProducer producer;

    public RocketMQMessagePublisher(String nameServer, String producerGroup) {
        this.nameServer = nameServer;
        this.producerGroup = producerGroup;
    }

    @PostConstruct
    public void init() throws MQClientException {
        producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.start();
        logger.info("---- RocketMQ Publisher started ----");
    }

    @Override
    public boolean send(ProduceCommonTaskMessage task) {
        String topic = task.getTopic();
        String tag = task.getTaskName();
        String messageBody = task.getPayload();
        Message message = new Message(topic, tag, messageBody.getBytes());
        try {
            SendResult result = producer.send(message);
            logger.info("MQ send result: {}", result);
        } catch (Exception e) {
            logger.error("MQ send failed: {}", e.getMessage());
            return false;
        }
        return true;
    }

    @PreDestroy
    public void shutdown() {
        if (producer != null) {
            producer.shutdown();
            logger.info("---- RocketMQ Publisher shutdown ----");
        }
    }
}
