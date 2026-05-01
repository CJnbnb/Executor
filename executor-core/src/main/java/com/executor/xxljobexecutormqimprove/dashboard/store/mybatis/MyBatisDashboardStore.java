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
    public Map<String, Object> countStats(long now) {
        return mapper.countStats(now);
    }

    @Override
    public List<CommonTaskEntity> selectTasksPage(int offset, int size, String taskName, String bizName,
                                                   String bizGroup, String enable, String process) {
        return mapper.selectTasksPage(offset, size, taskName, bizName, bizGroup, enable, process);
    }

    @Override
    public long countTasks(String taskName, String bizName, String bizGroup, String enable, String process) {
        return mapper.countTasks(taskName, bizName, bizGroup, enable, process);
    }

    @Override
    public int toggleEnable(String id) {
        return mapper.toggleEnable(id);
    }

    @Override
    public int batchToggleEnable(List<String> ids, String enable) {
        return mapper.batchToggleEnable(ids, enable);
    }

    @Override
    public int deleteById(String id) {
        return mapper.deleteById(id);
    }

    @Override
    public int batchDeleteByIds(List<String> ids) {
        return mapper.batchDeleteByIds(ids);
    }

    @Override
    public int releaseTask(String id) {
        return mapper.releaseTask(id);
    }

    @Override
    public int batchReleaseTasks(List<String> ids) {
        return mapper.batchReleaseTasks(ids);
    }
}
