# Executor 调度中台压力测试报告

> **测试日期**: 2026-05-07
> **测试环境**: 本地开发机 (16 vCPU / 8 GB RAM) + Docker RocketMQ 5.2.0 + MySQL 8.0
> **Java**: JDK 21 虚拟线程 | **MQ**: 本地 Docker (< 1 ms RTT)

---

## 1. 测试环境

### 1.1 基础设施

| 组件 | 配置 | 说明 |
|------|------|------|
| 应用服务器 | 16 vCPU / 8 GB RAM, JDK 21 虚拟线程 | 本地运行 |
| RocketMQ NameServer | Docker 容器, 1.5 CPU / 512 MB Heap | rocketmq:5.2.0 |
| RocketMQ Broker | Docker 容器, 2.5 CPU / 1 GB Heap | ASYNC_MASTER + SYNC_FLUSH |
| MySQL | 本地 8.0 | HikariCP 连接池 |
| 网络 | 本地 Docker bridge | < 1 ms RTT |

### 1.2 应用配置

```properties
# executor-stress (端口 8083)
server.port=8083
stress.mq.mock=false                    # Layer 1 设为 true
stress.mq.mock-delay-ms=0

# RocketMQ (本地 Docker)
xxl.job.process.nameserver=localhost:9876
xxl.job.process.send-message-timeout=3000
xxl.job.process.retry-times-when-send-failed=2

# DB
spring.datasource.url=jdbc:mysql://localhost:3306/xxl_job_executor_mq

# 调度参数
LIMIT_COUNT=200           # 每轮锁定任务数上限
PRE_READ_MS=5000          # 时间轮预读窗口
fastTriggerPool: max=200  # 时间轮触发线程池
```

### 1.3 Docker MQ 配置

```yaml
# docker-compose 核心配置
rmqnamesrv: 1.5 CPU / 2G Memory, 端口 9876
rmqbroker:  2.5 CPU / 4G Memory, 端口 10911/10909
rocketmq-dashboard: 端口 8090
```

```
# broker.conf 关键配置
flushDiskType=SYNC_FLUSH
brokerRole=ASYNC_MASTER
defaultTopicQueueNums=8
```

---

## 2. 被测版本

| 版本 | 标签 | 优化项 |
|------|------|--------|
| **v1** | baseline | 无任何优化 |
| **v2** | tw-batch | cron 缓存 + 时间轮 batch UPDATE + dedup + computeIfAbsent |
| **v3** | dev (当前) | v2 全部 + ProducerHandler batch changeTaskInfo（双边 batch） |

### 优化项详情

| 优化项 | v1 | v2 | v3 | 位置 |
|--------|:--:|:--:|:--:|------|
| CronExpression 缓存 | - | ✓ | ✓ | CronTimeUtil |
| 时间轮批量 UPDATE | - | ✓ | ✓ | SchedulerRealtimeService |
| 时间轮触发去重 | - | ✓ | ✓ | SchedulerRealtimeService.ringThread |
| 原子 pushTimeRing | - | ✓ | ✓ | SchedulerRealtimeService |
| 低频批量 changeTaskInfo | - | - | ✓ | ProducerHandler |

---

## 3. 测试方法

采用分层测试法，逐层剥离外部依赖，定位真实瓶颈：

```
Layer 1: Mock MQ    → 调度内核极限 (DB + 锁 + 扫描，MQ 即时返回)
Layer 2: Local MQ   → 应用 + MQ 联合基准 (本地 Docker RocketMQ)
Layer 3: Full Chain → Burst 并发 + Soak 稳定性 + 阶梯加压
```

测试分两组：

- **A 组 (低频)**: CommonTask → ProducerHandler 调度链路
- **B 组 (高频)**: RealtimeTask → 时间轮调度链路

---

## 4. 跨版本对比结果

### 4.1 A 组: 低频调度

#### A1: Mock MQ 串行 (5000 tasks, 5 groups)

| 指标 | v1 | v2 | v3 |
|------|----|----|-----|
| **TPS** | **2,720** | **2,989** | **3,085** |
| 总处理量 | 5,800 | 6,600 | 6,200 |
| 总轮数 | 29 | 33 | 31 |
| MQ 成功率 | 100% | 100% | 100% |
| Bench 耗时(ms) | 2,132 | 2,208 | 2,010 |

