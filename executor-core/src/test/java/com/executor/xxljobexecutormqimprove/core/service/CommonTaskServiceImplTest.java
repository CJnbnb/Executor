package com.executor.xxljobexecutormqimprove.core.service;

import com.executor.xxljobexecutormqimprove.core.store.TaskStore;
import com.executor.xxljobexecutormqimprove.entity.ChangeTaskInfoDTO;
import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
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
    private TaskStore taskStore;

    private CommonTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommonTaskServiceImpl();
        try {
            java.lang.reflect.Field field = CommonTaskServiceImpl.class.getDeclaredField("taskStore");
            field.setAccessible(true);
            field.set(service, taskStore);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testChangeTaskInfoCronTask() {
        ProduceCommonTaskMessage task = createTask("1", "0 * * * * ?");
        when(taskStore.updateTaskTriggerInfo(any())).thenReturn(true);

        boolean result = service.changeTaskInfo(task);

        assertTrue(result);
        ArgumentCaptor<ChangeTaskInfoDTO> captor = ArgumentCaptor.forClass(ChangeTaskInfoDTO.class);
        verify(taskStore).updateTaskTriggerInfo(captor.capture());
        assertEquals("1", captor.getValue().getEnable());
        assertNotNull(captor.getValue().getNextTriggerTime());
    }

    @Test
    void testChangeTaskInfoOnceTask() {
        ProduceCommonTaskMessage task = createTask("2", null);
        task.setScheduledType("2");
        when(taskStore.updateTaskTriggerInfo(any())).thenReturn(true);

        boolean result = service.changeTaskInfo(task);

        assertTrue(result);
        ArgumentCaptor<ChangeTaskInfoDTO> captor = ArgumentCaptor.forClass(ChangeTaskInfoDTO.class);
        verify(taskStore).updateTaskTriggerInfo(captor.capture());
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

        verify(taskStore).batchUpdateTaskTriggerInfo(anyList());
    }

    @Test
    void testBatchChangeTaskInfoEmptyList() {
        service.batchChangeTaskInfo(List.of());
        verify(taskStore, never()).batchUpdateTaskTriggerInfo(anyList());
    }

    @Test
    void testBatchChangeTaskInfoNullList() {
        service.batchChangeTaskInfo(null);
        verify(taskStore, never()).batchUpdateTaskTriggerInfo(anyList());
    }

    @Test
    void testSingleAndBatchProduceSameResult() {
        ProduceCommonTaskMessage task = createTask("test", "0 0 12 * * ?");
        when(taskStore.updateTaskTriggerInfo(any())).thenReturn(true);

        service.changeTaskInfo(task);

        ArgumentCaptor<ChangeTaskInfoDTO> singleCaptor = ArgumentCaptor.forClass(ChangeTaskInfoDTO.class);
        verify(taskStore).updateTaskTriggerInfo(singleCaptor.capture());

        service.batchChangeTaskInfo(List.of(task));

        ArgumentCaptor<List<ChangeTaskInfoDTO>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(taskStore).batchUpdateTaskTriggerInfo(batchCaptor.capture());

        ChangeTaskInfoDTO single = singleCaptor.getValue();
        ChangeTaskInfoDTO batch = batchCaptor.getValue().get(0);

        assertEquals(single.getId(), batch.getId());
        assertEquals(single.getEnable(), batch.getEnable());
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
