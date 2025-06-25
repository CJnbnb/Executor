package com.executor.xxljobexecutormqimprove.core.base;

import com.executor.xxljobexecutormqimprove.entity.ChangeTaskInfoDTO;
import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.mapper.CommonTaskMapper;
import jakarta.annotation.Resource;
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
    public int unlockTasks(List<String> ids){
        return commonTaskMapper.unlockTasks(ids);
    }

    public boolean changeTaskInfo(ChangeTaskInfoDTO changeTaskInfoDTO){
        return commonTaskMapper.updateTaskTriggerInfo(changeTaskInfoDTO);
    }

    public void batchChangeTaskInfo(List<ChangeTaskInfoDTO> dtoList) {
        commonTaskMapper.batchUpdateTaskTriggerInfo(dtoList);
    }
    public int deleteData(){
        return commonTaskMapper.deleteDisabledTasks();
    }
}