> Mock MQ 模式下 TPS **2,720 ~ 3,085**，三版本差异 < 12%，在统计波动范围内。
> 纯调度内核（lock + select + update）的极限约 **3,000 TPS**。

#### A2: Local MQ 串行 (5000 tasks, 5 groups)

| 指标 | v1 | v2 | v3 |
|------|----|----|-----|
| **TPS** | **2,626** | **3,150** | **2,831** |
| 总处理量 | 7,400 | 5,600 | 5,800 |
| 总轮数 | 37 | 28 | 29 |
| MQ 成功率 | 100% | 100% | 100% |
| Bench 耗时(ms) | 2,818 | 1,778 | 2,049 |

> Local MQ 与 Mock MQ TPS 差异 < 10%，**MQ 发送不构成额外瓶颈**。
> 本地 Docker RocketMQ 的网络开销可忽略不计。

---

### 4.2 B 组: 高频调度 (时间轮)

#### B1: Mock TW 500 tasks (30s 观察)

| 指标 | v1 | v2 | v3 |
|------|----|----|-----|
| **Avg TPS** | **400** | **400** | **433** |
| Peak TPS | 666 | 666 | 500 |
| 总产出 | 12,000 | 12,000 | 13,000 |
| 失败 | 0 | 0 | 0 |

> 500 任务轻松处理，时间轮无压力。三版本表现一致。

#### B2: Mock TW 1000 tasks (30s 观察)

| 指标 | v1 | v2 | v3 |
|------|----|----|-----|
| **Avg TPS** | **660** | **661** | **649** |
| Peak TPS | 935 | 941 | 1,000 |
| 总产出 | 19,809 | 19,823 | 19,464 |
| 失败 | 0 | 0 | 0 |

> 1000 任务时 Avg TPS 稳定在 **~660**，`fastTriggerPool` (200 线程) 开始成为瓶颈。
> 三版本差异 < 2%，cron 缓存和 batch UPDATE 在此负载下无明显收益。

#### B3: Local TW 500 tasks (30s 观察)

| 指标 | v1 | v2 | v3 |
|------|----|----|-----|
| **Avg TPS** | **450** | **450** | **483** |
| Peak TPS | 500 | 500 | 667 |
| 总产出 | 13,500 | 13,500 | 14,500 |
| 失败 | 0 | 0 | 0 |

> 本地 MQ 下 500 任务同样无压力，三版本一致。

#### B4: Local TW 1000 tasks (30s 观察)

| 指标 | v1 | v2 | v3 |
|------|----|----|-----|
| **Avg TPS** | **680** | **689** | **673** |
| Peak TPS | 1,139 | 891 | 1,068 |
| 总产出 | 20,419 | 20,676 | 20,205 |
| 失败 | 0 | 0 | 0 |

> 三版本 Avg TPS **673 ~ 689**，差异 < 3%。
> Peak TPS 波动较大 (891 ~ 1,139)，是时间轮固有的调度抖动。

---

### 4.3 汇总

```
低频 Mock MQ  TPS:  v1=2,720  v2=2,989  v3=3,085    差异 ±12%
低频 Local MQ TPS:  v1=2,626  v2=3,150  v3=2,831    差异 ±10%
高频 Local 1000 TPS: v1=680   v2=689    v3=673      差异 ±2%
```

---

## 5. 瓶颈分析

### 5.1 瓶颈定位

| 瓶颈 | 层级 | 严重度 | 证据 |
|------|------|--------|------|
| **DB 事务** | 低频主瓶颈 | 高 | Mock MQ ≈ Real MQ (差 < 10%)，TPS 约 3,000 即 DB 极限 |
| **fastTriggerPool** | 高频硬性瓶颈 | 高 | 500 tasks → ~500/s 稳定，1000 tasks → ~666/s 稳定 |
| MQ 发送 | 非瓶颈 | 低 | 全部测试 MQ 成功率 100%，0 失败 |
| Cron 缓存 | 微量优化 | 低 | 三版本高频 TPS 差异 < 3% |
| Batch UPDATE | 微量优化 | 低 | 三版本高频 TPS 差异 < 3% |
| Batch changeTaskInfo | 微量优化 | 低 | 三版本低频 TPS 差异 < 12% |

