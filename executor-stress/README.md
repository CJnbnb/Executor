# executor-stress 压力测试模块

内嵌 executor-core 全部调度能力，通过 REST API 对时间轮（高频）和 XXL-Job 调度（低频）进行独立压力测试，无需启动 XXL-Job Admin。

## 前置条件

- JDK 21+
- MySQL 8.0+（执行过 executor-core 的 schema 初始化）
- RocketMQ NameServer + Broker（需 ACL 凭证）

## 快速开始

### 1. 配置

编辑 `src/main/resources/application.properties`，填入你的 RocketMQ 和数据库信息：

```properties
# RocketMQ — 必填
xxl.job.process.nameserver=<YOUR_NAMESERVER_IP>:9876
xxl.job.process.access-key=<YOUR_ACCESS_KEY>
xxl.job.process.secret-key=<YOUR_SECRET_KEY>

# DataSource — 必填
spring.datasource.url=jdbc:mysql://localhost:3306/xxl_job_executor_mq?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=<YOUR_DB_PASSWORD>
```

### 2. 构建

```bash
# 先安装 executor-core
mvn install -pl executor-core -am -DskipTests

# 打包 executor-stress
mvn package -pl executor-stress -DskipTests
```

### 3. 启动

```bash
java -jar executor-stress/target/executor-stress-0.0.1-SNAPSHOT.jar
```

服务启动在 **8083** 端口。

### 4. 验证

```bash
curl http://localhost:8083/stress/health
# {"app":"executor-stress","db":"UP","status":"UP"}
```

---

## API 参考

### Health

```bash
GET /stress/health
```

返回应用和数据库连接状态。

---

### 低频任务测试（CommonTask / XXL-Job 调度模拟）

模拟 XXL-Job Admin 的分片调度流程：lock → select → send MQ → unlock。

#### 创建任务

```bash
POST /stress/low-freq/setup
Content-Type: application/json

{
  "numTasks": 5000,           # 任务总数
  "numBizGroups": 5,           # bizGroup 数量（模拟分片）
  "cronExpr": "0/1 * * * * ?", # cron 表达式
  "baseBizName": "stress-test",# 业务名
  "topic": "executorConsumeTask"
}
```

任务写入 `user_scheduled_common_task` 表。

#### 单次触发

```bash
POST /stress/low-freq/trigger
Content-Type: application/json

{
  "bizParam": "stress-test,group-0",  # 格式: bizName,bizGroup
  "shardIndex": -1,                    # 分片索引，-1 表示不分片
  "shardTotal": -1                     # 分片总数，-1 表示不分片
}
```

每轮最多锁定 200 条（LIMIT_COUNT），通过虚拟线程并发发送 MQ。

#### 持续触发（跑完为止）

```bash
POST /stress/low-freq/run
Content-Type: application/json

{
  "bizParam": "stress-test,group-0",
  "maxRounds": 50                     # 最大轮数，无待处理任务时自动结束
}
```

#### 查看状态

```bash
GET /stress/low-freq/status?baseBizName=stress-test&numBizGroups=5
```

返回各 bizGroup 的待处理任务数。

---

### 高频任务测试（RealtimeTask / 时间轮）

任务写入 `user_scheduled_realtime_task` 表后，由 `SchedulerRealtimeService` 时间轮自动调度，无需手动触发。

#### 创建任务

```bash
POST /stress/high-freq/setup
Content-Type: application/json

{
  "numTasks": 3000,
  "cronExpr": "0/1 * * * * ?",
  "bizName": "stress-realtime",
  "bizGroup": "hft",
  "topic": "executorConsumeTask"
}
```

创建后时间轮立即开始调度。

#### 查看状态

```bash
GET /stress/high-freq/status?bizName=stress-realtime&bizGroup=hft
```

---

### Metrics

```bash
GET /stress/metrics
```

返回累计指标：

