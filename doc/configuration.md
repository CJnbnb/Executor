# 配置参考

## 1. executor-core 配置

### 基础配置（application.properties）

```properties
spring.application.name=xxl-job-executor-mq-improve
server.port=8081
```

### RocketMQ 配置

| 配置项 | 说明 | 示例值 |
|--------|------|--------|
| `xxl.job.process.nameserver` | NameServer 地址 | `192.168.5.8:9876` |
| `xxl.job.process.topic` | SDK 注册任务使用的 Topic | `executorConsumeTask` |
| `xxl.job.process.group` | 消费组名称 | `executorConsumeMessageGroup` |
| `xxl.job.process.access-key` | RocketMQ ACL 用户名 | `rocketmq` |
| `xxl.job.process.secret-key` | RocketMQ ACL 密码 | 通过环境变量覆盖 |
| `xxl.job.process.send-message-timeout` | 发送超时（毫秒） | `3000` |
| `xxl.job.process.retry-times-when-send-failed` | 发送失败重试次数 | `2` |
| `xxl.job.producer.produceGroup` | 生产者组名称 | `executorProduceGroup` |

### XXL-Job Executor 配置

| 配置项 | 说明 | 示例值 |
|--------|------|--------|
| `xxl.job.executor.address` | 外部可访问的本机地址 | `192.168.5.8` |
| `xxl.job.executor.ip` | 绑定 IP（留空自动检测） | |
| `xxl.job.executor.port` | 执行器通信端口 | `9999` |
| `xxl.job.executor.logpath` | 日志存储路径 | `/data/applogs/xxl-job/jobhandler` |
| `xxl.job.executor.logretentiondays` | 日志保留天数 | `30` |

### XXL-Job Admin 配置

| 配置项 | 说明 | 示例值 |
|--------|------|--------|
| `xxl.job.admin.address` | Admin 控制台地址 | `http://localhost:8080/xxl-job-admin` |
| `xxl.job.admin.token` | 通信令牌 | `default_token` |
| `xxl.job.admin.appname` | 执行器在 Admin 中注册的名称 | `xxl-job-executor-mq-improve` |

### 数据源配置

| 配置项 | 说明 | 示例值 |
|--------|------|--------|
| `spring.datasource.url` | MySQL 连接 | `jdbc:mysql://localhost:3306/xxl_job_executor?...` |
| `spring.datasource.username` | 数据库用户名 | `root` |
| `spring.datasource.password` | 数据库密码 | **务必通过环境变量覆盖** |
| `spring.datasource.driver-class-name` | JDBC 驱动 | `com.mysql.cj.jdbc.Driver` |

### MyBatis 配置

| 配置项 | 说明 | 示例值 |
|--------|------|--------|
| `mybatis.mapper-locations` | Mapper XML 路径 | `classpath:/mapper/*Mapper.xml` |
| `mybatis.configuration.map-underscore-to-camel-case` | 驼峰映射 | `true` |

### 存储 / MQ 实现选择

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `xxl.job.store.type` | 存储实现 | `mybatis` |
| `xxl.job.mq.type` | 消息队列实现 | `rocketmq` |

## 2. 环境变量覆盖

所有敏感配置支持通过环境变量覆盖，优先级：**环境变量 > 配置文件默认值**。

```bash
# 必须设置
export DB_PASSWORD=your_db_password
export ROCKETMQ_SECRET_KEY=your_rocketmq_secret

# 可选覆盖
export DB_URL=jdbc:mysql://prod-host:3306/xxl_job_executor?useSSL=false&serverTimezone=Asia/Shanghai
export DB_USERNAME=prod_user
export ROCKETMQ_NAMESERVER=prod-nameserver:9876
```

## 3. executor-sdk 配置（业务方）

业务方在 `application.properties` 中配置：

```properties
# RocketMQ NameServer（必填）
xxl.job.process.nameserver=192.168.5.8:9876
# 任务注册 Topic（必填，与 executor-core 的 topic 一致）
xxl.job.process.topic=executorConsumeTask
# Producer Group
xxl.job.process.group=executorProduceGroup
# ACL 认证（必填）
xxl.job.process.access-key=rocketmq
xxl.job.process.secret-key=your_secret
```

## 4. XXL-Job Admin 调度配置

在 XXL-Job Admin 控制台中为 executor-core 创建调度任务。**这是任务能被执行的关键环节**——没有匹配的调度任务，SDK 注册的任务永远不会被扫描和执行。

### 任务参数与路由机制

SDK 注册任务时通过 `.biz(bizName, bizGroup)` 指定业务归属，ProducerHandler 通过 XXL-Job 的**任务参数**决定扫描哪些任务：

```
XXL-Job Admin 任务参数 → ProducerHandler 解析为 bizName + bizGroup
                     → SQL: WHERE biz_name=? AND biz_group=?
```

**两者必须完全一致**（字符级匹配）。如果不一致，任务会一直待在 DB 中不被执行。

### 任务配置模板

| 字段 | 值 |
|------|-----|
| 执行器 | `xxl-job-executor-mq-improve` |
| JobHandler | `Executor` |
| 调度类型 | CRON |
| Cron | `0/5 * * * * ?`（每5秒扫描一次）|
| 任务参数 | `bizName,bizGroup`（如 `order,daily_report`）|
| 路由策略 | 分片广播 |

### 多租户调度隔离

为不同 bizName/bizGroup 分别创建 XXL-Job 任务，可实现：
- **独立扫描频率**: 高频任务用 `0/3 * * * * ?`，低频用 `0/30 * * * * ?`
- **独立分片并行**: 每个组合可以有自己的分片策略
- **故障隔离**: 一个分组的任务积压不影响其他分组

```text
XXL-Job 任务1: 参数="order,daily_report"  Cron="0/5 * * * * ?"
XXL-Job 任务2: 参数="order,export"        Cron="0/30 * * * * ?"  
XXL-Job 任务3: 参数="user,cleanup"        Cron="0 0 2 * * ?"
```

### 常见配置错误

| 问题 | 原因 | 解决 |
|------|------|------|
| 任务注册后一直 pending | XXL-Job Admin 未创建对应 bizName,bizGroup 的调度任务 | 创建匹配的调度任务 |
| 部分任务不执行 | SDK 的 `.biz()` 值与 Admin 任务参数不匹配（大小写、空格） | 确保两端字符串完全一致 |
| 参数格式错误 | 任务参数不是 `xxx,yyy` 格式 | 格式必须是 `bizName,bizGroup`，不能多也不能少 |
