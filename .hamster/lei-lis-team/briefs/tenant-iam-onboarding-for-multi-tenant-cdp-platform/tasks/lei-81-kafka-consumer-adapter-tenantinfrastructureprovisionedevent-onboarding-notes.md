---
id: "2d8174e3-748b-48ce-be93-bfb3a1d8a30d"
entity_type: "task"
entity_id: "051ec468-f17a-423a-bfeb-e38bf7cb7095"
title: "实现 Kafka Consumer Adapter 接收 TenantInfrastructureProvisionedEvent 并触发 onboarding - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-81"
parent_task_id: "3f23a260-b3b2-4b13-bba0-38e8f6f01035"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:56:55.740485+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现 Kafka Consumer Adapter，订阅 `TenantInfrastructureProvisionedEvent` 主题并触发 `TenantIamProvisioningService` 执行 onboarding。

## 实现方式

1. 在 infrastructure 层新增 Kafka Consumer 组件，订阅上游 `TenantInfrastructureProvisionedEvent` 主题。
2. 配置反序列化器（如 JSON 或 Avro），将 Kafka 消息解析为领域事件对象，事件契约由兄弟任务定义。
3. 将事件字段映射为 `TenantIamDesiredState`(tenantId、tier、adminEmail，默认 identityMode=LOCAL_ONLY、realmStrategy=SHARED_REALM)。
4. 调用 `TenantIamProvisioningService.provisionTenantIam(desiredState, correlationId)`。
5. 处理反序列化异常与未知字段：失败时记录日志并按预先约定的策略处理（例如发送到 DLQ 或暂停消费）。
6. 提供 Consumer 级测试，用 Embedded Kafka 或 Mock Consumer 验证事件到达后用例被触发。

## 验收标准

- 能订阅指定主题并解析事件消息
- 接收事件后能正确构造 Desired State 并调用 provisioning service
- Kafka 类型不泄漏到应用核心
- 反序列化或处理异常被显式处理且不会静默吞掉
- 拥有针对消费链路的集成测试

## 技术约束

- 反序列化、Offset 管理、错误处理等 Kafka 细节只能存在于 infrastructure 层
- 事件契约本身由兄弟任务定义，本任务只消费契约
- 应用核心入口仍是 `provisionTenantIam(desiredState, correlationId)`

## 范围

### 包含

- Kafka Consumer 配置、反序列化、事件到 Desired State 的映射、对应用服务的调用

### 不包含

- 事件契约定义与 EventPublisher 抽象（兄弟任务负责）
- Provisioning Service 实现（兄弟任务负责）
- 真实 Keycloak Adapter 实现（兄弟任务负责）## Details

**Scope**: Kafka Consumer Adapter 实现，包含主题订阅、反序列化、事件到 Desired State 的映射、调用 provisioning service 用例。

**Out of Scope**: 事件契约定义与 EventPublisher 抽象由 "Tenant IAM 事件边界契约" 兄弟任务负责；provisioning service 编排由 "Tenant IAM Provisioning Service 与本地状态机" 兄弟任务负责。

**Constraints**: Kafka 客户端类型不可泄漏到 application 与 domain 层, Consumer 与领域代码之间通过明确的事件 DTO 与映射函数隔离, 失败处理策略需可观察（日志/指标），不允许静默吞掉消息

## Acceptance Criteria

- [ ] Kafka Consumer 能订阅 `TenantInfrastructureProvisionedEvent` 主题并将消息反序列化为领域事件对象
- [ ] Consumer 接收到事件后将其映射为 `TenantIamDesiredState`（包含 tenantId、tier、adminEmail）并调用 `TenantIamProvisioningService.provisionTenantIam(...)`
- [ ] Consumer 仅作为 infrastructure 层 Adapter，应用核心不感知 Kafka 类型、ConsumerRecord 或 Kafka 反序列化异常
- [ ] 消费失败时不会静默丢失事件，错误能通过日志与失败状态被观察
- [ ] 提供测试覆盖：消息到达 Consumer 后能触发一次完整的 provisioning 调用

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

