package com.executor.xxljobexecutormqimprove.entity;

import lombok.Data;

import java.io.Serializable;
@Data
public class ProduceCommonTaskMessage implements Serializable {
    private String id;
    private String taskName;
    private String scheduledConf;
    private String scheduledType;
    private String payload;
    private String enable;
    private String topic;
    private String process;
    private Long nextTriggerTime;
    private Long lastTriggerTime;
}
