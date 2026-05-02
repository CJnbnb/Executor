package com.executor.xxljobexecutormqimprove.model.entity;

import lombok.Data;

@Data
public class RocketMQEntity {
    private String topic;
    private String consumerGroup;
    private String address;
    private String producerGroup;
}
