package com.executor.xxljobexecutormqimprove.core.base;

import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.model.entity.RealTimeTaskEntity;
import com.executor.xxljobexecutormqimprove.mapper.RealtimeTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RealtimeTaskBaseService {
    @Autowired
    private RealtimeTaskMapper realtimeTaskMapper;
    public boolean upsetTask(RealTimeTaskEntity realTimeTask){
        return realtimeTaskMapper.upsetTaskInfo(realTimeTask);
    }

    public ProduceCommonTaskMessage loadById(String id){
        return realtimeTaskMapper.loadById(id);
    }


}