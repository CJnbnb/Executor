# Executor — 基于 RocketMQ 的任务调度执行引擎

对 XXL-Job 的增强改造，将任务注册与调度解耦。业务方通过轻量级 SDK 注册定时任务，调度引擎统一扫描并分发到 RocketMQ，消费者异步执行。内置 Dashboard 供中台团队全局运维。

## 快速开始

### 环境

- JDK 21+ / MySQL 8.0+ / RocketMQ 4.x+ / XXL-Job Admin 2.4.0

### 初始化数据库

```bash
mysql -u root -p < doc/schema.sql
```

### 构建

```bash
cd executor-sdk && mvn clean install -DskipTests
cd ../Executor && mvn clean package -DskipTests
```

### 配置 & 启动

```bash
cd Executor/executor-core
export DB_PASSWORD=your_password
export ROCKETMQ_SECRET_KEY=your_secret

# 编辑 application.properties 中的 nameserver / admin 地址后启动
mvn spring-boot:run
```

启动后访问 Dashboard：`http://localhost:8081/`

## 模块

| 模块 | 说明 |
|------|------|
| `executor-sdk` | 业务方引入的客户端 SDK，无 XXL-Job 依赖 |
| `executor-core` | 调度引擎 + Dashboard，Spring Boot 应用 |
| `executor-example` | SDK 使用示例 |

## 业务方接入

```java
@Autowired private ExecutorSdkClient sdkClient;

sdkClient.newTask()
    .biz("order", "daily_report")
    .taskName("每日订单汇总")
    .cron("0 0 8 * * ?")
    .payload("{\"type\":\"report\"}")
    .schedule();
```

## Dashboard

中台团队通过 Dashboard 全局监控和管理所有任务：统计、搜索、启用/禁用、释放卡住任务、批量操作。

![](无截图)

## 文档

- [架构设计](doc/architecture.md)
- [配置参考](doc/configuration.md)
- [SDK 使用指南](doc/sdk-usage.md)
- [Dashboard API](doc/dashboard-api.md)
- [部署指南](doc/deployment.md)
- [数据库 Schema](doc/schema.sql)

## License

Internal Use Only
