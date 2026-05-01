package com.executor.xxljobexecutormqimprove.core.schedulerhandler;

import com.executor.xxljobexecutormqimprove.core.service.CommonTaskService;
import com.executor.xxljobexecutormqimprove.core.store.TaskStore;
import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.mq.MessagePublisher;
import com.executor.xxljobexecutormqimprove.util.ValidateParamUtil;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
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
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MessagePublisher messagePublisher;

    @Autowired
    private TaskStore taskStore;

    @Autowired
    private CommonTaskService commonTaskService;

    @XxlJob("Executor")
    public void producerMessage(){
        /**
         * 加个校验逻辑
         */
        String param = XxlJobHelper.getJobParam();
        String[] remoteArg = ValidateParamUtil.validateAndParseJobParam(param);
        // 分片参数
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        String bizName = remoteArg[0];
        String bizGroup = remoteArg[1];
        long now = System.currentTimeMillis();
        logger.info("校验");

        //指定事务位置
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("lockData");
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = transactionManager.getTransaction(def);

        //短事务提交
        List<ProduceCommonTaskMessage> produceCommonTaskMessageList;
        List<String> ids;
        try {
//            分片参数处理
            if (shardIndex == -1 || shardTotal == -1){
                produceCommonTaskMessageList = taskStore.lockAndSelectTasks(bizName,bizGroup, now,LIMIT_COUNT);
            }else {
                produceCommonTaskMessageList = taskStore.lockAndSelectTasksByShard(bizName,bizGroup, now,LIMIT_COUNT,shardTotal,shardIndex);
            }

            if (produceCommonTaskMessageList.isEmpty()) {
                transactionManager.rollback(status);
                return;
            }
            ids = produceCommonTaskMessageList.stream().map(ProduceCommonTaskMessage::getId).collect(Collectors.toList());
            taskStore.lockTaskById(ids);
            transactionManager.commit(status);
            logger.info("锁定事务成功");
        }catch (Exception e){
            transactionManager.rollback(status);
            logger.error("数据库事务添加错误", e);
            throw e;
        }


        /**
         * 用线程池优化业务执行速度
         */
        List<Future<Boolean>> futures = new ArrayList<>();
        List<String> attemptedIds = new ArrayList<>();
        //发送业务MQ
        try(ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()){
            for (ProduceCommonTaskMessage task : produceCommonTaskMessageList) {
                attemptedIds.add(task.getId());
                futures.add(executor.submit(() ->{
                    boolean isSuccess = messagePublisher.send(task);
                    logger.info("已发送任务: {}", task.getTaskName());
                    if (isSuccess){
                        boolean taskSuccess = commonTaskService.changeTaskInfo(task);
                        if (taskSuccess){
                            logger.info("更改任务下次执行时间成功");
                        }
                    }
                    return isSuccess;
                }));
            }
            //阻塞等待
            for (Future<Boolean> future : futures){
                try{
                    future.get();
                }catch (Exception e){
                    logger.error("MQ异步任务发送异常", e);
                }
            }
        }

        // 4. 解锁（回写状态，所有尝试过的任务都解锁，失败的可下次重试）
        taskStore.unlockTasks(attemptedIds);

    }

}
