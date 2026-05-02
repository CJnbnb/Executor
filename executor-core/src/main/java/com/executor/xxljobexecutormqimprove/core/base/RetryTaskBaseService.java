package com.executor.xxljobexecutormqimprove.core.base;

import com.executor.xxljobexecutormqimprove.model.entity.RetryTaskEntity;
import com.executor.xxljobexecutormqimprove.model.dto.RetryTaskUpdateDTO;
import com.executor.xxljobexecutormqimprove.mapper.RetryTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetryTaskBaseService {
    @Autowired
    private RetryTaskMapper retryTaskMapper;
    public void record(RetryTaskEntity retryTaskEntity){
        retryTaskMapper.insertRetryTask(retryTaskEntity);
    }

    public List<RetryTaskEntity> getRetryableTask(Long now){
        return retryTaskMapper.findRetryableTasks(now);
    }

    public void removeRetryTask(String id){
        retryTaskMapper.deleteRetryTask(id);
    }

    public void updateRecord(RetryTaskUpdateDTO retryTaskUpdateDTO){
        retryTaskMapper.updateRecord(retryTaskUpdateDTO);
    }
}
