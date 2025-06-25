package com.executor.xxljobexecutormqimprove.core.service;

import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;

import java.util.List;

public interface CommonTaskService {
     void changeTaskInfo(ProduceCommonTaskMessage produceCommonTaskMessage);

     void batchChangeTaskInfo(List<ProduceCommonTaskMessage> produceCommonTaskMessageList);
}
