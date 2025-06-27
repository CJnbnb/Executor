package com.executor.xxljobexecutormqimprove.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommonTaskEntity {
    private String id;
    private String taskName;
    private String bizName;
    private String bizGroup;
    private Long nextTriggerTime;
    private Long lastTriggerTime;
    private String scheduledConf;
    private LocalDateTime createAt;
    private LocalDateTime updateAt;
    private String scheduledType;
    private String enable;
    private String payload;
    private String topic;
    private Long locked_at;
}
