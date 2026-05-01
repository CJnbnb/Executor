package com.executor.xxljobexecutormqimprove.config;

import com.executor.xxljobexecutormqimprove.mq.MessagePublisher;
import com.executor.xxljobexecutormqimprove.mq.MessageSubscriber;
import com.executor.xxljobexecutormqimprove.mq.rocketmq.RocketMQMessagePublisher;
import com.executor.xxljobexecutormqimprove.mq.rocketmq.RocketMQMessageSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MessageQueueProperties.class)
@ConditionalOnProperty(prefix = "xxl.job.process", name = "nameserver")
public class MessageQueueAutoConfiguration {

    @Value("${xxl.job.producer.produceGroup:executorProduceGroup}")
    private String producerGroup;

    @Bean
    public MessagePublisher messagePublisher(MessageQueueProperties properties) {
        return new RocketMQMessagePublisher(properties.getNameserver(), producerGroup,
                properties.getAccessKey(), properties.getSecretKey(),
                properties.getSendMessageTimeout(), properties.getRetryTimesWhenSendFailed());
    }

    @Bean
    public MessageSubscriber messageSubscriber(MessageQueueProperties properties) {
        return new RocketMQMessageSubscriber(properties.getNameserver(), properties.getTopic(),
                properties.getGroup(), properties.getAccessKey(), properties.getSecretKey());
    }
}
