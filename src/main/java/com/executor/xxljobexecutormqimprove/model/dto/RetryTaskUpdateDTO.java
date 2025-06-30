package com.executor.xxljobexecutormqimprove.model.dto;

import lombok.Data;

@Data
public class RetryTaskUpdateDTO {
    private String id;
    private Long nextTriggerTime;
    private Integer retryCount;
}
