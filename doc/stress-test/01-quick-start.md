# Executor 压力测试 — 快速开始

## 前置条件

- JDK 21+
- MySQL 8.0+ (数据库 `xxl_job_executor_mq` 已建表)
- Docker Desktop 已启动
- 项目已编译: `mvn package -pl executor-stress -DskipTests`

## Step 1: 启动 Docker RocketMQ

```bash
cd docker-stress-mq
docker-compose up -d

# 等待 Broker 就绪 (约 15 秒)
sleep 15
docker ps --filter "name=rmq"
```

## Step 2: 配置

编辑 `executor-stress/src/main/resources/application.properties`:

```properties
# DB
spring.datasource.url=jdbc:mysql://localhost:3306/xxl_job_executor_mq?useSSL=false&serverTimezone=UTC&allowMultiQueries=true
spring.datasource.username=你的用户名
spring.datasource.password=你的密码

# MQ (本地 Docker)
xxl.job.process.nameserver=localhost:9876
xxl.job.process.access-key=
xxl.job.process.secret-key=
```

## Step 3: 启动

```bash
# Mock MQ (Layer 1 — 调度内核极限)
java -jar executor-stress/target/executor-stress-0.0.1-SNAPSHOT.jar --stress.mq.mock=true

# 或 Local MQ (Layer 2/3 — 真实 RocketMQ)
java -jar executor-stress/target/executor-stress-0.0.1-SNAPSHOT.jar --stress.mq.mock=false
```

## Step 4: 验证

```bash
curl http://localhost:8083/stress/health
# 预期: {"app":"executor-stress","db":"UP","status":"UP"}
```

## 执行测试

### Layer 1: Mock MQ

```bash
curl -X POST http://localhost:8083/stress/layer/mock-run \
  -H "Content-Type: application/json" \
  -d '{"numTasks":5000,"numBizGroups":5,"cronExpr":"0/1 * * * * ?","maxRounds":200}'
```

### Layer 2: 本地 MQ

```bash
curl -X POST http://localhost:8083/stress/layer/local-mq-run \
  -H "Content-Type: application/json" \
  -d '{"numTasks":2000,"numBizGroups":3,"cronExpr":"0/1 * * * * ?","maxRounds":100}'
```

### Layer 3a: Burst 并发

```bash
curl -X POST http://localhost:8083/stress/layer/burst \
  -H "Content-Type: application/json" \
  -d '{"numTasks":10000,"numBizGroups":10,"maxRounds":100}'
```

### Layer 3b: Soak 稳定性

```bash
curl -X POST http://localhost:8083/stress/layer/soak \
  -H "Content-Type: application/json" \
  -d '{"numTasks":3000,"numBizGroups":3,"durationMinutes":30,"reportIntervalSeconds":60}'

# 中途停止
curl -X POST http://localhost:8083/stress/layer/soak/stop
```

### Layer 3c: 阶梯加压

```bash
curl -X POST http://localhost:8083/stress/layer/stair-step \
  -H "Content-Type: application/json" \
  -d '{"startTasks":100,"stepSize":100,"stepIntervalSeconds":10,"maxTasks":5000,"numBizGroups":3,"maxRoundsPerStep":20,"failThreshold":0.01}'

# 中途停止
curl -X POST http://localhost:8083/stress/layer/stair-step/stop
```

### 高频 (时间轮)

```bash
# Mock MQ 时间轮
curl -X POST http://localhost:8083/stress/layer/time-wheel/mock-run \
  -H "Content-Type: application/json" \
  -d '{"numTasks":500,"cronExpr":"0/1 * * * * ?","observeSeconds":30}'

# Local MQ 时间轮
curl -X POST http://localhost:8083/stress/layer/time-wheel/local-mq-run \
  -H "Content-Type: application/json" \
  -d '{"numTasks":500,"cronExpr":"0/1 * * * * ?","observeSeconds":30}'
```

## 监控

```bash
# 运行时状态
curl http://localhost:8083/stress/layer/status

# 指标快照
curl http://localhost:8083/stress/metrics
```

## 测试后清理

```bash
# API 清理
curl -X DELETE "http://localhost:8083/stress/layer/cleanup?numBizGroups=10"

# Docker 清理
cd docker-stress-mq
docker-compose down -v
```
