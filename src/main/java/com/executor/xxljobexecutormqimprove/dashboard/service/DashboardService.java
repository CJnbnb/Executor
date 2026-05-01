package com.executor.xxljobexecutormqimprove.dashboard.service;

import com.executor.xxljobexecutormqimprove.dashboard.store.DashboardStore;
import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
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
        Map<String, Long> raw = dashboardStore.countStats(System.currentTimeMillis());
        Map<String, Object> result = new HashMap<>();
        result.put("total", raw.getOrDefault("total", 0L));
        result.put("enabled", raw.getOrDefault("enabled", 0L));
        result.put("pending", raw.getOrDefault("pending", 0L));
        result.put("disabled", raw.getOrDefault("disabled", 0L));
        return result;
    }

    public Map<String, Object> tasks(int page, int size, String taskName, String bizName) {
        int offset = (page - 1) * size;
        List<CommonTaskEntity> list = dashboardStore.selectTasksPage(offset, size, taskName, bizName);
        long total = dashboardStore.countTasks(taskName, bizName);
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

    public boolean delete(String id) {
        return dashboardStore.deleteById(id) > 0;
    }
}
