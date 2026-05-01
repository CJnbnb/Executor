package com.executor.xxljobexecutormqimprove.process;

import com.executor.xxljobexecutormqimprove.core.store.TaskStore;
import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.entity.ProcessCommonTaskDTO;
import com.executor.xxljobexecutormqimprove.metrics.MetricsCollector;
import com.executor.xxljobexecutormqimprove.mq.MessageHandler;
import com.executor.xxljobexecutormqimprove.mq.MessageSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessorTest {

    @Mock
    private TaskStore taskStore;

    @Mock
    private MessageSubscriber subscriber;

    @Mock
    private MetricsCollector metricsCollector;

    private Processor processor;

    @BeforeEach
    void setUp() {
        processor = new Processor(taskStore, subscriber, metricsCollector);
    }

    @Test
    void testHandleSuccess() {
        ProcessCommonTaskDTO dto = createBaseDTO();
        boolean result = processor.handle(dto);
        assertTrue(result);
        verify(taskStore).upsetTask(any(CommonTaskEntity.class));
        verify(metricsCollector).recordConsumed(true);
    }

    @Test
    void testHandleWithCronTask() {
        ProcessCommonTaskDTO dto = createBaseDTO();
        dto.setScheduledConf("0 * * * * ?");
        dto.setScheduledType("1");
        when(taskStore.upsetTask(any(CommonTaskEntity.class))).thenReturn(true);

        boolean result = processor.handle(dto);
        assertTrue(result);
    }

    @Test
    void testHandleWithOnceTask() {
        ProcessCommonTaskDTO dto = createBaseDTO();
        dto.setScheduledConf(null);
        dto.setExecuteTime(System.currentTimeMillis() + 3600_000);
        when(taskStore.upsetTask(any(CommonTaskEntity.class))).thenReturn(true);

        boolean result = processor.handle(dto);
        assertTrue(result);
    }

    @Test
    void testHandleWithNullTopicDefaultsToExecutorPool() {
        ProcessCommonTaskDTO dto = createBaseDTO();
        dto.setTopic(null);
        ArgumentCaptor<CommonTaskEntity> captor = ArgumentCaptor.forClass(CommonTaskEntity.class);
        when(taskStore.upsetTask(captor.capture())).thenReturn(true);

        processor.handle(dto);

        assertEquals("executorPool", captor.getValue().getTopic());
    }

    @Test
    void testHandleWithDisabledTask() {
        ProcessCommonTaskDTO dto = createBaseDTO();
        dto.setEnable(false);
        ArgumentCaptor<CommonTaskEntity> captor = ArgumentCaptor.forClass(CommonTaskEntity.class);
        when(taskStore.upsetTask(captor.capture())).thenReturn(true);

        processor.handle(dto);

        assertEquals("0", captor.getValue().getEnable());
    }

    @Test
    void testHandleWithCustomTopic() {
        ProcessCommonTaskDTO dto = createBaseDTO();
        dto.setTopic("customTopic");
        ArgumentCaptor<CommonTaskEntity> captor = ArgumentCaptor.forClass(CommonTaskEntity.class);
        when(taskStore.upsetTask(captor.capture())).thenReturn(true);

        processor.handle(dto);

        assertEquals("customTopic", captor.getValue().getTopic());
    }

    @Test
    void testRegistrationAsMessageHandler() {
        verify(subscriber).registerHandler(processor);
    }

    private ProcessCommonTaskDTO createBaseDTO() {
        ProcessCommonTaskDTO dto = new ProcessCommonTaskDTO();
        dto.setTaskName("testTask");
        dto.setBizName("testBiz");
        dto.setBizGroup("testGroup");
        dto.setScheduledConf("0 0 12 * * ?");
        dto.setScheduledType("1");
        dto.setEnable(true);
        dto.setPayload("{\"key\":\"value\"}");
        dto.setTopic("testTopic");
        return dto;
    }
}
