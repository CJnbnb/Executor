# Executor 分层压力测试方案

## 目标

精确测量 xxl-executor 调度系统在**不同层级**的真实承载能力，剥离外部干扰，找到真正的性能瓶颈和极限拐点。

## 为什么需要分层压测

上一版压测报告中，TPS 在 185-302 之间波动，但这个数值**被公网 MQ 的网络延迟绑架了**——我们不知道真正的瓶颈是调度器本身还是 MQ 网络。分层压测的核心思想：

> 不要用公网网络去掩盖系统真实能力；分层测试 + 阶梯加压 + 稳定性验证。

## 三层架构

```
Layer 1: 调度内核（Mock MQ）
  ┌──────────────────────────────────────────┐
  │  时间轮 + DB 扫描 + 虚拟线程调度           │
  │  MQ 发送 → Mock（即时返回）                │
  │  测量: 调度器本身的极限 TPS                │
  └──────────────────────────────────────────┘
           │ 预期 TPS: 3000-10000+
           ▼
Layer 2: 应用 + 本地 MQ（同机 Docker）
  ┌──────────────────────────────────────────┐
  │  调度引擎 + 真实 RocketMQ 发送             │
  │  NameServer + Broker 同机部署              │
  │  测量: 内网可复现的基准 TPS                │
  └──────────────────────────────────────────┘
           │ 预期 TPS: 3000-10000
           ▼
Layer 3: 全链路 Burst + Soak
  ┌──────────────────────────────────────────┐
  │  完整调度 → MQ → 消费链路                  │
  │  a) Burst: 极限注入，找拐点                │
  │  b) Soak:  长时间稳定性验证                │
  └──────────────────────────────────────────┘
```

## Layer 1: Mock MQ — 调度内核极限

### 目的

去掉 MQ 网络发送的全部开销，只测调度器本身（时间轮 + DB 锁 + 扫描）的吞吐能力。

### 方法

1. 在 `application.properties` 设置 `stress.mq.mock=true`
2. 应用启动后，`MockMQMessagePublisher` 替代 `RocketMQMessagePublisher`，所有 `send()` 调用直接返回 `true`（0ms 延迟）
3. DB、事务、虚拟线程全部真实运行
4. 通过 `/stress/layer/mock-run` 端点执行

### 测试矩阵

| 参数 | 值 | 说明 |
|------|-----|------|
| numTasks | 5000 | 任务总数 |
| numBizGroups | 5 | 分片数 |
| cronExpr | 0/1 * * * * ? | 每秒触发 |
| LIMIT_COUNT | 200 | 每轮锁定数 |
| mock.mq.mock-delay-ms | 0 | 无延迟 |

### 预期

- TPS 远超 300，可能达到 **3000-10000+**
- 瓶颈暴露：DB 连接池、扫描 SQL、时间轮 ring 操作
- 如果 TPS 依然很低 → 代码级锁或 I/O 瓶颈

### 测试步骤

```bash
# 1. 确认 Mock 模式
grep "stress.mq.mock" application.properties
# stress.mq.mock=true

# 2. 启动服务
java -jar executor-stress/target/executor-stress-0.0.1-SNAPSHOT.jar

# 3. 日志确认 Mock 模式
# 应看到: ==== STRESS-TEST MODE: Mock MQ (delay=0ms) ====

# 4. 执行 Mock 压测
curl -X POST http://localhost:8083/stress/layer/mock-run \
  -H "Content-Type: application/json" \
  -d '{
    "numTasks": 5000,
    "numBizGroups": 5,
    "cronExpr": "0/1 * * * * ?",
    "maxRounds": 200
  }' | python3 -m json.tool

# 5. 查看系统状态
curl http://localhost:8083/stress/layer/status | python3 -m json.tool

# 6. 清理
curl -X DELETE "http://localhost:8083/stress/layer/cleanup?numBizGroups=5"
```

### 结果记录模板

