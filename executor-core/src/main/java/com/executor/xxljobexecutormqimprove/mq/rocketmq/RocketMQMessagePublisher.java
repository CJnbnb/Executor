package com.executor.xxljobexecutormqimprove.mq.rocketmq;

import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
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

        int maxAttempts = 3;
        long baseDelay = 2000;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                SendResult result = producer.send(message);
                logger.info("MQ send result: {}", result);
                return true;
            } catch (Exception e) {
                if (attempt < maxAttempts - 1) {
                    long delay = baseDelay * (1L << attempt);
                    logger.warn("MQ send failed (attempt {}), retrying in {}ms: {}",
                            attempt + 1, delay, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    logger.error("MQ send finally failed after {} attempts: {}",
                            maxAttempts, e.toString());
                }
            }
        }
        return false;
    }

    @PreDestroy
    public void shutdown() {
        if (producer != null) {
            producer.shutdown();
            logger.info("---- RocketMQ Publisher shutdown ----");
        }
    }
}
