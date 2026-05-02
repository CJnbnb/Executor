package com.executor.xxljobexecutormqimprove.service;

import com.alibaba.fastjson.JSONObject;
import com.executor.xxljobexecutormqimprove.Enum.ProcessEnum;
import com.executor.xxljobexecutormqimprove.Enum.TaskEnableEnum;
import com.executor.xxljobexecutormqimprove.config.MessageQueueProperties;
import com.executor.xxljobexecutormqimprove.core.base.CommonTaskBaseService;
import com.executor.xxljobexecutormqimprove.core.base.RealtimeTaskBaseService;
import com.executor.xxljobexecutormqimprove.model.dto.ProcessCommonTaskDTO;
import com.executor.xxljobexecutormqimprove.model.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.model.entity.RealTimeTaskEntity;
import com.executor.xxljobexecutormqimprove.util.CronTimeUtil;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.RPCHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TaskRegistrationService implements MessageListenerConcurrently {

    private final Logger logger = LoggerFactory.getLogger(TaskRegistrationService.class);

    private final MessageQueueProperties properties;
    private final CommonTaskBaseService commonTaskBaseService;
    private final RealtimeTaskBaseService realtimeTaskBaseService;

    private DefaultMQPushConsumer consumer;

    public TaskRegistrationService(MessageQueueProperties properties,
                                   CommonTaskBaseService commonTaskBaseService,
                                   RealtimeTaskBaseService realtimeTaskBaseService) {
        this.properties = properties;
        this.commonTaskBaseService = commonTaskBaseService;
        this.realtimeTaskBaseService = realtimeTaskBaseService;
    }

    @PostConstruct
    public void init() throws MQClientException {
        String ak = properties.getAccessKey();
        String sk = properties.getSecretKey();
        if (ak != null && !ak.isBlank() && sk != null && !sk.isBlank()) {
            RPCHook rpcHook = new AclClientRPCHook(new SessionCredentials(ak, sk));
            consumer = new DefaultMQPushConsumer(properties.getUpsertGroup(), rpcHook, new AllocateMessageQueueAveragely());
        } else {
            consumer = new DefaultMQPushConsumer(properties.getUpsertGroup());
        }
        consumer.setNamesrvAddr(properties.getNameserver());
        consumer.subscribe(properties.getTopic(), "*");
        consumer.registerMessageListener(this);
        consumer.start();
        logger.info("----TaskRegistrationService started, group={}, topic={}----",
                properties.getUpsertGroup(), properties.getTopic());
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
            logger.info("----TaskRegistrationService shutdown----");
        }
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            try {
                String body = new String(msg.getBody());
                ProcessCommonTaskDTO task = JSONObject.parseObject(body, ProcessCommonTaskDTO.class);
                process(task);
                logger.info("TaskRegistrationService consumed: taskName={}", task.getTaskName());
            } catch (Exception e) {
                logger.error("TaskRegistrationService consume failed: {}", e.getMessage());
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    private void process(ProcessCommonTaskDTO taskDTO) {
        if (taskDTO.getIsRealTime() != null && taskDTO.getIsRealTime()) {
            RealTimeTaskEntity entity = transFormatReal(taskDTO);
            realtimeTaskBaseService.upsetTask(entity);
        } else {
            CommonTaskEntity entity = transFormat(taskDTO);
            commonTaskBaseService.upsetTask(entity);
        }
    }

    private RealTimeTaskEntity transFormatReal(ProcessCommonTaskDTO dto) {
        RealTimeTaskEntity entity = new RealTimeTaskEntity();
        String id = System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);

        entity.setId(id);
        entity.setTaskName(dto.getTaskName());
        entity.setBizName(dto.getBizName());
        entity.setBizGroup(dto.getBizGroup());
        entity.setScheduledConf(dto.getScheduledConf());
        entity.setTopic(dto.getTopic() != null && !dto.getTopic().isEmpty() ? dto.getTopic() : "executorPool");
        if (dto.getScheduledConf() == null) {
            entity.setNextTriggerTime(dto.getExecuteTime());
        } else {
            try {
                entity.setNextTriggerTime(CronTimeUtil.getNextTriggerTime(dto.getScheduledConf(), System.currentTimeMillis()));
            } catch (Exception e) {
                logger.error("时间戳生成失败，内部错误");
            }
        }
        entity.setScheduledType(dto.getScheduledType());
        entity.setEnable(dto.getEnable() != null && dto.getEnable() ? TaskEnableEnum.TASK_ENABLE : TaskEnableEnum.TASK_UNABLE);
        entity.setCreateAt(LocalDateTime.now());
        entity.setUpdateAt(LocalDateTime.now());
        entity.setPayload(dto.getPayload());
        entity.setProcess(ProcessEnum.PENDING);
        entity.setTaskId(dto.getTaskId() != null ? dto.getTaskId() : id);
        return entity;
    }

    private CommonTaskEntity transFormat(ProcessCommonTaskDTO dto) {
        CommonTaskEntity entity = new CommonTaskEntity();
        String id = System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);

        entity.setId(id);
        entity.setTaskName(dto.getTaskName());
        entity.setBizName(dto.getBizName());
        entity.setBizGroup(dto.getBizGroup());
        entity.setScheduledConf(dto.getScheduledConf());
        entity.setTopic(dto.getTopic() != null && !dto.getTopic().isEmpty() ? dto.getTopic() : "executorPool");
        if (dto.getScheduledConf() == null) {
            entity.setNextTriggerTime(dto.getExecuteTime());
        } else {
            try {
                entity.setNextTriggerTime(CronTimeUtil.getNextTriggerTime(dto.getScheduledConf(), System.currentTimeMillis()));
            } catch (Exception e) {
                logger.error("时间戳生成失败，内部错误");
            }
        }
        entity.setScheduledType(dto.getScheduledType());
        entity.setEnable(dto.getEnable() != null && dto.getEnable() ? TaskEnableEnum.TASK_ENABLE : TaskEnableEnum.TASK_UNABLE);
        entity.setCreateAt(LocalDateTime.now());
        entity.setUpdateAt(LocalDateTime.now());
        entity.setPayload(dto.getPayload());
        entity.setProcess(ProcessEnum.PENDING);
        entity.setTaskId(dto.getTaskId() != null ? dto.getTaskId() : id);
        return entity;
    }
}