| 指标 | 值 |
|------|-----|
| 总任务数 | |
| 总处理量 | |
| 总耗时(ms) | |
| **TPS** | |
| MQ 成功率 | (Mock 模式始终 100%) |
| DB 连接活跃数 | |
| Heap 使用(MB) | |
| GC 次数 | |

---

## Layer 2: 本地 MQ — 应用 + MQ 联合基准

### 目的

在同一台机器上部署 RocketMQ Docker，去除公网 RTT 干扰，得出**内网可复现的基准 TPS**。

### 前置条件

```bash
# 启动本地 Docker RocketMQ (4C6G 资源限制)
cd F:/xxl-executor/docker-stress-mq
docker-compose up -d

# 验证
docker ps --filter "name=rmq"
# 应看到: rmqnamesrv, rmqbroker, rocketmq-dashboard

# Dashboard 访问: http://localhost:8090
```

### 方法

1. 确认 `stress.mq.mock=false`（使用真实 RocketMQ）
2. `xxl.job.process.nameserver=localhost:9876`（指向本地 Docker）
3. Broker 配置异步刷盘 (`flushDiskType=ASYNC_FLUSH`)
4. 通过 `/stress/layer/local-mq-run` 执行

### 测试矩阵

| 参数 | 值 | 说明 |
|------|-----|------|
| numTasks | 2000 | 单次测试任务数 |
| numBizGroups | 3 | 分片数 |
| cronExpr | 0/1 * * * * ? | 每秒触发 |
| LIMIT_COUNT | 200 | 每轮锁定数 |

### 预期

- 单机 4C6G Broker + 异步刷盘，TPS 通常在 **3000-10000** 之间
- 比公网 MQ 高 3-10 倍

### 测试步骤

```bash
# 1. 确认配置指向本地 MQ
grep "nameserver" application.properties
# xxl.job.process.nameserver=localhost:9876
grep "stress.mq.mock" application.properties
# stress.mq.mock=false

# 2. 启动服务
java -jar executor-stress/target/executor-stress-0.0.1-SNAPSHOT.jar

# 3. 执行本地 MQ 压测
curl -X POST http://localhost:8083/stress/layer/local-mq-run \
  -H "Content-Type: application/json" \
  -d '{
    "numTasks": 2000,
    "numBizGroups": 3,
    "cronExpr": "0/1 * * * * ?",
    "maxRounds": 100
  }' | python3 -m json.tool

# 4. 查看 RocketMQ Dashboard
# 浏览器打开 http://localhost:8090 → Producer 统计

# 5. 清理
curl -X DELETE "http://localhost:8083/stress/layer/cleanup?numBizGroups=3"
```

### 使用 JMeter 阶梯加压

```bash
# JMeter 命令行模式
jmeter -n -t executor-stress/jmeter/local-mq-stair-step.jmx \
  -l reports/local-mq-result.jtl \
  -e -o reports/local-mq-html \
  -JSTART_TASKS=100 -JSTEP_SIZE=100 -JMAX_TASKS=5000
```

### 结果记录模板

| 指标 | 测试 1 (2000 tasks) | 测试 2 (5000 tasks) | 测试 3 (10000 tasks) |
|------|---------------------|---------------------|----------------------|
| 总处理量 | | | |
| 总耗时(ms) | | | |
| **TPS** | | | |
| MQ 成功数 | | | |
| MQ 失败数 | | | |
| 失败率 | | | |
| DB 活跃连接 | | | |
| Heap (MB) | | | |
| MQ 发送 P99 延迟 | | | |

---

## Layer 3a: Burst 极限注入

### 目的

一次性注入大量任务，观察系统在极端压力下的行为。

### 测试步骤

```bash
# 50000 个任务，10 个分片并发
curl -X POST http://localhost:8083/stress/layer/burst \
  -H "Content-Type: application/json" \
  -d '{
    "numTasks": 50000,
    "numBizGroups": 10,
    "maxRounds": 500
  }' | python3 -m json.tool
```

