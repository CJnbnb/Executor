# 架构设计文档

## 1. 项目定位

Executor 是一个**基于 RocketMQ 的任务调度执行引擎**，对 XXL-Job 进行增强改造。核心设计理念：

- **外部业务团队**通过轻量级 SDK 注册定时任务，无需关心调度逻辑
- **中台团队**通过 Dashboard 全局监控和运维所有任务
- **调度引擎**复用 XXL-Job 的调度能力，将任务通过 RocketMQ 解耦分发

## 2. 模块划分

```
executor-parent (pom.xml)
├── executor-core       — 调度引擎核心（Spring Boot 应用）
└── executor-example    — SDK 使用示例（独立应用）

executor-sdk (独立项目)  — 客户端 SDK，供业务方引入
```

| 模块 | 角色 | 谁用 |
|------|------|------|
| `executor-sdk` | 任务注册客户端 | 外部业务团队 |
| `executor-core` | 调度 + 消费 + Dashboard | 中台团队部署 |
| `executor-example` | SDK 使用演示 | 开发者参考 |

## 3. 核心流程

```
┌─────────────────────────────────────────────────────────────┐
│  业务应用（外部团队）                                          │
│  ┌───────────────────────┐                                   │
│  │    executor-sdk       │                                   │
│  │  TaskBuilder          │                                   │
│  │    .biz("order","d")  │                                   │
│  │    .cron("0 * * * *") │                                   │
│  │    .payload("{}")     │                                   │
│  │    .schedule()        │                                   │
│  └───────┬───────────────┘                                   │
└──────────┼──────────────────────────────────────────────────┘
           │ RocketMQ (executorConsumeTask)
           ▼
┌─────────────────────────────────────────────────────────────┐
│  executor-core（中台部署）                                     │
│                                                              │
│  ┌─────────────────┐     ┌──────────────────┐               │
│  │ RocketMQSubscriber│────▶│    Processor     │               │
│  │ (消费注册消息)    │     │ upsert → DB       │               │
│  └─────────────────┘     └──────────────────┘               │
│                                                              │
│  ┌─────────────────┐     ┌──────────────────┐               │
│  │  XXL-Job 调度    │────▶│ ProducerHandler  │               │
│  │  定时触发        │     │ SELECT FOR UPDATE│               │
│  └─────────────────┘     │ lock → send MQ   │               │
│                          │ unlock(done)     │               │
│                          └──────┬───────────┘               │
│                                 │ RocketMQ (per-task topic)  │
│                                 ▼                            │
│                         ┌──────────────────┐               │
│                         │  业务消费者       │               │
│                         │  (实际执行逻辑)   │               │
│                         └──────────────────┘               │
│                                                              │
│  ┌─────────────────────────────────────────┐               │
│  │         Dashboard (内置)                 │               │
│  │  /dashboard        ┌────────────────┐   │               │
│  │  统计/搜索/批量操作  │  MySQL (直接)  │   │               │
│  └─────────────────────────────────────────┘               │
└────────────────────────────────────────────────────────────┘
```

### 流程详解

#### 阶段 1：任务注册
```
业务方 SDK → RocketMQ(executorConsumeTask) → Processor.handle() → TaskStore.upsetTask() → DB
```
- SDK 构建 TaskRequest，通过 RocketMQ 发送
- executor-core 消费后写入 `user_scheduled_common_task` 表
- 支持 Cron 和一次性两种模式

#### 阶段 2：调度执行
```
XXL-Job 定时触发 → ProducerHandler.producerMessage()
  ├── SELECT FOR UPDATE 锁定到期任务（短事务）
  ├── lockTaskById → process='processing'
  ├── 虚拟线程池并行发送 RocketMQ
  ├── changeTaskInfo → 更新 next_trigger_time
  └── unlockTasks → process='done'
```
- XXL-Job 按配置的 Cron 周期触发 `ProducerHandler`
- 支持分片：`MOD(id, shardTotal) = shardIndex` 实现多节点负载均衡
- 短事务减少 DB 锁持有时间
- Java 21 虚拟线程池并行处理，提高吞吐量

#### 阶段 3：Dashboard 运维
```
中台运维 → 浏览器访问 :8081 → DashboardController → DashboardStore → DB
```
- Dashboard **直接读写 DB**（绕过 SDK 和 MQ），拥有全局视角
- 外部业务团队**无权访问** Dashboard，只能通过 SDK + xxl-admin 管理各自任务

## 4. 关键设计决策

### 4.1 存储抽象层
`TaskStore` / `DashboardStore` 接口 + MyBatis 实现。可在 `application.properties` 中切换：
```properties
xxl.job.store.type=mybatis   # 默认值，可扩展为 jpa/jdbc
```

### 4.2 消息队列抽象层
`MessagePublisher` / `MessageSubscriber` / `MessageHandler` 接口 + RocketMQ 实现：
```properties
xxl.job.mq.type=rocketmq     # 默认值，可扩展为 kafka/rabbitmq
```

### 4.3 分片支持
XXL-Job 原生分片参数传递到 `ProducerHandler`，`lockAndSelectTasksByShard` 通过 `MOD(id, shardCount)` 实现无锁并行扫描。

### 4.4 任务生命周期
```
SDK 注册 → enable=1, process=NULL
    ↓ (调度器扫描)
lock → process=processing
    ↓ (MQ 发送成功)
next_trigger_time 更新 (Cron 模式) 或 enable=0 (一次性模式)
    ↓
unlock → process=done
    ↓ (下次调度周期)
重新匹配 next_trigger_time 条件，进入下一轮
```

## 5. 项目依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.0 | 基础框架 |
| XXL-Job | 2.4.0 | 调度核心 |
| RocketMQ Spring Boot | 2.2.3 | 消息队列 |
| MyBatis Spring Boot | 3.0.3 | 数据访问 |
| MySQL Connector | 8.3.0 | 数据库驱动 |
| Quartz | 2.3.0 | Cron 表达式解析 |
| Fastjson | 1.2.83 | JSON 序列化 |
| Java | 21 | 虚拟线程支持 |
