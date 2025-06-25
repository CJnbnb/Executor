package com.executor.xxljobexecutormqimprove.bus.listener;

import com.executor.xxljobexecutormqimprove.bus.api.type.OperationType;

public interface EventBusListener {
    OperationType getType();

}
