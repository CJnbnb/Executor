package com.executor.xxljobexecutormqimprove.mq;

import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;

public interface MessagePublisher {

    boolean send(ProduceCommonTaskMessage message);
}
