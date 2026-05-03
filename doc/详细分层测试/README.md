# Executor 详细分层压力测试文档

本目录包含 xxl-executor 调度系统的分层压力测试完整方案和报告。

## 文档索引

| 文档 | 说明 | 适用人群 |
|------|------|----------|
| [01-layered-test-plan.md](01-layered-test-plan.md) | 分层测试方案设计 | 架构师、QA |
| [02-api-reference.md](02-api-reference.md) | API 参考手册 | 开发者 |
| [03-results-and-analysis.md](03-results-and-analysis.md) | 测试结果记录与分析模板 | QA、SRE |
| [04-quick-start.md](04-quick-start.md) | 5 分钟快速开始 | 所有人 |

## 快速导航

- **想知道为什么需要分层压测** → [01-layered-test-plan.md § 为什么需要分层压测](01-layered-test-plan.md)
- **想立即执行测试** → [04-quick-start.md](04-quick-start.md)
- **想了解 API 细节** → [02-api-reference.md](02-api-reference.md)
- **想记录和分析结果** → [03-results-and-analysis.md](03-results-and-analysis.md)

## 三层压测概览

```
Layer 1: Mock MQ     → 调度器纯吞吐量 (TPS: 3000-10000+)
Layer 2: Local MQ    → 应用 + 本地 MQ 基准 (TPS: 3000-10000)
Layer 3: Full Chain  → Burst + Soak 全链路验证
```

## 新增代码结构

```
executor-stress/
├── src/main/java/com/executor/stress/
│   ├── config/StressTestConfig.java        # Mock/Real MQ 切换
│   ├── controller/LayerTestController.java # 分层压测端点
│   ├── metrics/StressMetrics.java          # 扩展指标
│   └── mq/MockMQMessagePublisher.java      # Mock MQ 发送器
```

## 相关文档

- 原始压测报告: [../stress-test-report.md](../stress-test-report.md)
- 项目架构: [../architecture.md](../architecture.md)
- 部署文档: [../deployment.md](../deployment.md)
