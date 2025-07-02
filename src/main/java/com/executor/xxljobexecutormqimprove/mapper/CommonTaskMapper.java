package com.executor.xxljobexecutormqimprove.mapper;

import com.executor.xxljobexecutormqimprove.model.dto.ChangeTaskInfoDTO;
import com.executor.xxljobexecutormqimprove.model.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
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

    List<ProduceCommonTaskMessage> lockAndSelectTasksByShard(
            String bizName,
            String bizGroup,
            long end,
            int limit,
            int shardCount,
            int shardIndex
    );

    int unlockTasks(@Param("ids") List<String> ids);

    int unlockTaskById(@Param("id") String id);

    boolean updateTaskTriggerInfo(ChangeTaskInfoDTO dto);
    void batchUpdateTaskTriggerInfo(@Param("list") List<ChangeTaskInfoDTO> list);
    List<String> selectTimeoutProcessingTaskIDs(@Param("now") Long now);
    int unlockExceptionTasks(@Param("ids") List<String> ids);
    int deleteDisabledTasks();
}
