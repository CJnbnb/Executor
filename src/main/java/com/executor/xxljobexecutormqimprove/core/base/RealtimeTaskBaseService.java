package com.executor.xxljobexecutormqimprove.core.base;

import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.mapper.RealtimeTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RealtimeTaskBaseService {
    @Autowired
    private RealtimeTaskMapper realtimeTaskMapper;
    public boolean upsetTask(CommonTaskEntity commonTaskEntity){
        return realtimeTaskMapper.upsetTskInfo(commonTaskEntity);
    }
    // 其他方法可参考CommonTaskBaseService按需添加
} 