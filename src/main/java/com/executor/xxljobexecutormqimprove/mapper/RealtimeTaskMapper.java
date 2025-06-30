package com.executor.xxljobexecutormqimprove.mapper;

import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.model.entity.RealTimeTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RealtimeTaskMapper {
    // 插入或更新任务
    boolean upsetTaskInfo(RealTimeTaskEntity realTimeTaskEntity);

    // 更新任务触发信息
    boolean updateTaskTriggerInfo(RealTimeTaskEntity realTimeTaskEntity);

    // 查询所有可调度任务（可用于时间轮预读）
    List<RealTimeTaskEntity> selectSchedulableTasks(@Param("end") Long end, @Param("limit") Integer limit);

    ProduceCommonTaskMessage loadById(@Param("id") String id);
}