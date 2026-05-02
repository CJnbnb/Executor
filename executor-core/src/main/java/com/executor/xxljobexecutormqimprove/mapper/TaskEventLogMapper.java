package com.executor.xxljobexecutormqimprove.mapper;

import com.executor.xxljobexecutormqimprove.model.entity.TaskEventLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskEventLogMapper {

    int insert(TaskEventLog log);

    int batchInsert(@Param("list") List<TaskEventLog> logs);
}
