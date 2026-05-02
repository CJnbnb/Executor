package com.executor.xxljobexecutormqimprove.mq;

import com.executor.xxljobexecutormqimprove.model.dto.ProcessCommonTaskDTO;

@FunctionalInterface
public interface MessageHandler {

    boolean handle(ProcessCommonTaskDTO task);
}
