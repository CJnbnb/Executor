package com.executor.stress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.executor.stress",
        "com.executor.xxljobexecutormqimprove"
})
public class StressTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(StressTestApplication.class, args);
    }
}
