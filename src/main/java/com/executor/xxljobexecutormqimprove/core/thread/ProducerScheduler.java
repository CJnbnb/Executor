//package com.executor.xxljobexecutormqimprove.core.thread;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
//public class ProducerScheduler {
//    private static Logger logger = LoggerFactory.getLogger(ProducerScheduler.class);
//
//    private static ProducerScheduler instance = new ProducerScheduler();
//    public static ProducerScheduler getInstance() {return instance;}
//
//    private Thread scheduleThread;
//    private Thread rightThread;
//
//    //这个是时间轮 用来优化扫表效率的
//    private volatile static Map<Integer, List<Integer>> ringData = new ConcurrentHashMap<>();
//
//    public void start(){
//        scheduleThread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while (true){
//
//                }
//            }
//        })
//    }
//}
