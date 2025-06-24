package com.executor.xxljobexecutormqimprove;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.executor.xxljobexecutormqimprove.mapper")
public class XxlJobExecutorMqImproveApplication {

    public static void main(String[] args) {
        SpringApplication.run(XxlJobExecutorMqImproveApplication.class, args);
    }

}
