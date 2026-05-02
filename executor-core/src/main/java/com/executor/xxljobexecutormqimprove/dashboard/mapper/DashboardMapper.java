package com.executor.xxljobexecutormqimprove.dashboard.mapper;

import com.executor.xxljobexecutormqimprove.model.entity.CommonTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {

    Map<String, Object> countStats(@Param("now") long now);

    List<CommonTaskEntity> selectTasksPage(@Param("offset") int offset,
                                           @Param("size") int size,
                                           @Param("taskName") String taskName,
                                           @Param("bizName") String bizName,
                                           @Param("bizGroup") String bizGroup,
                                           @Param("enable") String enable,
                                           @Param("process") String process,
                                           @Param("sortBy") String sortBy,
                                           @Param("sortDir") String sortDir);

    long countTasks(@Param("taskName") String taskName,
                    @Param("bizName") String bizName,
                    @Param("bizGroup") String bizGroup,
                    @Param("enable") String enable,
                    @Param("process") String process);

    int toggleEnable(@Param("id") String id);

    int batchToggleEnable(@Param("ids") List<String> ids, @Param("enable") String enable);

    int deleteById(@Param("id") String id);

    int batchDeleteByIds(@Param("ids") List<String> ids);

    int releaseTask(@Param("id") String id);

    int batchReleaseTasks(@Param("ids") List<String> ids);

    CommonTaskEntity selectTaskById(@Param("id") String id);
}
