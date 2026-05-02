package com.executor.xxljobexecutormqimprove;

import com.executor.xxljobexecutormqimprove.core.producer.MessageProducer;
import com.executor.xxljobexecutormqimprove.mq.MessagePublisher;
import com.executor.xxljobexecutormqimprove.mq.MessageSubscriber;
import com.executor.xxljobexecutormqimprove.service.TaskRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class XxlJobExecutorMqImproveApplicationTests {

    @MockBean
    private MessagePublisher messagePublisher;

    @MockBean
    private MessageSubscriber messageSubscriber;

    @MockBean
    private MessageProducer messageProducer;

    @MockBean
    private TaskRegistrationService taskRegistrationService;

    @Test
    void contextLoads() {
    }

}
