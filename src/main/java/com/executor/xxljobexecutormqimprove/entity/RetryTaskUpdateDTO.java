package com.executor.xxljobexecutormqimprove.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RetryTaskUpdateDTO {
    private String id;
    private Long nextTriggerTime;
    private Integer retryCount;
}
