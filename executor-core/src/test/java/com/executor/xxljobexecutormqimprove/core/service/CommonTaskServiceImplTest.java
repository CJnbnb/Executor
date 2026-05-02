package com.executor.xxljobexecutormqimprove.core.service;

import com.executor.xxljobexecutormqimprove.core.base.CommonTaskBaseService;
import com.executor.xxljobexecutormqimprove.model.dto.ChangeTaskInfoDTO;
import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommonTaskServiceImplTest {

    @Mock
    private CommonTaskBaseService commonTaskBaseService;

    private CommonTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommonTaskServiceImpl();
        try {
            java.lang.reflect.Field field = CommonTaskServiceImpl.class.getDeclaredField("commonTaskBaseService");
            field.setAccessible(true);
            field.set(service, commonTaskBaseService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testChangeTaskInfoCronTask() {
        ProduceCommonTaskMessage task = createTask("1", "0 * * * * ?");
        when(commonTaskBaseService.changeTaskInfo(any())).thenReturn(true);

        boolean result = service.changeTaskInfo(task);

        assertTrue(result);
        ArgumentCaptor<ChangeTaskInfoDTO> captor = ArgumentCaptor.forClass(ChangeTaskInfoDTO.class);
        verify(commonTaskBaseService).changeTaskInfo(captor.capture());
        assertEquals("1", captor.getValue().getEnable());
        assertNotNull(captor.getValue().getNextTriggerTime());
    }

    @Test
    void testChangeTaskInfoOnceTask() {
        ProduceCommonTaskMessage task = createTask("2", null);
        task.setScheduledType("2");
        when(commonTaskBaseService.changeTaskInfo(any())).thenReturn(true);

        boolean result = service.changeTaskInfo(task);

        assertTrue(result);
        ArgumentCaptor<ChangeTaskInfoDTO> captor = ArgumentCaptor.forClass(ChangeTaskInfoDTO.class);
        verify(commonTaskBaseService).changeTaskInfo(captor.capture());
        assertEquals("0", captor.getValue().getEnable());
        assertNull(captor.getValue().getNextTriggerTime());
    }

    @Test
    void testBatchChangeTaskInfo() {
        List<ProduceCommonTaskMessage> tasks = List.of(
                createTask("1", "0 * * * * ?"),
                createTask("2", "0 0 12 * * ?")
        );

        service.batchChangeTaskInfo(tasks);

        verify(commonTaskBaseService).batchChangeTaskInfo(anyList());
    }

    @Test
    void testBatchChangeTaskInfoEmptyList() {
        service.batchChangeTaskInfo(List.of());
        verify(commonTaskBaseService, never()).batchChangeTaskInfo(anyList());
    }

    @Test
    void testBatchChangeTaskInfoNullList() {
        service.batchChangeTaskInfo(null);
        verify(commonTaskBaseService, never()).batchChangeTaskInfo(anyList());
    }

    @Test
    void testSingleAndBatchProduceSameResult() {
        ProduceCommonTaskMessage task = createTask("test", "0 0 12 * * ?");
        when(commonTaskBaseService.changeTaskInfo(any())).thenReturn(true);

        service.changeTaskInfo(task);

        ArgumentCaptor<ChangeTaskInfoDTO> singleCaptor = ArgumentCaptor.forClass(ChangeTaskInfoDTO.class);
        verify(commonTaskBaseService).changeTaskInfo(singleCaptor.capture());

        service.batchChangeTaskInfo(List.of(task));

        ArgumentCaptor<List<ChangeTaskInfoDTO>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(commonTaskBaseService).batchChangeTaskInfo(batchCaptor.capture());

        ChangeTaskInfoDTO single = singleCaptor.getValue();
        ChangeTaskInfoDTO batch = batchCaptor.getValue().get(0);

        assertEquals(single.getId(), batch.getId());
        assertEquals(single.getEnable(), batch.getEnable());
    }

    @Test
    void testChangeTask() {
        ProduceCommonTaskMessage task = createTask("test", "0 * * * * ?");
        when(commonTaskBaseService.changeTaskInfo(any())).thenReturn(true);

        service.changeTask(task);

        verify(commonTaskBaseService).changeTaskInfo(any());
        verify(commonTaskBaseService).unlockTaskById("test");
    }

    private ProduceCommonTaskMessage createTask(String id, String cron) {
        ProduceCommonTaskMessage task = new ProduceCommonTaskMessage();
        task.setId(id);
        task.setTaskName("task-" + id);
        task.setScheduledConf(cron);
        task.setScheduledType(cron != null ? "1" : "2");
        task.setNextTriggerTime(System.currentTimeMillis());
        return task;
    }
}
