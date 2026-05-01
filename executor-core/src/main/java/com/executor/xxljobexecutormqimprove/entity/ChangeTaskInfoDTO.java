package com.executor.xxljobexecutormqimprove.entity;

import lombok.Data;

@Data
public class ChangeTaskInfoDTO {
    private String id;
    private Long lastTriggerTime;
    private Long nextTriggerTime;
    private String enable;
}
