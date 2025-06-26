//package com.executor.xxljobexecutormqimprove.core.thread;
//
//import com.executor.xxljobexecutormqimprove.core.context.ShardContext;
//import com.executor.xxljobexecutormqimprove.entity.ProduceCommonTaskMessage;
//import com.executor.xxljobexecutormqimprove.mapper.RealtimeTaskMapper;
//import com.executor.xxljobexecutormqimprove.producer.ProducerMessage;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.jdbc.datasource.DataSourceTransactionManager;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.TransactionDefinition;
//import org.springframework.transaction.TransactionStatus;
//import org.springframework.transaction.support.DefaultTransactionDefinition;
//
//import java.sql.Connection;
//import java.sql.PreparedStatement;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.TimeUnit;
//import java.util.stream.Collectors;
//
//@Service
//public class SchedulerRealtimeService {
//
//    @Autowired
//    private ProducerMessage producerMessage;
//    @Autowired
//    private DataSourceTransactionManager transactionManager;
//    @Autowired
//    private RealtimeTaskMapper realtimeTaskMapper;
//
//    private static Logger logger = LoggerFactory.getLogger(SchedulerRealtimeService.class);
//
//    private Thread scheduleThread;
//    private Thread ringThread;
//    //保持主存的可见性
//    private volatile boolean scheduleThreadToStop = false;
//    private volatile boolean ringThreadToStop = false;
//    //时间轮
//    private volatile static Map<Integer, List<ProduceCommonTaskMessage>> ringData = new ConcurrentHashMap<>();
//
//    public void start(){
//        scheduleThread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    //秒钟对齐
//                    TimeUnit.MILLISECONDS.sleep(5000 - System.currentTimeMillis()%1000 );
//                }catch (Throwable e){
//                    if (!scheduleThreadToStop){
//                        logger.error(e.getMessage(),e);
//                    }
//                }
//                logger.info("initThread");
//                //直接拿xxljob的魔法数字
//                int preReadCount = 6000;
//                //进行循环线程
//                while (!scheduleThreadToStop){
//                    //计算时间
//                    long start = System.currentTimeMillis();
//                    //指定事务位置
//                    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
//                    def.setName("lockData");
//                    def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
//                    TransactionStatus status = transactionManager.getTransaction(def);
//                    int shardIndex = ShardContext.getShardIndex();
//                    int shardTotal = ShardContext.getShardTotal();
//                    //短事务提交
//                    List<ProduceCommonTaskMessage> produceCommonTaskMessageList;
//                    List<String> ids;
//                    long nowTime = System.currentTimeMillis();
//                    try {
//                        if (shardIndex == -1 || shardTotal == -1) {
//                            produceCommonTaskMessageList = realtimeTaskMapper.lockAndSelectTasks(bizName, bizGroup, nowTime + 5000, 6000);
//                        } else {
//                            produceCommonTaskMessageList = realtimeTaskMapper.lockAndSelectTasksByShard(bizName, bizGroup, nowTime + 5000, 6000, shardTotal, shardIndex);
//                        }
//
//                        if (produceCommonTaskMessageList.isEmpty()) break;
//                        ids = produceCommonTaskMessageList.stream().map(ProduceCommonTaskMessage::getId).collect(Collectors.toList());
//                        realtimeTaskMapper.lockTaskById(ids);
//                        transactionManager.commit(status);
//                        logger.info("锁定事务成功");
//                    } catch (Exception e) {
//                        logger.error("数据库事务添加错误{}", e.getMessage());
//                        throw e;
//                    }
//                    if(produceCommonTaskMessageList != null && produceCommonTaskMessageList.size() > 0 ){
//                        for (ProduceCommonTaskMessage message : produceCommonTaskMessageList ){
//
//                            if (nowTime > message.getNextTriggerTime() + 5000){
//                                logger.warn(">>>>>>>>>>> xxl-job, schedule misfire, jobId = " + message.getId());
//                                refreshNextValidTime(jobInfo, new Date());
//                            }else if (nowTime > message.getNextTriggerTime()){
//                                producerMessage.send(message);
//
//                            }
//
//                        }
//                    }
//                }
//            }
//        });
//
//
//        ringThread  = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while (!ringThreadToStop){
//                    try {
//                        TimeUnit.MILLISECONDS.sleep(1000-System.currentTimeMillis() % 1000);
//                    } catch (Throwable e) {
//                        if (!ringThreadToStop) {
//                            logger.error(e.getMessage(), e);
//                        }
//                    }
//
//                    try {
//                        List<ProduceCommonTaskMessage> ringItemData = new ArrayList<>();
//                        int nowSecond = Calendar.getInstance().get(Calendar.SECOND);   // 避免处理耗时太长，跨过刻度，向前校验一个刻度；
//                        for (int i = 0; i < 2; i++) {
//                            List<ProduceCommonTaskMessage> tmpData = ringData.remove( (nowSecond+60-i)%60 );
//                            if (tmpData != null) {
//                                ringItemData.addAll(tmpData);
//                            }
//                        }
//
//                        logger.debug(">>>>>>>>>>> xxl-job, time-ring beat : " + nowSecond + " = " + Arrays.asList(ringItemData) );
//                        if (ringItemData.size() > 0) {
//                            // do trigger
//                            for (ProduceCommonTaskMessage produceCommonTaskMessage: ringItemData) {
//                                producerMessage.send(produceCommonTaskMessage);
//                            }
//                            // clear
//                            ringItemData.clear();
//                        }
//                    }catch (Throwable e) {
//                        if (!ringThreadToStop) {
//                            logger.error(">>>>>>>>>>> xxl-job, JobScheduleHelper#ringThread error:{}", e);
//                        }
//                    }
//                }
//            }
//        });
//        ringThread.setDaemon(true);
//        ringThread.setName("xxl-job, admin JobScheduleHelper#ringThread");
//        ringThread.start();
//    }
//}
