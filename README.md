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

## 核心概念

### bizName 与 bizGroup

`bizName`（业务线）和 `bizGroup`（业务分组）是任务调度路由的两个关键标识，**SDK 注册的值必须与 XXL-Job Admin 任务参数完全一致**：

```
SDK:  .biz("order", "daily_report")
  →  DB: biz_name='order', biz_group='daily_report'
  →  XXL-Job Admin 任务参数: order,daily_report
  →  ProducerHandler: WHERE biz_name='order' AND biz_group='daily_report'
```

- `bizName` — 业务线名称（如 order、user、payment），建议按业务域划分
- `bizGroup` — 业务线内的分组（如 daily_report、cleanup、export），按功能细分
- **两者必须**: SDK 注册时传入 + XXL-Job Admin 配置对应的调度任务，缺一任务永远不会执行

> 可以为不同 bizName/bizGroup 组合在 XXL-Job Admin 中创建多个调度任务，实现分组隔离、独立 Cron、独立分片。

## 业务方接入

```java
@Autowired private ExecutorSdkClient sdkClient;

// bizName 和 bizGroup 必须与 XXL-Job Admin 中的任务参数一致
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
