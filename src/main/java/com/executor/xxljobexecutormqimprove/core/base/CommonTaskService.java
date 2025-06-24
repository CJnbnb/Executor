package com.executor.xxljobexecutormqimprove.core.base;

import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.mapper.CommonTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CommonTaskService {
    @Autowired
    private CommonTaskMapper commonTaskMapper;
    public boolean upsetTask(CommonTaskEntity commonTaskEntity){
        return commonTaskMapper.upsetTskInfo();
    }

}