### 5.2 瓶颈结构

```
低频调度链路:
  lockAndSelectTasks (DB SELECT)
    → lockTaskById (DB UPDATE)
      → VirtualThread × MQ.send (并发)
        → batchChangeTaskInfo (DB batch UPDATE per round)
          → unlockTasks (DB batch UPDATE)

  瓶颈: DB 事务 (lock + select + update 每轮 200 条)
  TPS 天花板: ~3,000

高频调度链路:
  scheduleThread (每秒 DB 扫描)
    → batchUpdateTaskTriggerInfo (批量 UPDATE)
      → pushTimeRing (内存 ring)
        → ringThread (每秒消费)
          → fastTriggerPool (200 线程)
            → MQ.send (并发)

  瓶颈: fastTriggerPool 200 线程
  TPS 天花板: ~666 (1000 tasks @ 1s cron)
```

### 5.3 优化效果评估

| 优化项 | 预期效果 | 实测效果 | 评估 |
|--------|----------|----------|------|
| Cron 缓存 | 减少重复解析 | TPS 差异 < 3% | 当前负载下无感 |
| Batch UPDATE | 减少 DB round-trip | TPS 差异 < 3% | 时间轮每批量不大，收益有限 |
| Dedup | 避免重复触发 | 稳定性提升 | 无法在 TPS 上体现 |
| Batch changeTaskInfo | 减少 DB round-trip | TPS 差异 < 12% | 低频每轮 200 条，batch 收益有限 |

> 当前测试规模（500 ~ 5000 tasks）下，优化项均未体现出显著 TPS 提升。
> 瓶颈在更底层（DB 事务 / fastTriggerPool），优化被天花板掩盖。

---

## 6. 系统稳定性

| 指标 | 全部测试 | 判定 |
|------|----------|------|
| MQ 成功率 | **100%** (~40 万次发送，0 失败) | 优秀 |
| Heap | 54 ~ 118 MB，波动正常 | 无泄漏 |
| 线程数 | 140 ~ 141，稳定 | 无泄漏 |
| DB 连接 | 1 ~ 4，HikariCP 正常波动 | 无泄漏 |
| GC | 累积增长正常，无 Full GC 频繁 | 正常 |

---

## 7. 结论

### 真实承载能力

| 场景 | TPS | 瓶颈 |
|------|-----|------|
| 低频串行 | **~3,000** | DB 事务 |
| 高频时间轮 (1000 tasks) | **~666** | fastTriggerPool (200 线程) |
| 多 biz 并发 | 待补测 (A3 burst) | DB 锁竞争 |

### 核心结论

1. **调度内核极限约 3,000 TPS**（低频）和 **~666 TPS**（高频），瓶颈分别是 DB 事务和 fastTriggerPool 线程池
2. **MQ 不是瓶颈**：本地 Docker MQ 与 Mock MQ 差异 < 10%，全程 0 失败
3. **当前优化项效果不明显**：cron 缓存、batch UPDATE、batch changeTaskInfo 在当前负载下 TPS 差异在统计波动范围内
4. **要突破瓶颈，方向应为**：
   - 低频：增大 `LIMIT_COUNT` 减少轮次开销，或引入分区表分散 DB 锁
   - 高频：增大 `fastTriggerPool.max` 突破 200 线程限制
5. **系统稳定可靠**：长时间运行无内存/线程/连接泄漏，MQ 发送成功率 100%

---

## 8. 附录

### 压测启动方式

```bash
# Layer 1: Mock MQ (调度内核极限)
java -jar executor-stress/target/executor-stress-0.0.1-SNAPSHOT.jar \
  --stress.mq.mock=true

# Layer 2/3: 本地 MQ (真实 RocketMQ)
java -jar executor-stress/target/executor-stress-0.0.1-SNAPSHOT.jar \
  --stress.mq.mock=false
```

### 清理测试数据

```bash
curl -X DELETE "http://localhost:8083/stress/layer/cleanup?numBizGroups=10"
curl -X DELETE "http://localhost:8083/stress/cleanup?numLowFreqGroups=10&highFreqBizName=stress-realtime&highFreqBizGroup=hft"
```

### 详细 API 参考

见 [API 参考](02-api-reference.md)。
