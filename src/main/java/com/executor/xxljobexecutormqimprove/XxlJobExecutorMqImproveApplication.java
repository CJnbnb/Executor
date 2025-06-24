package com.executor.xxljobexecutormqimprove;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class XxlJobExecutorMqImproveApplication {

    public static void main(String[] args) {
        SpringApplication.run(XxlJobExecutorMqImproveApplication.class, args);
    }

}
