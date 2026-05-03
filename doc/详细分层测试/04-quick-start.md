# Executor 分层压力测试 — 快速开始指南

## 5 分钟上手

### 前置条件

- JDK 21+ 已安装
- MySQL 8.0+ 本地运行（数据库 `xxl_job_executor_mq` 已建表）
- Docker Desktop 已启动（用于 Layer 2/3）
- 项目已编译：`mvn package -pl executor-stress -DskipTests`

### Step 1: 启动 Docker RocketMQ

```bash
cd F:/xxl-executor/docker-stress-mq
docker-compose up -d

# 等待 Broker 启动（约 15 秒）
sleep 15
docker ps
# 预期: rmqnamesrv, rmqbroker, rocketmq-dashboard 三个容器 UP
```

### Step 2: 配置

编辑 `executor-stress/src/main/resources/application.properties`：

```properties
# DB（必填）
spring.datasource.url=jdbc:mysql://localhost:3306/xxl_job_executor_mq?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=你的密码

# MQ（本地 Docker）
xxl.job.process.nameserver=localhost:9876
xxl.job.process.access-key=
xxl.job.process.secret-key=
```

### Step 3: 启动压测服务

```bash
java -jar executor-stress/target/executor-stress-0.0.1-SNAPSHOT.jar
```

### Step 4: 验证

```bash
# 健康检查
curl http://localhost:8083/stress/layer/status | python3 -m json.tool
# 应返回: "db": "UP", "mqMode": "RocketMQMessagePublisher"
```

---

## Layer 1: Mock MQ 快速压测

```bash
# 1. 配置 Mock 模式（修改 application.properties 或启动参数）
# stress.mq.mock=true

# 2. 重启或以下面参数启动
java -jar executor-stress/target/executor-stress-0.0.1-SNAPSHOT.jar \
  --stress.mq.mock=true

# 3. 执行压测
curl -X POST http://localhost:8083/stress/layer/mock-run \
  -H "Content-Type: application/json" \
  -d '{"numTasks":5000,"numBizGroups":5}'
```

期望结果：TPS 远超 300，达到 3000-10000+。

---

## Layer 2: 本地 MQ 压测

```bash
# 确保 stress.mq.mock=false（默认）

curl -X POST http://localhost:8083/stress/layer/local-mq-run \
  -H "Content-Type: application/json" \
  -d '{"numTasks":2000,"numBizGroups":3}'
```

期望结果：TPS 3000-10000（取决于 Broker 配置和消息体大小）。

---

## Layer 3: 全链路压测

### Burst 极限注入

```bash
curl -X POST http://localhost:8083/stress/layer/burst \
  -H "Content-Type: application/json" \
  -d '{"numTasks":50000,"numBizGroups":10,"maxRounds":500}'
```

### Soak 稳定性测试 (30 分钟)

```bash
curl -X POST http://localhost:8083/stress/layer/soak \
  -H "Content-Type: application/json" \
  -d '{
    "numTasks": 3000,
    "numBizGroups": 3,
    "durationMinutes": 30,
    "reportIntervalSeconds": 30
  }'
```

### 阶梯加压

```bash
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
  }'
```

---

## 监控参考

### 应用指标
```bash
# 实时状态
curl http://localhost:8083/stress/layer/status | python3 -m json.tool

# 原始 metrics
curl http://localhost:8083/stress/metrics
```

### JVM 监控 (另一个终端)
```bash
# 找到 Java 进程 PID
jps -l | grep stress

# GC 监控
jstat -gcutil <PID> 1000

# 线程数
jcmd <PID> Thread.print | grep -c "Thread-"

# Heap
jcmd <PID> GC.heap_info
```

### Docker MQ 监控
```bash
# 容器资源
docker stats rmqbroker rmqnamesrv

# 日志
docker logs -f rmqbroker --tail 50

# Dashboard
open http://localhost:8090
```

### MySQL 监控
```sql
-- 连接数
SHOW PROCESSLIST;

-- 锁等待
SELECT * FROM information_schema.INNODB_LOCK_WAITS;

-- 任务数
SELECT biz_name, biz_group, COUNT(*) 
FROM user_scheduled_common_task 
WHERE biz_name LIKE 'layer-%' 
GROUP BY biz_name, biz_group;
```

---

## 测试后清理

```bash
# API 清理
curl -X DELETE "http://localhost:8083/stress/layer/cleanup?numBizGroups=10"

# Docker 清理
cd F:/xxl-executor/docker-stress-mq
docker-compose down -v
```

---

## 常见问题

**Q: Mock 模式下 TPS 仍然很低（<500）？**
A: 检查 DB 连接池配置 (`spring.datasource.hikari.maximumPoolSize`)，确保 >= 20。检查是否有其他进程占用 MySQL。

**Q: 本地 MQ 连接失败？**
A: 确认 Docker 正在运行，`telnet localhost 9876` 验证端口可达。

**Q: Burst 测试出现大量 MQ 失败？**
A: 降低 numTasks 或增大 Broker `defaultTopicQueueNums`。检查 Broker 日志是否有限流。

**Q: Soak 测试中 TPS 逐渐下降？**
A: 可能是内存泄漏或连接池泄漏。观察 heap 趋势和 DB 连接数。考虑缩短 durationMinutes。

**Q: 阶梯加压在低负载就触发了阈值？**
A: 降低 `failThreshold`（如从 0.01 改为 0.05）或增大 `stepIntervalSeconds` 给系统更多恢复时间。
