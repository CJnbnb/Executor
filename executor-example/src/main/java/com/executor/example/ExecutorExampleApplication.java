package com.executor.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Executor SDK 示例应用。
 * <p>
 * 仅引入 executor-sdk，演示业务方如何通过 SDK 注册定时任务到 Executor 调度引擎。
 * 无需依赖 xxl-job，业务代码只和 SDK 交互。
 * </p>
 */
@SpringBootApplication
public class ExecutorExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutorExampleApplication.class, args);
    }
}
