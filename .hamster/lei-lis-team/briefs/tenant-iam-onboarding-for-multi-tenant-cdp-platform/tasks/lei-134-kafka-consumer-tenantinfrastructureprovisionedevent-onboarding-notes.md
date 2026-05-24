---
id: "a6892fcd-d417-4f64-ab43-4e2106b29ca3"
entity_type: "task"
entity_id: "e5d6d4e0-fd1a-4d17-915b-6874908a987f"
title: "Kafka Consumer 接收 TenantInfrastructureProvisionedEvent 并触发 onboarding - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-134"
parent_task_id: "3f23a260-b3b2-4b13-bba0-38e8f6f01035"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:15:44.771343+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

实现 Kafka Consumer 适配器，订阅 `TenantInfrastructureProvisionedEvent` 并触发 `TenantIamProvisioningService` 的 onboarding 用例。

## Implementation Approach

1. 在基础设施层（`infrastructure/kafka/inbound`）定义 Kafka Consumer，订阅 `TenantInfrastructureProvisionedEvent` 主题。
2. 定义事件 DTO（与外部 schema 对齐），包含 `tenantId`、`tier`、`adminEmail`、`correlationId` 字段。
3. 在 Consumer 中：

- 反序列化为 DTO
- 校验必填字段
- 构造 `TenantIamDesiredState`（其它扩展字段取默认值 `LOCAL_ONLY` / `SHARED_REALM` / 空）
- 调用 `TenantIamProvisioningService.provisionTenantIam(desiredState, correlationId)`

1. 非法消息走 DLT 或显式记录拒绝，不阻塞分区消费。
2. 不在 Consumer 中实现重试/幂等逻辑（重试由 Step Pipeline 的 ensure 语义保证，幂等去重由后续子任务负责）。

## Acceptance Criteria

- Kafka Consumer 能订阅并反序列化 `TenantInfrastructureProvisionedEvent`
- 合法事件触发一次应用用例调用
- 非法事件被拒绝并记录，不阻塞分区
- 应用核心不依赖任何 Kafka 类型
- 测试覆盖合法 + 非法事件路径

## Technical Constraints

- Consumer 位于基础设施层，仅 import 应用服务接口
- 不在 Consumer 内执行业务逻辑或状态机判断
- correlationId 必须从事件透传到应用层

## Code Patterns to Follow

- Ports and Adapters：Consumer 是入站适配器，调用应用服务
- 与兄弟任务 "Tenant IAM 事件边界契约" 的 inbound event 定义保持一致## Details

**Scope**: 实现订阅 TenantInfrastructureProvisionedEvent 的 Kafka Consumer 适配器，将事件转换为 TenantIamDesiredState 并调用 TenantIamProvisioningService

**Out of Scope**: Kafka Publisher 实现（独立子任务）；幂等/去重逻辑（独立子任务）；correlationId 在日志中的 MDC 注入（独立子任务）；应用服务的状态机与 Step Pipeline 实现（兄弟任务负责）；TenantIamDesiredState 领域模型定义（兄弟任务负责）

## Acceptance Criteria

- [ ] Kafka Consumer 能成功订阅 TenantInfrastructureProvisionedEvent 主题并反序列化消息为领域内部表示
- [ ] Consumer 将事件转换为 TenantIamDesiredState 并调用 TenantIamProvisioningService.provisionTenantIam(desiredState, correlationId)
- [ ] 字段缺失或反序列化失败的非法消息不会导致 Consumer 崩溃，必须显式记录并不阻塞后续消息
- [ ] Consumer 适配器位于基础设施层，应用核心不依赖任何 Kafka 类型或注解
- [ ] 单元/集成测试验证：合法事件触发一次 onboarding 用例调用，非法事件不会触发用例调用

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

