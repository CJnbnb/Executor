package com.executor.xxljobexecutormqimprove.mq.rocketmq;

import com.alibaba.fastjson.JSONObject;
import com.executor.xxljobexecutormqimprove.entity.ProcessCommonTaskDTO;
import com.executor.xxljobexecutormqimprove.mq.MessageHandler;
import com.executor.xxljobexecutormqimprove.mq.MessageSubscriber;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.RPCHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;

public class RocketMQMessageSubscriber implements MessageSubscriber, MessageListenerConcurrently {

    private final Logger logger = LoggerFactory.getLogger(RocketMQMessageSubscriber.class);

    private final String nameServer;
    private final String topic;
    private final String consumerGroup;
    private final String accessKey;
    private final String secretKey;

    private DefaultMQPushConsumer consumer;
    private MessageHandler handler;

    public RocketMQMessageSubscriber(String nameServer, String topic, String consumerGroup,
                                     String accessKey, String secretKey) {
        this.nameServer = nameServer;
        this.topic = topic;
        this.consumerGroup = consumerGroup;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Override
    public void registerHandler(MessageHandler handler) {
        this.handler = handler;
    }

    @PostConstruct
    public void init() throws MQClientException {
        if (accessKey != null && !accessKey.isEmpty()) {
            RPCHook rpcHook = new AclClientRPCHook(new SessionCredentials(accessKey, secretKey));
            consumer = new DefaultMQPushConsumer(consumerGroup, rpcHook, new AllocateMessageQueueAveragely());
        } else {
            consumer = new DefaultMQPushConsumer(consumerGroup);
        }
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener(this);
        consumer.start();
        logger.info("---- RocketMQ Subscriber started ----");
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
            logger.info("---- RocketMQ Subscriber shutdown ----");
        }
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        if (handler == null) {
            logger.warn("No handler registered, skip message");
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
        for (MessageExt msg : msgs) {
            try {
                String body = new String(msg.getBody());
                ProcessCommonTaskDTO task = JSONObject.parseObject(body, ProcessCommonTaskDTO.class);
                boolean ok = handler.handle(task);
                if (!ok) {
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                logger.info("Message handled: {}", task);
            } catch (Exception e) {
                logger.error("Message handling failed", e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
}
