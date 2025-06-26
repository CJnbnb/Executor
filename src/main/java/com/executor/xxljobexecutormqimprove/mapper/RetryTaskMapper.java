package com.executor.xxljobexecutormqimprove.mapper;

import com.executor.xxljobexecutormqimprove.entity.RetryTaskEntity;
import com.executor.xxljobexecutormqimprove.entity.RetryTaskUpdateDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RetryTaskMapper {
    void insertRetryTask(RetryTaskEntity retryTaskEntity);

    List<RetryTaskEntity> findRetryableTasks(Long currentTime);

    void deleteRetryTask(String id);

    void updateRecord(RetryTaskUpdateDTO retryTaskUpdateDTO);
}
