package com.executor.xxljobexecutormqimprove.core.store.mybatis;

import com.executor.xxljobexecutormqimprove.core.store.TaskStore;
import com.executor.xxljobexecutormqimprove.entity.ChangeTaskInfoDTO;
import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.mapper.CommonTaskMapper;

import java.util.List;

public class MyBatisTaskStore implements TaskStore {

    private final CommonTaskMapper mapper;

    public MyBatisTaskStore(CommonTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean upsetTask(CommonTaskEntity entity) {
        return mapper.upsetTskInfo(entity);
    }

    @Override
    public int lockTaskById(List<String> ids) {
        return mapper.lockTaskById(ids);
    }

    @Override
    public List<ProduceCommonTaskMessage> lockAndSelectTasks(String bizName, String bizGroup, Long end, Integer limit) {
        return mapper.lockAndSelectTasks(bizName, bizGroup, end, limit);
    }

    @Override
    public List<ProduceCommonTaskMessage> lockAndSelectTasksByShard(String bizName, String bizGroup, Long end,
                                                                     Integer limit, Integer shardCount, Integer shardIndex) {
        return mapper.lockAndSelectTasksByShard(bizName, bizGroup, end, limit, shardCount, shardIndex);
    }

    @Override
    public int unlockTasks(List<String> ids) {
        return mapper.unlockTasks(ids);
    }

    @Override
    public boolean updateTaskTriggerInfo(ChangeTaskInfoDTO dto) {
        return mapper.updateTaskTriggerInfo(dto);
    }

    @Override
    public void batchUpdateTaskTriggerInfo(List<ChangeTaskInfoDTO> list) {
        mapper.batchUpdateTaskTriggerInfo(list);
    }

    @Override
    public int deleteDisabledTasks() {
        return mapper.deleteDisabledTasks();
    }
}
