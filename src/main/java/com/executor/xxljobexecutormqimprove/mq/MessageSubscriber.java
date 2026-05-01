package com.executor.xxljobexecutormqimprove.mq;

public interface MessageSubscriber {

    void registerHandler(MessageHandler handler);
}
