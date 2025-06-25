package com.executor.xxljobexecutormqimprove.core.schedulerhandler;

import com.executor.xxljobexecutormqimprove.bus.api.TaskChangeEvent;
import com.executor.xxljobexecutormqimprove.core.base.CommonTaskBaseService;
import com.executor.xxljobexecutormqimprove.core.service.CommonTaskService;
import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.producer.ProducerMessage;
import com.executor.xxljobexecutormqimprove.util.ValidateParamUtil;
import com.google.common.eventbus.EventBus;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ProducerHandler {
    private static Logger logger = LoggerFactory.getLogger(ProducerHandler.class);

    private static final Integer LIMIT_COUNT = 200;

    @Autowired
    private DataSourceTransactionManager transactionManager;

    @Autowired
    private ProducerMessage producerMessage;

    @Autowired
    private CommonTaskBaseService commonTaskBaseService;

    @Autowired
    private CommonTaskService commonTaskService;

    @Resource
    private EventBus eventBus;

    @XxlJob("Executor")
    public void producerMessage(String bizName,String bizGroup){
        /**
         * 加个校验逻辑
         */
//        String param = XxlJobHelper.getJobParam();
//        String[] remoteArg = ValidateParamUtil.validateAndParseJobParam(param);
        // 分片参数
//        int shardIndex = XxlJobHelper.getShardIndex();
//        int shardTotal = XxlJobHelper.getShardTotal();
//        String bizName = remoteArg[0];
//        String bizGroup = remoteArg[1];
        long now = System.currentTimeMillis();

        //指定事务位置
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("lockData");
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = transactionManager.getTransaction(def);

        //短事务提交
        List<ProduceCommonTaskMessage> produceCommonTaskMessageList;
        List<String> ids;
        try {
            //分片参数处理
//            if (shardIndex == 0 && shardTotal == 1){
                produceCommonTaskMessageList = commonTaskBaseService.lockAndSelectTasks(bizName,bizGroup,now,LIMIT_COUNT);
//                logger.info("单机执行");
//            }else {
//                produceCommonTaskMessageList = commonTaskBaseService.lockAndSelectTasksByShard(bizName,bizGroup, now,LIMIT_COUNT,shardTotal,shardIndex);
//                logger.info("分片执行");
//            }

            if (produceCommonTaskMessageList.isEmpty()) return ;
            ids = produceCommonTaskMessageList.stream().map(ProduceCommonTaskMessage::getId).collect(Collectors.toList());
            commonTaskBaseService.lockTaskById(ids);
            transactionManager.commit(status);
            logger.info("锁定事务成功");
        }catch (Exception e){
            logger.error("数据库事务添加错误{}",e.getMessage());
            throw e;
        }


        /**
         * 用线程池优化业务执行速度
         */
        List<ProduceCommonTaskMessage> toChangeTasks = new ArrayList<>();
        List<Future<ProduceCommonTaskMessage>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ProduceCommonTaskMessage task : produceCommonTaskMessageList) {
                futures.add(executor.submit(() -> {
                    boolean isSuccess = producerMessage.send(task);
                    logger.info("已发送任务: {}", task.getTaskName());
                    return isSuccess ? task : null;
                }));
            }
            for (Future<ProduceCommonTaskMessage> future : futures) {
                try {
                    ProduceCommonTaskMessage result = future.get();
                    if (result != null) {
                        toChangeTasks.add(result);
                    }
                } catch (Exception e) {
                    logger.error("MQ异步任务发送异常{}", e.getMessage());
                }
            }
        }

// 2. 批量更改任务状态
        if (!toChangeTasks.isEmpty()) {
            commonTaskService.batchChangeTaskInfo(toChangeTasks);
            logger.info("批量更改任务状态成功");
        }
// 3. 批量解锁
        commonTaskBaseService.BatchUnlockTasks(ids);

    }

}
