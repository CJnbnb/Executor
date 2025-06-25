package com.executor.xxljobexecutormqimprove.process.bus;

import com.executor.xxljobexecutormqimprove.bus.api.TaskChangeEvent;
import com.executor.xxljobexecutormqimprove.core.base.CommonTaskBaseService;
import com.executor.xxljobexecutormqimprove.core.service.CommonTaskService;
import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class TaskChangeEventListener {

    @Autowired
    private CommonTaskService commonTaskService;

    @Autowired
    private CommonTaskBaseService commonTaskBaseService;
    @Resource
    private EventBus eventBus;

    @PostConstruct
    public void register() {
        eventBus.register(this);
    }

    @Subscribe
    public void handleTaskChange(TaskChangeEvent event) {
        ProduceCommonTaskMessage task = event.getTaskMessage();
        String id = event.getId();
        commonTaskService.changeTaskInfo(task);
        commonTaskBaseService.unlockTask(id);
    }
}