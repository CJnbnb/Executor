package com.executor.xxljobexecutormqimprove.dashboard.service;

import com.executor.xxljobexecutormqimprove.dashboard.store.DashboardStore;
import com.executor.xxljobexecutormqimprove.model.entity.CommonTaskEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    @Autowired
    private DashboardStore dashboardStore;

    public Map<String, Object> stats() {
        Map<String, Object> raw = dashboardStore.countStats(System.currentTimeMillis());
        Map<String, Object> result = new HashMap<>();
        result.put("total", toLong(raw.get("total")));
        result.put("enabled", toLong(raw.get("enabled")));
        result.put("pending", toLong(raw.get("pending")));
        result.put("disabled", toLong(raw.get("disabled")));
        result.put("processing", toLong(raw.get("processing")));
        result.put("stuck", toLong(raw.get("stuck")));
        return result;
    }

    private long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }

    private static final java.util.Set<String> ALLOWED_SORT_COLUMNS = java.util.Set.of(
            "task_name", "biz_name", "biz_group", "next_trigger_time", "create_at", "update_at"
    );

    public Map<String, Object> tasks(int page, int size, String taskName, String bizName,
                                      String bizGroup, String enable, String process,
                                      String sortBy, String sortDir) {
        if (sortBy != null && !ALLOWED_SORT_COLUMNS.contains(sortBy)) {
            sortBy = null;
            sortDir = null;
        }
        if (!"asc".equalsIgnoreCase(sortDir) && !"desc".equalsIgnoreCase(sortDir)) {
            sortDir = null;
        }
        int offset = (page - 1) * size;
        List<CommonTaskEntity> list = dashboardStore.selectTasksPage(offset, size,
                taskName, bizName, bizGroup, enable, process, sortBy, sortDir);
        long total = dashboardStore.countTasks(taskName, bizName, bizGroup, enable, process);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", (total + size - 1) / size);
        return result;
    }

    public boolean toggleEnable(String id) {
        return dashboardStore.toggleEnable(id) > 0;
    }

    public boolean batchToggleEnable(List<String> ids, String enable) {
        return dashboardStore.batchToggleEnable(ids, enable) > 0;
    }

    public boolean delete(String id) {
        return dashboardStore.deleteById(id) > 0;
    }

    public boolean batchDelete(List<String> ids) {
        return dashboardStore.batchDeleteByIds(ids) > 0;
    }

    public boolean releaseTask(String id) {
        return dashboardStore.releaseTask(id) > 0;
    }

    public boolean batchReleaseTasks(List<String> ids) {
        return dashboardStore.batchReleaseTasks(ids) > 0;
    }

    public CommonTaskEntity getTaskById(String id) {
        return dashboardStore.getTaskById(id);
    }
}
