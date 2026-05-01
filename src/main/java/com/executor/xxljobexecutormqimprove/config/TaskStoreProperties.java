package com.executor.xxljobexecutormqimprove.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xxl.job.store")
public class TaskStoreProperties {

    /** DB store implementation type: mybatis (default), jpa, jdbc */
    private String type = "mybatis";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
