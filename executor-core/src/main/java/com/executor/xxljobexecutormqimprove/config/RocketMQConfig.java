package com.executor.xxljobexecutormqimprove.config;

import com.executor.xxljobexecutormqimprove.model.entity.RocketMQEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMQConfig {
    private Logger logger = LoggerFactory.getLogger(RocketMQConfig.class);
    @Value("${xxl.job.process.topic:executorConsumeTask}")
    private String topic;

    @Value("${xxl.job.process.nameserver:localhost:9876}")
    private String nameServer;

    @Value("${xxl.job.process.group:executorConsumeMessageGroup}")
    private String consumerGroup;

    @Value("${xxl.job.producer.produceGroup:executorProduceGroup}")
    private String producerGroup;

    @Value("${xxl.job.process.access-key:}")
    private String accessKey;

    @Value("${xxl.job.process.secret-key:}")
    private String secretKey;

    @Bean
    public RocketMQEntity rocketInit(){
        logger.info("—————MQ初始化————");
        RocketMQEntity rocketMQEntity = new RocketMQEntity();
        rocketMQEntity.setAddress(nameServer);
        rocketMQEntity.setTopic(topic);
        rocketMQEntity.setConsumerGroup(consumerGroup);
        rocketMQEntity.setProducerGroup(producerGroup);
        rocketMQEntity.setAccessKey(accessKey);
        rocketMQEntity.setSecretKey(secretKey);
        return rocketMQEntity;
    }
}
