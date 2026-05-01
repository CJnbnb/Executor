package com.executor.xxljobexecutormqimprove.mq;

import com.executor.xxljobexecutormqimprove.entity.ProcessCommonTaskDTO;

@FunctionalInterface
public interface MessageHandler {

    boolean handle(ProcessCommonTaskDTO task);
}
