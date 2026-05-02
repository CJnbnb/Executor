package com.executor.xxljobexecutormqimprove.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskEventLog {
    private Long id;
    private String taskId;
    private String taskName;
    private String bizName;
    private String bizGroup;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private String eventMsg;
    private LocalDateTime eventTime;
}
