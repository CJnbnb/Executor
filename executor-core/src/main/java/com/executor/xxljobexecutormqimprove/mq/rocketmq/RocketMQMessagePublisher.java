package com.executor.xxljobexecutormqimprove.mq.rocketmq;

import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.mq.MessagePublisher;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.RPCHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

public class RocketMQMessagePublisher implements MessagePublisher {

    private final Logger logger = LoggerFactory.getLogger(RocketMQMessagePublisher.class);

    private final String nameServer;
    private final String producerGroup;
    private final String accessKey;
    private final String secretKey;
    private final int sendMessageTimeout;
    private final int retryTimesWhenSendFailed;

    private DefaultMQProducer producer;

    public RocketMQMessagePublisher(String nameServer, String producerGroup,
                                    String accessKey, String secretKey,
                                    int sendMessageTimeout, int retryTimesWhenSendFailed) {
        this.nameServer = nameServer;
        this.producerGroup = producerGroup;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.sendMessageTimeout = sendMessageTimeout;
        this.retryTimesWhenSendFailed = retryTimesWhenSendFailed;
    }

    @PostConstruct
    public void init() throws MQClientException {
        if (accessKey != null && !accessKey.isEmpty()) {
            RPCHook rpcHook = new AclClientRPCHook(new SessionCredentials(accessKey, secretKey));
            producer = new DefaultMQProducer(producerGroup, rpcHook);
        } else {
            producer = new DefaultMQProducer(producerGroup);
        }
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(sendMessageTimeout);
        producer.setRetryTimesWhenSendFailed(retryTimesWhenSendFailed);
        producer.start();
        logger.info("---- RocketMQ Publisher started ----");
    }

    @Override
    public boolean send(ProduceCommonTaskMessage task) {
        String topic = task.getTopic();
        String tag = task.getTaskName();
        String messageBody = task.getPayload();
        if (messageBody == null) {
            messageBody = "{}";
        }
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
