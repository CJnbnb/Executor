package com.executor.xxljobexecutormqimprove.mapper;

import com.executor.xxljobexecutormqimprove.entity.ChangeTaskInfoDTO;
import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.entity.ProcessCommonTaskDTO;
import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommonTaskMapper {
    boolean upsetTskInfo(CommonTaskEntity commonTaskEntity);

    int lockTaskById(@Param("ids") List<String> ids);

    List<ProduceCommonTaskMessage> lockAndSelectTasks(
            @Param("bizName") String bizName,
            @Param("bizGroup") String bizGroup,
            @Param("end") Long end,
            @Param("limit") Integer limit
    );

    int unlockTasks(
            @Param("ids") List<String> ids
    );

    void updateTaskTriggerInfo(ChangeTaskInfoDTO dto);
}
