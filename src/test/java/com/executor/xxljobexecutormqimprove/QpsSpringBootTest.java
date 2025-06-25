//package com.executor.xxljobexecutormqimprove;
//
//import com.executor.xxljobexecutormqimprove.core.schedulerhandler.ProducerHandler;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicInteger;
//
//@SpringBootTest
//public class QpsSpringBootTest {
//    @Autowired
//    private ProducerHandler producerHandler;
//
//    @Test
//    public void testQps() throws Exception {
//        int availableProcessors = Math.min(2, Runtime.getRuntime().availableProcessors());
//        ExecutorService pool = Executors.newFixedThreadPool(availableProcessors); // 限制线程数
//
//        int total = 100;
//        AtomicInteger counter = new AtomicInteger(0);
//
//        long start = System.currentTimeMillis();
//
//        for (int i = 0; i < total; i++) {
//            pool.submit(() -> {
////                XxlJobHelper.setJobParam("testBiz,testGroup");
//                producerHandler.producerMessage("testBiz","testGroup");
//                counter.incrementAndGet();
//            });
//        }
//
//        pool.shutdown();
//        pool.awaitTermination(30, TimeUnit.MINUTES);
//
//        long end = System.currentTimeMillis();
//        double qps = total * 1000.0 * 200/ (end - start);
//        System.out.println("真实联调QPS任务数: " + qps);
//    }
//}
