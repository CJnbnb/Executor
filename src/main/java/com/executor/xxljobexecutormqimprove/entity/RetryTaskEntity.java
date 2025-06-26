package com.executor.xxljobexecutormqimprove.entity;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class RetryTaskEntity {
    private String id;
    private Long nextTriggerTime;
    private Integer retryCount;
    private String args;
    private LocalDateTime createAt;
}
