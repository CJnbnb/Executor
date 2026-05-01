package com.executor.xxljobexecutormqimprove.dashboard.store;

import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;

import java.util.List;
import java.util.Map;

public interface DashboardStore {

    Map<String, Long> countStats(long now);

    List<CommonTaskEntity> selectTasksPage(int offset, int size, String taskName, String bizName);

    long countTasks(String taskName, String bizName);

    int toggleEnable(String id);

    int deleteById(String id);
}
