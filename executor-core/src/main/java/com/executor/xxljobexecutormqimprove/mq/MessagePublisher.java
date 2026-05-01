package com.executor.xxljobexecutormqimprove.mq;

import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;

public interface MessagePublisher {

    boolean send(ProduceCommonTaskMessage message);
}
