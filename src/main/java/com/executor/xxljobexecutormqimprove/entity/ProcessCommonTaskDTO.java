package com.executor.xxljobexecutormqimprove.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class ProcessCommonTaskDTO implements Serializable{
    private String taskId;
    private String taskName;
    private String bizName;
    private String bizGroup;
    private String scheduledConf;
    private String scheduledType;
    private Long executeTime;
    private Boolean enable;
    private String payload;
    private String topic;
}

