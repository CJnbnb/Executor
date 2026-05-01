# SDK 使用指南

## 1. 引入依赖

业务方在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.executor</groupId>
    <artifactId>executor-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

> SDK 不传递任何 XXL-Job 依赖，业务方无需感知调度引擎实现。

## 2. 配置

`application.properties` 中添加 RocketMQ 连接信息：

```properties
xxl.job.process.nameserver=192.168.5.8:9876
xxl.job.process.topic=executorConsumeTask
xxl.job.process.group=executorProduceGroup
xxl.job.process.access-key=rocketmq
xxl.job.process.secret-key=your_secret
```

## 3. API 使用

### 方式一：Builder API（推荐）

```java
@Autowired
private ExecutorSdkClient sdkClient;

// 注册 Cron 定时任务
sdkClient.newTask()
    .biz("order", "daily_report")          // bizName, bizGroup
    .taskName("每日订单汇总")
    .cron("0 0 8 * * ?")                    // 每天早上 8 点
    .payload("{\"type\":\"daily_report\"}")
    .schedule();

// 注册一次性任务
sdkClient.newTask()
    .biz("order", "cleanup")
    .taskName("临时数据清理")
    .once(System.currentTimeMillis() + 3600_000)  // 1 小时后执行
    .payload("{\"action\":\"cleanup\"}")
    .schedule();

// 注册带自定义 Topic 的任务
sdkClient.newTask()
    .biz("user", "export")
    .taskName("用户数据导出")
    .cron("0 0/30 * * * ?")
    .topic("userExportTopic")               // 发送到指定 Topic
    .payload("{\"format\":\"csv\"}")
    .schedule();
```

### 方式二：REST API（备选）

```bash
curl -X POST http://executor-host:8082/example/task \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "测试任务",
    "bizName": "test",
    "bizGroup": "demo",
    "scheduledType": "1",
    "scheduledConf": "0 */5 * * * ?",
    "payload": "{\"key\":\"value\"}",
    "enable": true
  }'
```

### Builder API 参数说明

| 方法 | 参数 | 说明 |
|------|------|------|
| `.biz(name, group)` | 业务线、业务分组 | 必填，用于调度路由和 Dashboard 筛选 |
| `.taskName(name)` | 任务名称 | 必填，描述任务用途 |
| `.cron(expr)` | Cron 表达式 | 与 `.once()` 二选一 |
| `.once(timestamp)` | 执行时间戳(ms) | 一次性任务，与 `.cron()` 二选一 |
| `.payload(json)` | JSON 字符串 | 业务负载，消费者收到的消息体 |
| `.topic(name)` | Topic 名称 | 可选，默认 `executorPool` |
| `.schedule()` | — | 发送注册消息到 RocketMQ |

## 4. 任务参数契约

SDK 发送的 `TaskRequest` 与 executor-core 消费的 `ProcessCommonTaskDTO` 字段一一对应：

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskName` | String | 任务名称 |
| `bizName` | String | 业务线名称 |
| `bizGroup` | String | 业务分组 |
| `scheduledConf` | String | Cron 表达式（Cron 模式时填写） |
| `scheduledType` | String | `"1"` = Cron，`"2"` = 一次性 |
| `executeTime` | Long | 执行时间戳毫秒（一次性模式） |
| `enable` | Boolean | 是否启用 |
| `payload` | String | 业务负载 JSON |
| `topic` | String | 目标 MQ Topic |

## 5. 消费者端

业务方消费执行消息时，收到的消息结构与 `ProduceCommonTaskMessage` 一致：

```json
{
  "id": "1714567890123-5678",
  "taskName": "每日订单汇总",
  "bizName": "order",
  "bizGroup": "daily_report",
  "scheduledConf": "0 0 8 * * ?",
  "scheduledType": "1",
  "payload": "{\"type\":\"daily_report\"}",
  "topic": "executorPool",
  "nextTriggerTime": 1714651200000
}
```
