package com.executor.xxljobexecutormqimprove.dashboard.mapper;

import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {

    /** 统计数据：total, enabled, pending（nextTriggerTime < now）, disabled */
    Map<String, Long> countStats(@Param("now") long now);

    /** 分页查询任务列表，支持模糊搜索 */
    List<CommonTaskEntity> selectTasksPage(@Param("offset") int offset,
                                           @Param("size") int size,
                                           @Param("taskName") String taskName,
                                           @Param("bizName") String bizName);

    /** 符合搜索条件的总记录数 */
    long countTasks(@Param("taskName") String taskName,
                    @Param("bizName") String bizName);

    /** 切换启用/禁用 */
    int toggleEnable(@Param("id") String id);

    /** 删除任务 */
    int deleteById(@Param("id") String id);
}
