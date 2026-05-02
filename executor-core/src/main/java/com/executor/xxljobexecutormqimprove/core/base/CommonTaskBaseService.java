package com.executor.xxljobexecutormqimprove.core.base;

import com.executor.xxljobexecutormqimprove.Enum.ProcessEnum;
import com.executor.xxljobexecutormqimprove.mapper.CommonTaskMapper;
import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.model.dto.ChangeTaskInfoDTO;
import com.executor.xxljobexecutormqimprove.model.entity.CommonTaskEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CommonTaskBaseService {

    @Autowired
    private CommonTaskMapper commonTaskMapper;

    @Autowired
    private TaskEventLogBaseService logService;

    public boolean upsetTask(CommonTaskEntity commonTaskEntity) {
        boolean ok = commonTaskMapper.upsetTskInfo(commonTaskEntity);
        try {
            logService.logById(commonTaskEntity.getId(), commonTaskEntity.getTaskName(),
                    commonTaskEntity.getBizName(), commonTaskEntity.getBizGroup(),
                    "SCHEDULED", null, ProcessEnum.PENDING, null);
        } catch (Exception e) {
            // 日志写入失败不影响主流程
        }
        return ok;
    }

    public int lockTaskById(List<String> ids) {
        return commonTaskMapper.lockTaskById(ids);
    }

    public List<ProduceCommonTaskMessage> lockAndSelectTasks(String bizName, String bizGroup, Long end, Integer limit) {
        return commonTaskMapper.lockAndSelectTasks(bizName, bizGroup, end, limit);
    }

    public List<ProduceCommonTaskMessage> lockAndSelectTasksByShard(String bizName, String bizGroup, Long end, Integer limit, Integer shardCount, Integer shardIndex) {
        return commonTaskMapper.lockAndSelectTasksByShard(bizName, bizGroup, end, limit, shardCount, shardIndex);
    }

    public int unlockTasks(List<String> ids) {
        return commonTaskMapper.unlockTasks(ids);
    }

    public int unlockTaskById(String id) {
        return commonTaskMapper.unlockTaskById(id);
    }

    public boolean changeTaskInfo(ChangeTaskInfoDTO changeTaskInfoDTO) {
        return commonTaskMapper.updateTaskTriggerInfo(changeTaskInfoDTO);
    }

    public void batchChangeTaskInfo(List<ChangeTaskInfoDTO> dtoList) {
        commonTaskMapper.batchUpdateTaskTriggerInfo(dtoList);
    }

    public List<String> findTimeoutProcessingTaskIds(Long timeNow) {
        return commonTaskMapper.selectTimeoutProcessingTaskIDs(timeNow);
    }

    public int unlockExceptionTasks(List<String> ids) {
        int count = commonTaskMapper.unlockExceptionTasks(ids);
        if (count > 0) {
            for (String id : ids) {
                logService.logById(id, null, null, null,
                        "TIMEOUT_RESET", ProcessEnum.PROCESSING, ProcessEnum.PENDING, "timeout reset to pending");
            }
        }
        return count;
    }

    public int deleteData() {
        return commonTaskMapper.deleteDisabledTasks();
    }
}
