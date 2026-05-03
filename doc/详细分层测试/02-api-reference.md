# Executor 分层压力测试 — API 参考手册

## 服务信息

- **端口**: 8083
- **基础路径**: `/stress`
- **启动类**: `com.executor.stress.StressTestApplication`

## 模块结构

```
executor-stress/
├── pom.xml
├── README.md
├── src/main/java/com/executor/stress/
│   ├── StressTestApplication.java          # 启动类
│   ├── config/
│   │   └── StressTestConfig.java           # Mock/Real MQ 切换配置
│   ├── controller/
│   │   ├── StressTestController.java       # 原始压测端点
│   │   └── LayerTestController.java        # 分层压测端点 (NEW)
│   ├── metrics/
│   │   └── StressMetrics.java              # 扩展指标收集 (NEW)
│   └── mq/
│       └── MockMQMessagePublisher.java     # Mock MQ 发送器 (NEW)
├── src/main/resources/
│   ├── application.properties
│   └── application-example.properties
└── jmeter/
    ├── low-freq-stress.jmx
    ├── high-freq-stress.jmx
    ├── run-jmeter-test.bat
    └── run-stress-test.ps1
```

## 新增 API (分层压测)

### Health & Status

```bash
# 运行时状态（含 MQ 模式、JVM 指标、压测状态）
GET /stress/layer/status
```

响应示例：
```json
{
  "app": "executor-stress",
  "db": "UP",
  "mqMode": "MockMQMessagePublisher",
  "system": {
    "availableProcessors": 4,
    "heapUsedMB": 256,
    "heapMaxMB": 4096,
    "threadCount": 45,
    "dbConnections": 4,
    "gcCount": 12,
    "gcTimeMs": 350
  },
  "metrics": {
    "tasksProduced": 15000,
    "tasksProducedFailed": 0
  },
  "soakRunning": false,
  "stairRunning": false
}
```

---

### Layer 1: Mock MQ 基准

```bash
POST /stress/layer/mock-run
Content-Type: application/json

{
  "numTasks": 5000,
  "numBizGroups": 5,
  "cronExpr": "0/1 * * * * ?",
  "baseBizName": "layer-mock",
  "maxRounds": 200
}
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| numTasks | int | 5000 | 创建任务总数 |
| numBizGroups | int | 5 | 分片数量（均匀分配） |
| cronExpr | string | "0/1 * * * * ?" | Cron 表达式 |
| baseBizName | string | "layer-mock" | 业务名（用于数据库隔离） |
| maxRounds | int | 200 | 每 biz 最大触发轮数 |

响应：
```json
{
  "layer": "Mock MQ — Scheduler Kernel Only",
  "created": 5000,
  "setupMs": 450,
  "benchMs": 1500,
  "totalProcessed": 5000,
  "mqSuccess": 5000,
  "mqFail": 0,
  "totalRounds": 25,
  "tps": 3333.33,
  "groupResults": [...],
  "metrics": {...},
  "system": {...}
}
```

---

### Layer 2: 本地 MQ 基准

```bash
POST /stress/layer/local-mq-run
Content-Type: application/json

{
  "numTasks": 2000,
  "numBizGroups": 3,
  "cronExpr": "0/1 * * * * ?",
  "baseBizName": "layer-local",
  "maxRounds": 100
}
```

参数同 Layer 1，区别是使用真实 RocketMQ 发送。

---

### Layer 3a: Burst 极限注入

```bash
POST /stress/layer/burst
Content-Type: application/json

{
  "numTasks": 50000,
  "numBizGroups": 10,
  "cronExpr": "0/1 * * * * ?",
  "baseBizName": "layer-burst",
  "maxRounds": 500
}
```

所有 bizGroup **并发执行**（虚拟线程），区别于 Layer 1/2 的串行遍历。

---

### Layer 3b: Soak 稳定性测试

```bash
POST /stress/layer/soak
Content-Type: application/json

{
  "numTasks": 3000,
  "numBizGroups": 3,
  "durationMinutes": 30,
  "reportIntervalSeconds": 30,
  "cronExpr": "0/1 * * * * ?",
  "baseBizName": "layer-soak"
}

# 停止
POST /stress/layer/soak/stop
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| numTasks | int | 3000 | 初始创建任务数 |
| numBizGroups | int | 3 | 分片数 |
| durationMinutes | int | 30 | 目标运行时长 |
| reportIntervalSeconds | int | 30 | 指标快照间隔 |

响应包含**时间序列快照**，用于检测趋势：
```json
{
  "snapshots": [
    {
      "elapsedMin": 0.5,
      "batchProcessed": 600,
      "cumulativeProcessed": 600,
      "currentTps": 1200.0,
      "heapUsedMB": 312.0,
      "heapDeltaMB": 56.0,
      "dbConnections": 8,
      "threadCount": 85
    },
    ...
  ],
  "heapGrowthMB": 12.5,
  "avgTps": 1180.5
}
```

---

### Layer 3c: 阶梯加压

```bash
POST /stress/layer/stair-step
Content-Type: application/json

{
  "startTasks": 100,
  "stepSize": 100,
  "stepIntervalSeconds": 10,
  "maxTasks": 5000,
  "numBizGroups": 3,
  "maxRoundsPerStep": 20,
  "failThreshold": 0.01
}

# 停止
POST /stress/layer/stair-step/stop
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| startTasks | int | 100 | 起始任务数 |
| stepSize | int | 100 | 每步增加量 |
| stepIntervalSeconds | int | 10 | 步间间隔 |
| maxTasks | int | 5000 | 最大累计任务数 |
| failThreshold | double | 0.01 | 失败率阈值(1%) |

响应包含**拐点分析**：
```json
{
  "totalSteps": 18,
  "cumulativeTasks": 1800,
  "kneePointStep": 12,
  "kneePointTasks": 1200,
  "steps": [
    {"step": 1, "cumulativeTasks": 100, "tps": 850.0, "failRate": 0.0},
    {"step": 2, "cumulativeTasks": 200, "tps": 1650.0, "failRate": 0.0},
    {"step": 12, "cumulativeTasks": 1200, "tps": 8500.0, "failRate": 0.5},
    {"step": 13, "cumulativeTasks": 1300, "tps": 8600.0, "failRate": 1.2}
  ]
}
```

---

### 批量 Cleanup

```bash
DELETE /stress/layer/cleanup?numBizGroups=5
```

清除所有 `layer-*` 前缀的测试数据（mock/local/stair/soak/burst）。

---

## 原始 API (兼容)

所有原始 `/stress/low-freq/*` 和 `/stress/high-freq/*` 端点保留不变，详见 `executor-stress/README.md`。

## 配置属性参考

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `stress.mq.mock` | false | true=Mock MQ, false=真实 MQ |
| `stress.mq.mock-delay-ms` | 0 | Mock 模拟延迟(ms) |
| `xxl.job.process.nameserver` | - | RocketMQ NameServer 地址 |
| `xxl.job.process.topic` | executorConsumeTask | MQ Topic |
| `xxl.job.process.send-message-timeout` | 3000 | MQ 发送超时 |
| `server.port` | 8083 | 服务端口 |
| `spring.datasource.url` | - | MySQL 连接 |
