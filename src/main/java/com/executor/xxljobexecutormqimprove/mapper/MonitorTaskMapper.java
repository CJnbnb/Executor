package com.executor.xxljobexecutormqimprove.mapper;

import com.executor.xxljobexecutormqimprove.model.MonitorTaskVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface MonitorTaskMapper {
    List<MonitorTaskVO> selectAllTasks();
    List<MonitorTaskVO> selectExceptionTasks();
    List<MonitorTaskVO> selectRecentTasks(@Param("limit") int limit);
    MonitorTaskVO selectTaskById(@Param("id") String id);
    int countByProcess(@Param("process") String process);
    int countAll();

    // 新增：分real/common的查询
    List<MonitorTaskVO> selectAllCommonTasks();
    List<MonitorTaskVO> selectAllRealTasks();
    int countCommonByProcess(@Param("process") String process);
    int countRealByProcess(@Param("process") String process);
} 