package com.executor.xxljobexecutormqimprove.dashboard.service;

import com.executor.xxljobexecutormqimprove.dashboard.store.DashboardStore;
import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DashboardStore dashboardStore;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService();
        // Manually set the mock
        try {
            java.lang.reflect.Field field = DashboardService.class.getDeclaredField("dashboardStore");
            field.setAccessible(true);
            field.set(dashboardService, dashboardStore);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testStats() {
        when(dashboardStore.countStats(anyLong())).thenReturn(Map.of(
                "total", 10L, "enabled", 8L, "pending", 3L,
                "disabled", 2L, "processing", 1L, "stuck", 0L
        ));

        Map<String, Object> result = dashboardService.stats();

        assertEquals(10L, result.get("total"));
        assertEquals(8L, result.get("enabled"));
        assertEquals(3L, result.get("pending"));
        assertEquals(2L, result.get("disabled"));
        assertEquals(1L, result.get("processing"));
        assertEquals(0L, result.get("stuck"));
    }

    @Test
    void testTasks() {
        List<CommonTaskEntity> list = List.of(new CommonTaskEntity());
        when(dashboardStore.selectTasksPage(eq(0), eq(20), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull())).thenReturn(list);
        when(dashboardStore.countTasks(isNull(), isNull(), isNull(), isNull(), isNull())).thenReturn(25L);

        Map<String, Object> result = dashboardService.tasks(1, 20, null, null, null, null, null, null, null);

        assertEquals(list, result.get("list"));
        assertEquals(25L, result.get("total"));
        assertEquals(1, result.get("page"));
        assertEquals(20, result.get("size"));
        assertEquals(2L, result.get("totalPages"));
    }

    @Test
    void testTasksTotalPagesCalculation() {
        when(dashboardStore.selectTasksPage(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(dashboardStore.countTasks(any(), any(), any(), any(), any())).thenReturn(100L);

        Map<String, Object> result = dashboardService.tasks(1, 20, null, null, null, null, null, null, null);

        assertEquals(5L, result.get("totalPages"));
    }

    @Test
    void testToggleEnable() {
        when(dashboardStore.toggleEnable("task-1")).thenReturn(1);
        assertTrue(dashboardService.toggleEnable("task-1"));
    }

    @Test
    void testToggleEnableFailure() {
        when(dashboardStore.toggleEnable("task-1")).thenReturn(0);
        assertFalse(dashboardService.toggleEnable("task-1"));
    }

    @Test
    void testBatchToggleEnable() {
        when(dashboardStore.batchToggleEnable(anyList(), eq("1"))).thenReturn(3);
        assertTrue(dashboardService.batchToggleEnable(List.of("a", "b", "c"), "1"));
    }

    @Test
    void testDelete() {
        when(dashboardStore.deleteById("task-1")).thenReturn(1);
        assertTrue(dashboardService.delete("task-1"));
    }

    @Test
    void testBatchDelete() {
        when(dashboardStore.batchDeleteByIds(anyList())).thenReturn(2);
        assertTrue(dashboardService.batchDelete(List.of("a", "b")));
    }

    @Test
    void testReleaseTask() {
        when(dashboardStore.releaseTask("task-1")).thenReturn(1);
        assertTrue(dashboardService.releaseTask("task-1"));
    }

    @Test
    void testBatchReleaseTasks() {
        when(dashboardStore.batchReleaseTasks(anyList())).thenReturn(2);
        assertTrue(dashboardService.batchReleaseTasks(List.of("a", "b")));
    }

    @Test
    void testGetTaskById() {
        CommonTaskEntity entity = new CommonTaskEntity();
        entity.setId("task-1");
        when(dashboardStore.getTaskById("task-1")).thenReturn(entity);

        CommonTaskEntity result = dashboardService.getTaskById("task-1");
        assertNotNull(result);
        assertEquals("task-1", result.getId());
    }

    @Test
    void testInvalidSortByRejected() {
        when(dashboardStore.selectTasksPage(anyInt(), anyInt(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull())).thenReturn(List.of());
        when(dashboardStore.countTasks(isNull(), isNull(), isNull(), isNull(), isNull())).thenReturn(0L);

        // Should not throw, should just reset sortBy to null
        Map<String, Object> result = dashboardService.tasks(1, 20, null, null, null, null, null, "evil; DROP TABLE", "asc");
        assertNotNull(result);
    }
}
