package com.executor.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Executor 示例应用主类。
 * <p>
 * 启动后自动注册为 XXL-Job 执行器，并通过 executor-sdk 提供
 * 任务注册能力（RocketMQ Producer 自动初始化）。
 * </p>
 */
@SpringBootApplication
public class ExecutorExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutorExampleApplication.class, args);
    }
}
