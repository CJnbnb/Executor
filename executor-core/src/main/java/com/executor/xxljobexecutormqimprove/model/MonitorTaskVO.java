package com.executor.xxljobexecutormqimprove.model;

import lombok.Data;

@Data
public class MonitorTaskVO {
    private String id;
    private String taskName;
    private String bizName;
    private String bizGroup;
    private String scheduledConf;
    private String scheduledType;
    private String payload;
    private String enable;
    private String topic;
    private String process;
    private Long nextTriggerTime;
    private Long lastTriggerTime;
} 