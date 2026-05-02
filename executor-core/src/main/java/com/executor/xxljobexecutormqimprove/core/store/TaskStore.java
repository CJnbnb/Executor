package com.executor.xxljobexecutormqimprove.core.store;

import com.executor.xxljobexecutormqimprove.model.dto.ChangeTaskInfoDTO;
import com.executor.xxljobexecutormqimprove.model.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;

import java.util.List;

public interface TaskStore {

    boolean upsetTask(CommonTaskEntity entity);

    int lockTaskById(List<String> ids);

    List<ProduceCommonTaskMessage> lockAndSelectTasks(String bizName, String bizGroup, Long end, Integer limit);

    List<ProduceCommonTaskMessage> lockAndSelectTasksByShard(String bizName, String bizGroup, Long end,
                                                              Integer limit, Integer shardCount, Integer shardIndex);

    int unlockTasks(List<String> ids);

    boolean updateTaskTriggerInfo(ChangeTaskInfoDTO dto);

    void batchUpdateTaskTriggerInfo(List<ChangeTaskInfoDTO> list);

    int deleteDisabledTasks();
}
