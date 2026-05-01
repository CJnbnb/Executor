# Dashboard API 参考

Dashboard 内置于 executor-core（端口 8081），**仅供中台团队使用**，外部业务团队无权限访问。

## 访问地址

```
http://{executor-host}:8081/
```

## API 列表

### 1. 获取统计数据

```
GET /dashboard/stats
```

响应：
```json
{
  "total": 150,
  "enabled": 120,
  "pending": 45,
  "disabled": 30,
  "processing": 5,
  "stuck": 2
}
```

| 字段 | 说明 |
|------|------|
| `total` | 任务总数 |
| `enabled` | 启用中的任务数 |
| `pending` | 待执行：enable=1 且 next_trigger_time 已过期 |
| `disabled` | 已禁用的任务数 |
| `processing` | 正在执行中的任务数 |
| `stuck` | 卡住任务：process=processing 且超过 5 分钟未释放 |

---

### 2. 任务列表（分页 + 筛选）

```
GET /dashboard/tasks?page=1&size=20&taskName=xxx&bizName=xxx&bizGroup=xxx&enable=1&process=processing
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | int | 否 | 页码，默认 1 |
| `size` | int | 否 | 每页条数，默认 20 |
| `taskName` | String | 否 | 任务名称模糊匹配 |
| `bizName` | String | 否 | 业务线名称模糊匹配 |
| `bizGroup` | String | 否 | 业务分组模糊匹配 |
| `enable` | String | 否 | `1`=启用，`0`=禁用 |
| `process` | String | 否 | `pending`=待处理，`processing`=处理中，`stuck`=卡住，`done`=已完成 |

响应：
```json
{
  "list": [
    {
      "id": "1714567890123-5678",
      "taskId": "1714567890123-5678",
      "taskName": "每日订单汇总",
      "bizName": "order",
      "bizGroup": "daily_report",
      "scheduledConf": "0 0 8 * * ?",
      "scheduledType": "1",
      "nextTriggerTime": 1714651200000,
      "lastTriggerTime": 1714564800000,
      "enable": "1",
      "process": "done",
      "payload": "{\"type\":\"report\"}",
      "topic": "executorPool",
      "createAt": "2026-05-01 08:00:00",
      "updateAt": "2026-05-01 18:00:00"
    }
  ],
  "total": 150,
  "page": 1,
  "size": 20,
  "totalPages": 8
}
```

---

### 3. 切换启用/禁用

```
PUT /dashboard/tasks/{id}/toggle
```

响应：
```json
{"success": true}
```

---

### 4. 释放任务（移除 processing 状态）

```
PUT /dashboard/tasks/{id}/release
```

将卡住或异常处理中的任务释放回待处理状态（`process = NULL`）。

响应：
```json
{"success": true}
```

---

### 5. 删除任务

```
DELETE /dashboard/tasks/{id}
```

响应：
```json
{"success": true}
```

---

### 6. 批量启用/禁用

```
PUT /dashboard/tasks/batch/toggle
Content-Type: application/json

{"ids": ["id1", "id2", "id3"], "enable": "1"}
```

| 字段 | 说明 |
|------|------|
| `ids` | 任务 ID 列表 |
| `enable` | `"1"` 批量启用，`"0"` 批量禁用 |

---

### 7. 批量释放

```
PUT /dashboard/tasks/batch/release
Content-Type: application/json

{"ids": ["id1", "id2"]}
```

---

### 8. 批量删除

```
DELETE /dashboard/tasks/batch/delete
Content-Type: application/json

{"ids": ["id1", "id2"]}
```

## 错误处理

所有接口返回 HTTP 200，通过 `success` 字段判断结果。失败时额外返回错误信息：

```json
{"success": false, "message": "错误描述"}
```
