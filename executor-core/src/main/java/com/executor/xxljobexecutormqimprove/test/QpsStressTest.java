//package com.executor.xxljobexecutormqimprove.test;
//
//import com.executor.xxljobexecutormqimprove.core.schedulerhandler.ProducerHandler;
//
//
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class QpsStressTest {
//
//    public static void main(String[] args) throws Exception {
//        ProducerHandler producerHandler = new ProducerHandler();
//        int threadNum = 100; // 并发线程数
//        int total = 1000000; // 总请求数
//        AtomicInteger counter = new AtomicInteger(0);
//
//        ExecutorService pool = Executors.newFixedThreadPool(threadNum);
//
//        long start = System.currentTimeMillis();
//
//        for (int i = 0; i < total; i++) {
//            pool.submit(() -> {
//                // 建议用Mock，避免外部依赖影响
//                producerHandler.producerMessage();
//                counter.incrementAndGet();
//            });
//        }
//
//        pool.shutdown();
//        pool.awaitTermination(10, TimeUnit.MINUTES);
//
//        long end = System.currentTimeMillis();
//        double qps = total * 1000.0 / (end - start);
//        System.out.println("极限QPS: " + qps);
//    }
//}
