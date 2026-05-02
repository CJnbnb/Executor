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
  ├── lockAndSelectTasks (短事务) → 捡出 process='pending' 的到期任务
  ├── lockTaskById → process='processing', locked_at=NOW
  ├── 虚拟线程池并行发送 RocketMQ
  ├── MQ 成功 → changeTaskInfo → next_trigger_time 更新 + process='pending'
  ├── MQ 失败 → Spring Retry → @Recover → retry_task 补偿表
  └── unlockTasks → process='pending', locked_at=NULL
```
- XXL-Job 按配置的 Cron 周期触发 `ProducerHandler`
- 支持分片：`MOD(id, shardTotal) = shardIndex` 实现多节点负载均衡
- 短事务减少 DB 锁持有时间
- Java 21 虚拟线程池并行处理，提高吞吐量
- 所有任务完成后回到 `pending`，为下一轮调度做准备

#### 阶段 2b：高频实时调度（时间轮）
```
SchedulerRealtimeService
  ├── scheduleThread: 每 1s 预读 next_trigger_time 在 5s 内的任务
  ├── pushTimeRing: 按秒刻度入环
  └── ringThread: 每秒触发对应刻度的任务 → jobTriggerPoolHelper
```
- 适用于秒级精度的高频任务（real_time_task 表）
- 时间轮内存结构，避免频繁查 DB
- 支持 misfire 检测：超过 5s 未触发则跳过并刷新 next_trigger_time

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

### 4.4 任务状态机

> **调度前提**：`ProducerHandler` 通过 XXL-Job 任务参数 `bizName,bizGroup` 决定扫描范围。
> SDK 注册的 `bizName`/`bizGroup` 必须与 XXL-Job Admin 中配置的**任务参数完全一致**（字符级匹配），否则任务会永远停留在 `pending` 状态不被执行。
> 详见 [配置参考 §4](configuration.md#4-xxl-job-admin-调度配置)。

所有任务只有三个稳定状态：`pending` / `processing` / `exception`。任何路径最终回到 `pending`，形成闭环。

```
               TaskRegistrationService    ProducerHandler              ScheduledUnlock
               ────────────────         ──────────────────           ───────────────
upsertTask:                             lockAndSelectTasks:
  process = pending ←──────────┐         process='pending' ──→  lockTaskById
  locked_at = NULL             │         next_time < now          process=processing
                               │         enable='1'               locked_at=NOW
                               │              │
                               │       ┌──────┴──────┐
                               │       │ MQ 成功     │ MQ 失败
                               │       ▼             ▼
                               │ changeTaskInfo   @Recover
                               │ process=pending  → retry_task
                               │ next_time=T2
                               │       │             │
                               │       └──────┬──────┘
                               │              ▼
                               │       unlockTasks:
                               │       process=pending
                               │       locked_at=NULL
                               │              │
                               └──────────────┘
                                    (下次 cron upsert 或超时补偿)

超时补偿路径:
  processing ──(1min)→ selectTimeoutProcessingTasks
                     → unlockExceptionTasks
                       process = pending (AND process='processing' 防竞态)
                       locked_at = NULL
```

**关键设计原则：**
- `locked_at` 在所有 unlock/upsert 路径上都置 NULL，不留残留锁
- 超时阈值 1 分钟（`selectTimeoutProcessingTaskIDs`），快速补偿
- `unlockExceptionTasks` 加 `AND process = 'processing'` 防止覆盖已完成任务
- `upsetTskInfo` 的 `ON DUPLICATE KEY UPDATE` 包含 `process` 和 `locked_at`，确保新消息可重置状态

### 4.5 消息发送补偿

MQ 发送失败后走 **两层补偿**：

```
MessageProducer.send()
  ├── @Retryable (maxAttempts=2, backoff=2s→4s)     ← Spring Retry 即时重试
  └── 全部失败 → @Recover → retryTaskService         ← 写入 retry_task 表

RetryTaskScheduler (每 1ms 轮询)
  ├── retry_count 0-3: +10s/次
  ├── retry_count 4-5: +10min/次
  ├── retry_count 6-9: +1h/次
  └── retry_count ≥10: 不再重试 (WHERE retry_count < 10)
```
- retry_task 表保留任务序列化参数，重试时直接重新发送 MQ 消息
- 发送成功则从 retry_task 删除；失败则递增 retry_count 并更新 next_trigger_time
- 主任务表在 unlockTasks 时已回到 `pending`，超时补偿（1min）也会兜底

### 4.6 对账日志

`task_event_log` 表记录所有状态变更，支持事后对账：

| event_type | from → to | 触发点 |
|---|---|---|
| `SCHEDULED` | → pending | 任务注册 / cron 下一轮 |
| `LOCKED` | pending → processing | 调度锁住 |
| `UNLOCKED` | processing → pending | 释放回池 |
| `TIMEOUT_RESET` | processing → pending | 超时补偿 |

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
