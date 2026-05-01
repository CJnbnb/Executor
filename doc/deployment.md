# 部署指南

## 1. 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 21+ | 虚拟线程支持 |
| MySQL | 8.0+ | 任务持久化存储 |
| RocketMQ | 4.x/5.x | 消息队列 |
| XXL-Job Admin | 2.4.0 | 调度管理控制台 |
| Maven | 3.6+ | 构建工具 |

## 2. 数据库初始化

```bash
mysql -u root -p < doc/schema.sql
```

执行后会在数据库中创建：
- 数据库：`xxl_job_executor`
- 表：`user_scheduled_common_task`

## 3. 构建项目

```bash
# 先构建 SDK
cd executor-sdk
mvn clean install -DskipTests

# 再构建主项目
cd Executor
mvn clean package -DskipTests
```

## 4. 配置 executor-core

```bash
cd Executor/executor-core

# 设置环境变量
export DB_URL=jdbc:mysql://your-db-host:3306/xxl_job_executor?useSSL=false&serverTimezone=Asia/Shanghai
export DB_USERNAME=your_user
export DB_PASSWORD=your_password
export ROCKETMQ_SECRET_KEY=your_rocketmq_secret
```

编辑 `src/main/resources/application.properties`：
- 修改 `xxl.job.process.nameserver` 指向你的 RocketMQ NameServer
- 修改 `xxl.job.executor.address` 为本机可访问 IP
- 确认 `xxl.job.admin.address` 指向 XXL-Job Admin 地址

## 5. 启动 executor-core

```bash
mvn spring-boot:run
# 或
java -jar target/executor-core-0.0.1-SNAPSHOT.jar
```

启动成功后：
- 应用端口：`8081`
- Dashboard：`http://{host}:8081/`
- XXL-Job remoting 端口：`9999`

## 6. 在 XXL-Job Admin 中注册执行器

1. 登录 XXL-Job Admin 控制台
2. 执行器管理 → 新增执行器
   - AppName：`xxl-job-executor-mq-improve`
   - 名称：`Executor 调度引擎`
   - 注册方式：自动注册

## 7. 创建调度任务

**关键理解**: SDK 注册任务时指定了 `bizName` 和 `bizGroup`，XXL-Job Admin 的任务参数决定扫描哪些组合。**两端必须完全一致**，否则任务注册了也不会执行。

```
SDK 端:   .biz("order", "daily_report")  →  DB: biz_name='order', biz_group='daily_report'
Admin 端: 任务参数 = "order,daily_report" →  ProducerHandler: WHERE biz_name='order' AND biz_group='daily_report'
```

在 XXL-Job Admin 中创建任务：

| 配置项 | 值 |
|--------|-----|
| 执行器 | `xxl-job-executor-mq-improve` |
| JobHandler | `Executor` |
| 调度类型 | CRON |
| Cron | `0/5 * * * * ?` |
| 任务参数 | `bizName,bizGroup`（如 `order,daily_report`）|
| 路由策略 | 分片广播 |

> 可以为不同 bizName/bizGroup 组合创建多个调度任务，实现分组隔离调度、独立 Cron 频率和独立分片策略。

## 8. 业务方接入

业务方引入 `executor-sdk` 并配置 RocketMQ 连接信息后即可注册任务。详见 [SDK 使用指南](sdk-usage.md)。

## 9. 验证部署

1. Dashboard 能正常访问：`http://{host}:8081/`
2. 统计卡片数据正常（即使为 0）
3. 通过示例应用注册一个测试任务：
   ```bash
   curl -X POST http://{host}:8082/example/task \
     -H "Content-Type: application/json" \
     -d '{"taskName":"部署验证","bizName":"test","bizGroup":"deploy","scheduledType":"1","scheduledConf":"0 */1 * * * ?","payload":"{\"test\":true}","enable":true}'
   ```
4. 在 Dashboard 中能看到新注册的任务
5. 下一个调度周期后任务状态变为"已完成"
