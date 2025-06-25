package com.executor.xxljobexecutormqimprove.bus.api;

import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class TaskChangeEvent implements Serializable {
    private ProduceCommonTaskMessage taskMessage;
    private String id;

    public TaskChangeEvent(ProduceCommonTaskMessage taskMessage, String id) {
        this.taskMessage = taskMessage;
        this.id = id;
    }
}
