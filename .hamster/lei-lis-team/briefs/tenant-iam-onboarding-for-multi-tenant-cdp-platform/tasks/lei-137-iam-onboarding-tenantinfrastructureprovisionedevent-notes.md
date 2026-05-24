---
id: "285116e2-3a47-47c0-9c45-b6cb4ce2a2e0"
entity_type: "task"
entity_id: "a6595e19-5944-4f00-baa0-dbc210040fa6"
title: "定义 IAM Onboarding 输入事件契约 TenantInfrastructureProvisionedEvent - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-137"
parent_task_id: "3fed74d7-7fa9-40f7-9015-70ea177ecf46"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:15:50.406148+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

在领域层定义 IAM onboarding 的输入事件契约 `TenantInfrastructureProvisionedEvent`，并提供到 `TenantIamDesiredState` 的映射。

## 实现思路

1. 在 IAM 领域层创建 `TenantInfrastructureProvisionedEvent` 不可变值对象，字段包括 `tenantId`、`tier`、`adminEmail`、`correlationId`。
2. 字段使用领域值对象（`TenantId`、`Tier`、`AdminEmail`、`CorrelationId`），构造时进行非空与格式校验。
3. 提供 `TenantIamDesiredState.fromInfrastructureEvent(event)` 或独立的 mapper，将事件翻译为 Desired State，`identityMode`、`realmStrategy`、`identityProviders`、`policies` 使用第一版默认值。
4. 编写单元测试覆盖：合法事件构造、字段缺失/非法时拒绝、事件到 Desired State 的映射正确性、correlationId 透传。
5. 在领域包内为该事件添加 KDoc/Javadoc，说明它是 IAM provisioning 的语义触发点，与未来 Kafka 消费者反序列化结果同构。

## 验收标准

- TenantInfrastructureProvisionedEvent 是不可变值对象且无外部基础设施依赖
- 提供事件到 TenantIamDesiredState 的映射函数并有测试覆盖
- correlationId 字段从事件传入并能被后续流程读取
- 非法字段在事件构造时被拒绝

## 技术约束

- 仅限领域层；不得引入 Kafka、Spring messaging、Jackson 等基础设施依赖
- 事件类型必须不可变（final/data class），字段为值对象而非裸字符串
- 不实现任何事件消费/订阅机制，仅定义类型与映射

## 范围说明

- 包含：输入事件值对象、字段校验、到 Desired State 的映射、相关单元测试
- 不包含：Kafka 消费者实现（属于 Phase 3 兄弟任务）、TenantIamDesiredState 自身模型定义（属于"Desired State 领域模型"兄弟任务，本任务只复用其已有类型）、Step Pipeline 与 Provisioning Service 实现## Details

**Scope**: 输入事件值对象的领域层定义、字段校验、到 TenantIamDesiredState 的映射、对应单元测试

**Out of Scope**: Kafka 消费者与反序列化（Phase 3 兄弟任务）、TenantIamDesiredState 模型本身（Desired State 兄弟任务）、Provisioning Service 编排与状态机（兄弟任务）

## Acceptance Criteria

- [ ] 定义不可变的 TenantInfrastructureProvisionedEvent 值对象，至少包含 tenantId、tier、adminEmail、correlationId 字段
- [ ] 事件类型位于领域层，不依赖任何 Kafka、Spring 消息基础设施或序列化框架
- [ ] 提供从 TenantInfrastructureProvisionedEvent 到 TenantIamDesiredState 的显式映射函数或工厂方法，且映射规则有单元测试覆盖
- [ ] correlationId 在事件、Desired State 和后续处理之间保持稳定可追踪
- [ ] 字段为空或非法值时构造事件失败，并有相应单元测试

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |
| skillReferences | Hamster Blueprint |

