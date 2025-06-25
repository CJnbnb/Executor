package com.executor.xxljobexecutormqimprove.config;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class EventBusConfig {

    private static ThreadPoolExecutor executorService = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 2, 0,
            TimeUnit.SECONDS, new LinkedBlockingDeque<>(),
            r -> new Thread(r, "EngineEventBus-" + Thread.currentThread().getName()));


    @Bean
    public AsyncEventBus eventBus() {
        return new AsyncEventBus("EngineEventBus",executorService); // 可以自定义线程池等参数
    }

}
