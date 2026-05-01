//package com.executor.xxljobexecutormqimprove.test;
//
//import com.executor.xxljobexecutormqimprove.core.schedulerhandler.ProducerHandler;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.context.ApplicationContext;
//
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicInteger;
//@SpringBootApplication
//public class RealQpsStressTest {
//    public static void main(String[] args) throws Exception {
//        // 这里用真实的Spring Bean获取ProducerHandler
//        ApplicationContext ctx = SpringApplication.run(RealQpsStressTest.class, args);
//        ProducerHandler producerHandler = ctx.getBean(ProducerHandler.class);
//
//        int threadNum = 50; // 并发线程数
//        int total = 10000; // 总请求数
//        AtomicInteger counter = new AtomicInteger(0);
//
//        ExecutorService pool = Executors.newFixedThreadPool(threadNum);
//
//        long start = System.currentTimeMillis();
//
//        for (int i = 0; i < total; i++) {
//            pool.submit(() -> {
//                producerHandler.producerMessage();
//                counter.incrementAndGet();
//            });
//        }
//
//        pool.shutdown();
//        pool.awaitTermination(30, TimeUnit.MINUTES);
//
//        long end = System.currentTimeMillis();
//        double qps = total * 1000.0 / (end - start);
//        System.out.println("真实联调QPS: " + qps);
//    }
//}
