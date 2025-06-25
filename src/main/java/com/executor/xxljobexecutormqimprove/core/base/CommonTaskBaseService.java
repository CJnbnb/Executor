package com.executor.xxljobexecutormqimprove.core.base;

import com.executor.xxljobexecutormqimprove.entity.ChangeTaskInfoDTO;
import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.mapper.CommonTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CommonTaskBaseService {
    @Autowired
    private CommonTaskMapper commonTaskMapper;
    public boolean upsetTask(CommonTaskEntity commonTaskEntity){
        return commonTaskMapper.upsetTskInfo(commonTaskEntity);
    }

    public int lockTaskById(List<String> ids) {
        return commonTaskMapper.lockTaskById(ids);
    }

    public List<ProduceCommonTaskMessage> lockAndSelectTasks(String bizName,String bizGroup,Long end,Integer limit){
        return commonTaskMapper.lockAndSelectTasks(bizName,bizGroup,end,limit);
    }

    public List<ProduceCommonTaskMessage> lockAndSelectTasksByShard(String bizName,String bizGroup,Long end,Integer limit,Integer shardCount,Integer shardIndex){
        return commonTaskMapper.lockAndSelectTasksByShard(bizName,bizGroup,end,limit,shardCount,shardIndex);
    }
    public int BatchUnlockTasks(List<String> ids){
        return commonTaskMapper.BatchUnlockTasks(ids);
    }

    public int unlockTask(String id){
        return commonTaskMapper.unlocksTask(id);
    }

    public void changeTaskInfo(ChangeTaskInfoDTO changeTaskInfoDTO){
        commonTaskMapper.updateTaskTriggerInfo(changeTaskInfoDTO);
    }

    public int deleteData(){
        return commonTaskMapper.deleteDisabledTasks();
    }
}
