package com.executor.xxljobexecutormqimprove.config;

import com.executor.xxljobexecutormqimprove.core.store.TaskStore;
import com.executor.xxljobexecutormqimprove.core.store.mybatis.MyBatisTaskStore;
import com.executor.xxljobexecutormqimprove.dashboard.store.DashboardStore;
import com.executor.xxljobexecutormqimprove.dashboard.store.mybatis.MyBatisDashboardStore;
import com.executor.xxljobexecutormqimprove.dashboard.mapper.DashboardMapper;
import com.executor.xxljobexecutormqimprove.mapper.CommonTaskMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TaskStoreProperties.class)
@ConditionalOnProperty(prefix = "xxl.job.store", name = "type", havingValue = "mybatis", matchIfMissing = true)
public class TaskStoreAutoConfiguration {

    @Bean
    @ConditionalOnBean(CommonTaskMapper.class)
    public TaskStore taskStore(CommonTaskMapper mapper) {
        return new MyBatisTaskStore(mapper);
    }

    @Bean
    @ConditionalOnBean(DashboardMapper.class)
    public DashboardStore dashboardStore(DashboardMapper mapper) {
        return new MyBatisDashboardStore(mapper);
    }
}
