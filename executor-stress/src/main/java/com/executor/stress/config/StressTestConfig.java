package com.executor.stress.config;

import com.executor.stress.mq.MockMQMessagePublisher;
import com.executor.xxljobexecutormqimprove.mq.MessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.PostConstruct;

@Configuration
public class StressTestConfig {

    private static final Logger log = LoggerFactory.getLogger(StressTestConfig.class);

    @Value("${stress.mq.mock:false}")
    private boolean mockMqEnabled;

    @Value("${stress.mq.mock-delay-ms:0}")
    private long mockMqDelayMs;

    @PostConstruct
    public void logMode() {
        if (mockMqEnabled) {
            log.info("==== STRESS-TEST MODE: Mock MQ (delay={}ms) ====", mockMqDelayMs);
        } else {
            log.info("==== STRESS-TEST MODE: Real RocketMQ ====");
        }
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "stress.mq.mock", havingValue = "true")
    public MessagePublisher mockMessagePublisher() {
        log.info("Creating MockMQMessagePublisher with delay={}ms", mockMqDelayMs);
        return new MockMQMessagePublisher(mockMqDelayMs);
    }
}
