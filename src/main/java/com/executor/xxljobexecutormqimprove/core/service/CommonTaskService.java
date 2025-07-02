package com.executor.xxljobexecutormqimprove.core.service;

import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;

import java.util.List;

public interface CommonTaskService {
//     boolean changeTaskInfo(ProduceCommonTaskMessage produceCommonTaskMessage);

     void batchChangeTaskInfo(List<ProduceCommonTaskMessage> produceCommonTaskMessageList);

     void changeTask(ProduceCommonTaskMessage produceCommonTaskMessage);
}
