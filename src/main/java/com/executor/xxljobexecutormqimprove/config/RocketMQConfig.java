package com.executor.xxljobexecutormqimprove.config;

import com.executor.xxljobexecutormqimprove.entity.RocketMQEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMQConfig {
    private Logger logger = LoggerFactory.getLogger(RocketMQConfig.class);
    @Value("${xxl.job.process.topic}")
    private String topic;

    @Value("${xxl.job.process.nameserver}")
    private String nameServer;

    @Value("${xxl.job.process.group}")
    private String consumerGroup;

    @Value("${xxl.job.producer.produceGroup}")
    private String producerGroup;

    @Bean
    public RocketMQEntity rocketInit(){
        logger.info("—————MQ初始化————");
        RocketMQEntity rocketMQEntity = new RocketMQEntity();
        rocketMQEntity.setAddress(nameServer);
        rocketMQEntity.setTopic(topic);
        rocketMQEntity.setConsumerGroup(consumerGroup);
        rocketMQEntity.setProducerGroup(producerGroup);
        return rocketMQEntity;
    }
}