| 字段 | 含义 |
|------|------|
| tasksProduced | MQ 发送成功次数 |
| tasksProducedFailed | MQ 发送失败次数 |
| tasksConsumed | MQ 消费成功次数 |
| tasksConsumedFailed | MQ 消费失败次数 |

---

### Cleanup

```bash
DELETE /stress/cleanup?lowFreqBizName=stress-test&numLowFreqGroups=5&highFreqBizName=stress-realtime&highFreqBizGroup=hft
```

清除测试任务和事件日志。

---

## 测试场景

### 场景 1: 低频单 biz 吞吐量

```bash
# 1. 创建 1000 个任务
curl -X POST http://localhost:8083/stress/low-freq/setup \
  -H "Content-Type: application/json" \
  -d '{"numTasks":1000,"numBizGroups":1}'

# 2. 持续触发
curl -X POST http://localhost:8083/stress/low-freq/run \
  -H "Content-Type: application/json" \
  -d '{"bizParam":"stress-test,group-0","maxRounds":10}'
```

### 场景 2: 低频多 biz 并发分片

```bash
# 创建 5 bizGroup × 1000 tasks
curl -X POST http://localhost:8083/stress/low-freq/setup \
  -H "Content-Type: application/json" \
  -d '{"numTasks":5000,"numBizGroups":5}'

# 并发触发不同 bizGroup（通过多个终端或 JMeter）
curl -X POST http://localhost:8083/stress/low-freq/trigger \
  -H "Content-Type: application/json" \
  -d '{"bizParam":"stress-test,group-0"}'
```

### 场景 3: 高频时间轮基础吞吐量

```bash
# 创建 500 个每秒触发的任务
curl -X POST http://localhost:8083/stress/high-freq/setup \
  -H "Content-Type: application/json" \
  -d '{"numTasks":500,"cronExpr":"0/1 * * * * ?"}'

# 观察 30 秒
watch -n 3 'curl -s http://localhost:8083/stress/metrics | python3 -m json.tool'
```

### 场景 4: 高频多密度混合

```bash
# 同时注入不同 cron 密度的任务
for cron in "0/1 * * * * ?" "0/2 * * * * ?" "0/5 * * * * ?"; do
  curl -X POST http://localhost:8083/stress/high-freq/setup \
    -H "Content-Type: application/json" \
    -d "{\"numTasks\":200,\"cronExpr\":\"$cron\",\"bizName\":\"stress-mix-$cron\"}"
done
```

---

## 关键参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| LIMIT_COUNT | 200 | 每轮锁定任务数 |
| PRE_READ_MS | 5000 | 时间轮预读窗口（ms） |
| fastTriggerPool core | 10 | 时间轮快速触发池核心线程 |
| fastTriggerPool max | 200 | 时间轮快速触发池最大线程 |
| MQ send timeout | 3000 | RocketMQ 发送超时（ms） |
| MQ retry | 2 | RocketMQ Producer 重试次数 |
| MQ send retry | 3 (指数退避) | RocketMQMessagePublisher 内置重试 |

---

## JMeter 压测（可选）

```batch
cd executor-stress\jmeter
run-jmeter-test.bat low    # 低频测试
run-jmeter-test.bat high   # 高频测试
```

或使用 PowerShell：
```powershell
.\jmeter\run-stress-test.ps1 -Scenario low -Host localhost -Port 8083
.\jmeter\run-stress-test.ps1 -Scenario high -Host localhost -Port 8083
```

---

## 故障排查

| 现象 | 可能原因 | 检查方式 |
|------|----------|----------|
| MQ 发送全部失败 | ACL 凭证未配置 | 确认 application.properties 中 access-key/secret-key 已填写 |
| 时间轮任务不触发 | nextTriggerTime 超出预读窗口 | 查看日志 `misfire` 关键字，调整 PRE_READ_MS |
| DB 连接池耗尽 | 虚拟线程并发过高 | 确认 HikariCP maximum-pool-size 足够 |
| 任务堆积 | RocketMQ Broker 限流 | 降低 numTasks 或增大 Broker 规格 |
