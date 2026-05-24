---
id: "ceeebad6-f972-4c54-a957-2e5ca8ffdaa8"
entity_type: "task"
entity_id: "25e8f42f-7d28-42da-b7a7-4ea1fa95b37e"
title: "实现 MarkIamProvisionedStep 与 PublishTenantIamProvisionedEventStep - Notes"
status: "todo"
priority: "high"
display_id: "LEI-159"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:17:18.41653+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现 Pipeline 终态的两个步骤：本地状态标记 `IAM_PROVISIONED` 与发布 `TenantIamProvisionedEvent`。

## 实现思路

1. 实现 `MarkIamProvisionedStep`：

- 通过 TenantIamStateRepository 读取当前状态。
- 不是 `IAM_PROVISIONED` → 更新为 `IAM_PROVISIONED` 并写 `provisionedAt`。
- 已经是 `IAM_PROVISIONED` → 直接返回。

1. 实现 `PublishTenantIamProvisionedEventStep`：

- 从 ExecutionContext 取 organizationId、adminUserId。
- 构造 `TenantIamProvisionedEvent` 并交给 EventPublisher。

1. 编写 Step 级单元测试，使用 Mock/Stub 验证幂等行为与事件字段映射。

## 验收标准

- 首次执行正确写入状态与 `provisionedAt`。
- 重复执行保持幂等。
- 事件载荷字段映射正确。
- 单元测试覆盖以上场景。

## 技术约束

- Step 内部不出现具体仓储/消息中间件类型。
- 通过领域异常表达失败。

## 范围边界

- **不**定义 TenantIamStateRepository 接口或状态机本身（兄弟任务）。
- **不**定义 EventPublisher 抽象或事件契约（兄弟任务）。
- **不**处理 IAM_FAILED 状态流转或失败事件发布（兄弟任务）。## Details

**Scope**: MarkIamProvisionedStep 与 PublishTenantIamProvisionedEventStep 的实现及单元测试

**Out of Scope**: TenantIamStateRepository 接口与状态机详细定义（兄弟任务）、EventPublisher 抽象与事件契约（兄弟任务）、失败事件发布 / IAM_FAILED 状态流转（兄弟任务）

## Acceptance Criteria

- [ ] `MarkIamProvisionedStep` 首次执行将状态设为 IAM_PROVISIONED 并填充 provisionedAt
- [ ] `MarkIamProvisionedStep` 重复执行保持幂等，不报错也不重复记录变更
- [ ] `PublishTenantIamProvisionedEventStep` 发布的事件包含 ExecutionContext 中的 organizationId、adminUserId 与 correlationId
- [ ] 两个 Step 仅通过抽象调用依赖，不出现具体存储或消息中间件类型
- [ ] 单元测试覆盖：首次标记、重复标记幂等、事件载荷字段映射

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |

