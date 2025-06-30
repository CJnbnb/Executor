# XXL-Job Executor MQ Improve

XXL-Job Executor MQ Improve 是基于 XXL-Job 的分布式高并发任务调度中台，集成 RocketMQ 消息队列，提供高性能、高可靠的任务调度解决方案。

## 主要特性

- ⏱️ 秒级精准调度，支持时间轮算法
- 🚀 RocketMQ 消息驱动，异步高吞吐
- 🖥️ Web 管理界面，实时监控任务状态
- 🛡️ 状态机+补偿机制，保障数据一致性
- ⚡ 现代化技术栈：Spring Boot 3.x + JDK 21 + 虚拟线程



## 🏗️ 架构设计

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   XXL-Job       │    │   Executor      │    │   RocketMQ      │
│   Admin         │───▶│   MQ Improve    │───▶│   Consumer      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │   MySQL         │
                       │   (Task Store)  │
                       └─────────────────┘
```

## 🚀 快速开始

### 环境要求

- JDK 21+
- MySQL 5.0+
- RocketMQ 4.9+
- XXL-Job Admin 2.4.0+

### 安装部署

1. **克隆项目**
```bash
git clone https://github.com/your-username/xxl-job-executor-mq-improve.git
cd xxl-job-executor-mq-improve
```

2. **配置数据库**
```sql
-- 执行SQL脚本
source src/main/resources/sql/init.sql
```

3. **修改配置**
```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/xxl_job_executor_mq
    username: your-username
    password: your-password

rocketmq:
  name-server: localhost:9876
  producer:
    group: xxl-job-producer
```

4. **启动应用**
```bash
mvn spring-boot:run
```

5. **访问管理界面**
```
http://localhost:8080/manage/dashboard
```

## 📖 使用指南

### 1. 创建任务

通过XXL-Job Admin创建任务，选择执行器为"Executor"。

### 2. 配置任务参数

```
bizName:testBiz,bizGroup:testGroup
eg:testBiz-1,testGroup-1
```

### 3. 监控任务状态

访问Web管理界面查看任务执行情况。

## 🔧 配置说明

### 核心配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `xxl.job.executor.port` | 9999 | 执行器端口 |
| `xxl.job.executor.logpath` | ./logs | 日志路径 |
| `xxl.job.executor.logretentiondays` | 30 | 日志保留天数 |

### 调度配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `scheduler.limit-count` | 200 | 单次处理任务数 |
| `scheduler.pre-read-ms` | 5000 | 预读时间(毫秒) |
| `scheduler.timeout-ms` | 300000 | 超时时间(毫秒) |

## 🏗️ 项目结构

```
src/main/java/com/executor/xxljobexecutormqimprove/
├── config/              # 配置类
├── controller/          # 控制器
├── core/               # 核心模块
│   ├── base/           # 基础服务
│   ├── schedulerhandler/ # 调度处理器
│   ├── service/        # 核心服务
│   └── thread/         # 线程池
├── entity/             # 实体类
├── mapper/             # 数据访问层
├── producer/           # 消息生产者
├── service/            # 业务服务
└── util/               # 工具类
```

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

### 开发环境搭建

1. Fork项目
2. 创建特性分支
3. 提交代码
4. 创建Pull Request

### 代码规范

- 遵循阿里巴巴Java开发手册
- 单元测试覆盖率 > 80%
- 提交信息使用英文

## 📄 许可证

本项目采用 [MIT License](LICENSE) 许可证。

## 🙏 致谢

- [XXL-Job](https://github.com/xuxueli/xxl-job) - 分布式任务调度平台
- [RocketMQ](https://github.com/apache/rocketmq) - 分布式消息队列
- [Spring Boot](https://spring.io/projects/spring-boot) - 应用框架

## 📞 联系方式

- 邮箱: 1498377512@qq.com
- 微信: 19925983329
- 项目地址: https://github.com/your-username/xxl-job-executor-mq-improve

---

如果这个项目对你有帮助，请给个⭐️支持一下！ 