### 观察项

| 指标 | 正常范围 | 告警阈值 |
|------|---------|----------|
| MQ 失败率 | <1% | >5% |
| DB 连接池 | <20 | 连接池耗尽 |
| 任务延迟率 | <1% | >5% misfire |
| Heap 使用 | <80% | Full GC 频繁 |

---

## Layer 3b: Soak 稳定性测试

### 目的

中等负载持续运行 30-60 分钟，检测内存泄漏、连接池泄漏、线程增长。

### 测试步骤

```bash
# 3000 任务，运行 30 分钟，每 30 秒报告一次
curl -X POST http://localhost:8083/stress/layer/soak \
  -H "Content-Type: application/json" \
  -d '{
    "numTasks": 3000,
    "numBizGroups": 3,
    "durationMinutes": 30,
    "reportIntervalSeconds": 30
  }' | python3 -m json.tool

# 如需中途停止
curl -X POST http://localhost:8083/stress/layer/soak/stop
```

### 观察项

| 时间 | Heap | Threads | DB Conn | TPS | FailRate | 判定 |
|------|------|---------|---------|-----|----------|------|
| 0 min | | | | | | 基准 |
| 10 min | | | | | | 稳定期 |
| 20 min | | | | | | 关键观察点 |
| 30 min | | | | | | 结束判定 |

---

## Layer 3c: 阶梯加压 (Stair-Step)

### 目的

从低负载开始，逐步增加任务数，精确找到系统的**极限拐点**（knee point）。

### 方法

- 从 100 tasks 开始
- 每步增加 100 tasks
- 每步间隔 10 秒
- 直到错误率 > 1% 或达到 maxTasks

### 测试步骤

```bash
# 阶梯加压
curl -X POST http://localhost:8083/stress/layer/stair-step \
  -H "Content-Type: application/json" \
  -d '{
    "startTasks": 100,
    "stepSize": 100,
    "stepIntervalSeconds": 10,
    "maxTasks": 5000,
    "numBizGroups": 3,
    "maxRoundsPerStep": 20,
    "failThreshold": 0.01
  }' | python3 -m json.tool

# 如需中途停止
curl -X POST http://localhost:8083/stress/layer/stair-step/stop
```

### 结果分析

拐点判定标准：当 TPS 增长率低于前一步 70% 时，即为 kne Point。

---

## 快速参考：三层对比

| 层级 | 端点 | MQ 模式 | 预期 TPS | 测试目标 |
|------|------|---------|----------|----------|
| Layer 1 | `/stress/layer/mock-run` | Mock (0ms) | 3000-10000+ | 调度器极限 |
| Layer 2 | `/stress/layer/local-mq-run` | 本地 Docker | 3000-10000 | 应用+MQ 基准 |
| Layer 3a | `/stress/layer/burst` | 真实 MQ | 取决于环境 | 极限爆发 |
| Layer 3b | `/stress/layer/soak` | 真实 MQ | 稳定吞吐 | 长时间稳定性 |
| Layer 3c | `/stress/layer/stair-step` | 真实 MQ | TPS 曲线 | 拐点定位 |

## 完整测试执行顺序

```
预热 (2 min)
  → Layer 1: Mock MQ (5 min)
    → Layer 2: 本地 MQ 阶梯 (15 min)
      → Layer 3a: Burst 极限 (5 min)
        → Layer 3b: Soak 30-60 min
          → 分析 & 报告
```

## 环境配置对照

### Mock 模式 (Layer 1)

```properties
stress.mq.mock=true
stress.mq.mock-delay-ms=0
```

### 本地 MQ 模式 (Layer 2 & 3)

```properties
stress.mq.mock=false
xxl.job.process.nameserver=localhost:9876
```

### Docker MQ 启动

```bash
cd F:/xxl-executor/docker-stress-mq
docker-compose up -d
docker-compose logs -f rmqbroker  # 观察 Broker 日志
```
