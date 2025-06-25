package com.executor.xxljobexecutormqimprove.bus.api.type;

public enum OperationType {
    unlock_task_event("解锁处理数据"),
    task_change_event("定时任务数据变更"),
    unKnow("未知类型")
    ;
    String desc;
    OperationType(String desc){
        this.desc = desc;
    }

    public static OperationType ToType(String operationType){
        OperationType[] types = OperationType.values();
        for (OperationType type : types){
            if (type.name().equals(operationType)){
                return type;
            }
        }
        return unKnow;
    }
}
