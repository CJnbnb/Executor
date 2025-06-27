package com.executor.xxljobexecutormqimprove.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommonTaskEntity {
    private String id;
    private String taskId;
    private String taskName;
    private String bizName;
    private String bizGroup;
    private Long nextTriggerTime;
    private Long lastTriggerTime;
    private String scheduledConf;
    private String scheduledType;
    private LocalDateTime createAt;
    private LocalDateTime updateAt;
    private String enable;
    private String payload;
    private String topic;
<<<<<<< HEAD
    private String process;
=======
    private Long locked_at;
>>>>>>> dev
}
