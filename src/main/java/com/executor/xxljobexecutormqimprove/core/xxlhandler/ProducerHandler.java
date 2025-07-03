package com.executor.xxljobexecutormqimprove.core.xxlhandler;

import com.executor.xxljobexecutormqimprove.core.base.CommonTaskBaseService;
import com.executor.xxljobexecutormqimprove.core.service.CommonTaskService;
import com.executor.xxljobexecutormqimprove.model.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.core.producer.MessageProducer;
import com.executor.xxljobexecutormqimprove.util.ValidateParamUtil;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
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

    private static final Integer LIMIT_COUNT = 1000;

    @Autowired
    private DataSourceTransactionManager transactionManager;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private CommonTaskBaseService commonTaskBaseService;

    @Autowired
    private CommonTaskService commonTaskService;

    private static final ExecutorService executors = Executors.newVirtualThreadPerTaskExecutor();

    //TODO 存在BUG一次好像只拉取200条，有问题
    @XxlJob("Executor")
    public void producerMessage() {
        long windowEnd = System.currentTimeMillis() + 1000;
        while (true) {
            /**
             * 加个校验逻辑
             * TODO
             * 可以考虑加一个taskName的索引
             */
            String param = XxlJobHelper.getJobParam();
            String[] remoteArg = ValidateParamUtil.validateAndParseJobParam(param);
            // 分片参数
            int shardIndex = XxlJobHelper.getShardIndex();
            int shardTotal = XxlJobHelper.getShardTotal();
            String bizName = remoteArg[0];
            String bizGroup = remoteArg[1];

            //指定事务位置
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setName("lockData");
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            TransactionStatus status = transactionManager.getTransaction(def);

            //短事务提交
            List<ProduceCommonTaskMessage> produceCommonTaskMessageList;
            List<String> ids;
            try {
//                走XxlJob的分片规则            分片参数处理
                if (shardIndex == 0 || shardTotal == 1) {
                    produceCommonTaskMessageList = commonTaskBaseService.lockAndSelectTasks(bizName, bizGroup, windowEnd, LIMIT_COUNT);
                } else {
                    produceCommonTaskMessageList = commonTaskBaseService.lockAndSelectTasksByShard(bizName, bizGroup, windowEnd, LIMIT_COUNT, shardTotal, shardIndex);
                }

                if (produceCommonTaskMessageList.isEmpty()) {
                    // 没有数据时也要提交事务，释放锁
                    transactionManager.commit(status);
                    logger.info("没有可处理的任务，事务已提交");
                    break;
                }

                ids = produceCommonTaskMessageList.stream().map(ProduceCommonTaskMessage::getId).collect(Collectors.toList());
                commonTaskBaseService.lockTaskById(ids);
                transactionManager.commit(status);
                logger.info("锁定事务成功");
            } catch (Exception e) {
                logger.error("数据库事务添加错误{}", e.getMessage());
                transactionManager.rollback(status);
                throw e;
            }


            /**
             * 用线程池优化业务执行速度
             */
            //发送业务MQ
            for (ProduceCommonTaskMessage task : produceCommonTaskMessageList) {
                executors.submit(() -> {
                    boolean isSuccess = messageProducer.send(task);
                    if (isSuccess){
                        logger.info("{}任务成功发送,详细信息为{}",task.getId(),task);
                    }else {
                        logger.error("{}消息发送失败，丢入重试队列,详细信息为{}",task.getId(),task);
                    }
                });
            }

            for (ProduceCommonTaskMessage task : produceCommonTaskMessageList){
                commonTaskService.changeTask(task);
            }

        }
    }


}
