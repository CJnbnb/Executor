//package com.executor.xxljobexecutormqimprove.core.thread;
//
//import com.executor.xxljobexecutormqimprove.core.service.RetryTaskService;
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.InitializingBean;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.concurrent.ExecutorService;
//
//@Service
//public class ScheduledRetryTaskService implements InitializingBean {
//
//    private Logger logger = LoggerFactory.getLogger(ScheduledRetryTaskService.class);
//
//    @Autowired
//    private RetryTaskService retryTaskService;
//
//    @Override
//    public void afterPropertiesSet() throws Exception {
//        Thread thread = new Thread(() ->{
//            while (true){
//                try {
//                    retryTaskService.retry();
//                }catch (Exception e){
//                    logger.error("删除任务调度失败{}",e);
//                }
//            }
//        });
//        thread.setDaemon(true);
//        thread.setName("RetryTaskWorker");
//        thread.start();
//    }
//}
//
