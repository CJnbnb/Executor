package com.executor.xxljobexecutormqimprove.dashboard.store.mybatis;

import com.executor.xxljobexecutormqimprove.dashboard.mapper.DashboardMapper;
import com.executor.xxljobexecutormqimprove.dashboard.store.DashboardStore;
import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;

import java.util.List;
import java.util.Map;

public class MyBatisDashboardStore implements DashboardStore {

    private final DashboardMapper mapper;

    public MyBatisDashboardStore(DashboardMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Map<String, Long> countStats(long now) {
        return mapper.countStats(now);
    }

    @Override
    public List<CommonTaskEntity> selectTasksPage(int offset, int size, String taskName, String bizName) {
        return mapper.selectTasksPage(offset, size, taskName, bizName);
    }

    @Override
    public long countTasks(String taskName, String bizName) {
        return mapper.countTasks(taskName, bizName);
    }

    @Override
    public int toggleEnable(String id) {
        return mapper.toggleEnable(id);
    }

    @Override
    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
