package com.executor.xxljobexecutormqimprove.process;

import com.alibaba.fastjson.JSONObject;
import com.executor.xxljobexecutormqimprove.entity.ProcessCommonTaskDTO;
import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.entity.RocketMQEntity;
import com.executor.xxljobexecutormqimprove.core.base.CommonTaskBaseService;
import com.executor.xxljobexecutormqimprove.util.CronTimeUtil;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class Processor implements MessageListenerConcurrently {

    private Logger logger = LoggerFactory.getLogger(Processor.class);

    @Autowired
    private RocketMQEntity rocketMQEntity;

    @Autowired
    private CommonTaskBaseService commonTaskBaseService;

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void init() throws MQClientException {
        consumer = new DefaultMQPushConsumer();
        consumer.setNamesrvAddr(rocketMQEntity.getAddress());
        consumer.setConsumerGroup(rocketMQEntity.getConsumerGroup());
        consumer.unsubscribe(rocketMQEntity.getTopic());
        consumer.start();
        logger.info("----initConsumer----");
    }
    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext consumeConcurrentlyContext) {
        for (MessageExt msg : msgs){
            try{
                String messageBody = new String(msg.getBody());
                ProcessCommonTaskDTO task = JSONObject.parseObject(messageBody, ProcessCommonTaskDTO.class);
                process(task);
                logger.info("收到信息{}",task);
            }catch (Exception e){
                logger.info("收到信息失败");
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    private void process(ProcessCommonTaskDTO taskDTO) {
        // 补充本地生成字段
        CommonTaskEntity entity = transFormat(taskDTO);
        logger.info("转为任务实体: {}", entity);
        commonTaskBaseService.upsetTask(entity);

    }

    private CommonTaskEntity transFormat(ProcessCommonTaskDTO dto) {
        CommonTaskEntity entity = new CommonTaskEntity();

        entity.setId(System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999));
        entity.setTaskName(dto.getTaskName());
        entity.setBizName(dto.getBizName());
        entity.setBizGroup(dto.getBizGroup());
        entity.setScheduledConf(dto.getScheduledConf());
        //如果为false则执行一次
        if (dto.getScheduledConf() == null){
            entity.setNextTriggerTime(dto.getExecuteTime());
        }else{
            try {
                entity.setNextTriggerTime(CronTimeUtil.getNextTriggerTime(dto.getScheduledConf(),System.currentTimeMillis()));
            }catch (Exception e){
                logger.error("时间戳生成失败，内部错误");
            }
        }
        entity.setScheduledType(dto.getScheduledType());
        //1为开启true; 0为关闭 false
        entity.setEnable(dto.getEnable() != null && dto.getEnable() ? "1" : "0");

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        entity.setCreateAt(now);
        entity.setUpdateAt(now);

        entity.setPayload(dto.getPayload());

        return entity;
    }



}
