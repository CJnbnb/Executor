package com.executor.xxljobexecutormqimprove.entity;

import lombok.Data;

@Data
public class CommonTaskEntity {
    private String id;
    private String taskName;
    private String bizName;
    private String bizGroup;
    private Long nextTriggerTime;
    private Long lastTriggerTime;
    private String scheduledConf;
    private String createAt;
    private String updateAt;
    private String scheduledType;
    private String enable;
    private String payload;
    private String topic;
}
