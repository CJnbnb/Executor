package com.executor.xxljobexecutormqimprove.core.base;

import com.executor.xxljobexecutormqimprove.mapper.TaskEventLogMapper;
import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.model.entity.TaskEventLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TaskEventLogBaseService {

    @Autowired
    private TaskEventLogMapper mapper;

    public void insert(TaskEventLog log) {
        mapper.insert(log);
    }

    public void log(ProduceCommonTaskMessage task, String eventType, String fromStatus, String toStatus, String msg) {
        TaskEventLog log = new TaskEventLog();
        log.setTaskId(task.getId());
        log.setTaskName(task.getTaskName());
        log.setBizName(null); // ProduceCommonTaskMessage 不含 bizName/bizGroup
        log.setBizGroup(null);
        log.setEventType(eventType);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setEventMsg(msg);
        log.setEventTime(LocalDateTime.now());
        mapper.insert(log);
    }

    public void logById(String taskId, String taskName, String bizName, String bizGroup,
                        String eventType, String fromStatus, String toStatus, String msg) {
        TaskEventLog log = new TaskEventLog();
        log.setTaskId(taskId);
        log.setTaskName(taskName);
        log.setBizName(bizName);
        log.setBizGroup(bizGroup);
        log.setEventType(eventType);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setEventMsg(msg);
        log.setEventTime(LocalDateTime.now());
        mapper.insert(log);
    }
}
