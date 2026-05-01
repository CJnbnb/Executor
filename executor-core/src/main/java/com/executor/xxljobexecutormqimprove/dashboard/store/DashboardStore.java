package com.executor.xxljobexecutormqimprove.dashboard.store;

import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;

import java.util.List;
import java.util.Map;

public interface DashboardStore {

    Map<String, Object> countStats(long now);

    List<CommonTaskEntity> selectTasksPage(int offset, int size, String taskName, String bizName,
                                           String bizGroup, String enable, String process);

    long countTasks(String taskName, String bizName, String bizGroup, String enable, String process);

    int toggleEnable(String id);

    int batchToggleEnable(List<String> ids, String enable);

    int deleteById(String id);

    int batchDeleteByIds(List<String> ids);

    int releaseTask(String id);

    int batchReleaseTasks(List<String> ids);
}
