package com.executor.xxljobexecutormqimprove.core.schedulerhandler;

import com.executor.xxljobexecutormqimprove.core.base.CommonTaskBaseService;
import com.executor.xxljobexecutormqimprove.core.service.CommonTaskService;
import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
import com.executor.xxljobexecutormqimprove.producer.ProducerMessage;
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

import java.util.List;
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
    @XxlJob("Executor")
    public void producerMessage(){
        /**
         * 加个校验逻辑
         */
        String param = XxlJobHelper.getJobParam();
        String[] remoteArg = param.split(",");
        remoteArg = ValidateParamUtil.validateAndParseJobParam(param);
        String bizName = null;
        String bizGroup = null;
        if (remoteArg.length == 2){
            bizName = remoteArg[0];
            bizGroup = remoteArg[1];
        }

        long now = System.currentTimeMillis();
        long triggerTime = now;

        //指定事务位置
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("lockData");
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = transactionManager.getTransaction(def);

        //短事务提交
        List<ProduceCommonTaskMessage> produceCommonTaskMessageList;
        List<String> ids;
        try {
            produceCommonTaskMessageList = commonTaskBaseService.lockAndSelectTasks(bizName,bizGroup,triggerTime,LIMIT_COUNT);
            if (produceCommonTaskMessageList.isEmpty()) return ;
            ids = produceCommonTaskMessageList.stream().map(ProduceCommonTaskMessage::getId).collect(Collectors.toList());
            commonTaskBaseService.lockTaskById(ids);
            transactionManager.commit(status);
            logger.info("锁定事务成功");
        }catch (Exception e){
            logger.error("数据库事务添加错误{}",e.getMessage());
            throw e;
        }


        //发送业务MQ
        for (ProduceCommonTaskMessage task : produceCommonTaskMessageList) {
            boolean isSuccess = producerMessage.send(task); // 假设send方法支持参数
            logger.info("已发送任务: {}", task.getTaskName());
            if (isSuccess){
                commonTaskService.changeTaskInfo(task);
                logger.info("更改任务下次执行时间成功");
            }
        }

        // 4. 解锁（回写状态）
        commonTaskBaseService.unlockTasks(ids);

    }

}
