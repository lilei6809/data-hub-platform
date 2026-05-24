---
id: "c51f3bd8-a6bc-4136-a43c-8a352c66161f"
entity_type: "task"
entity_id: "3c2d1918-f7ba-429a-8290-fb5756f2b959"
title: "定义 IAM Onboarding 输出事件契约 Provisioned 与 ProvisioningFailed - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-149"
parent_task_id: "3fed74d7-7fa9-40f7-9015-70ea177ecf46"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:44.63987+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

定义 IAM provisioning 的两个输出事件 `TenantIamProvisionedEvent` 与 `TenantIamProvisioningFailedEvent`，作为 IAL 对外的事件边界契约。

## 实现思路

1. 在 IAM 领域事件包中创建 `TenantIamProvisionedEvent` 不可变值对象：`tenantId`、`organizationId`、`adminUserId`、`correlationId`。
2. 创建 `TenantIamProvisioningFailedEvent` 不可变值对象：`tenantId`、`failureCode`、`retryable`、`correlationId`。
3. `failureCode` 复用本地状态机使用的 failure code 枚举/值对象（与 `TenantIamProvisioningState.failureCode` 一致），避免出现两套词汇。
4. 在事件类型上明确文档化：不允许携带 secret、临时密码、原始异常 stacktrace 或敏感资源属性。
5. 编写单元测试：合法事件构造、字段缺失或非法时拒绝、`retryable` 语义、两个事件的等价/相等行为。
6. 在事件类型上添加 KDoc/Javadoc，描述下游消费者语义（成功触发后续 onboarding，失败触发告警与重试入口）。

## 验收标准

- 两个事件类型均在领域层定义、不可变、无基础设施依赖
- 字段集合与 brief 中契约一致
- failureCode 与本地状态机词汇一致
- 单元测试覆盖构造与字段校验

## 技术约束

- 不引入 Kafka、Jackson、Spring 等依赖
- 不实现发布逻辑
- 必须不可变；构造时校验字段

## 范围说明

- 包含：两个输出事件值对象、字段约束、单元测试
- 不包含：EventPublisher 接口与实现、Step 中触发发布的逻辑、Kafka 发布、Outbox## Details

**Scope**: TenantIamProvisionedEvent 与 TenantIamProvisioningFailedEvent 两个领域事件值对象、字段不变量、单元测试

**Out of Scope**: EventPublisher 接口与实现（独立子任务）、PublishTenantIamProvisionedEventStep 实现（Step Pipeline 兄弟任务）、Kafka 发布实现（Phase 3 兄弟任务）、Outbox Pattern（Authorization BC 兄弟任务）

## Acceptance Criteria

- [ ] TenantIamProvisionedEvent 包含 tenantId、organizationId、adminUserId、correlationId 字段且不可变
- [ ] TenantIamProvisioningFailedEvent 包含 tenantId、failureCode、retryable、correlationId 字段且不可变
- [ ] failureCode 使用枚举或领域值对象，与本地状态 failureCode 使用同一词汇表，保证语义一致
- [ ] 失败事件不携带 secret、临时密码或原始异常栈 trace，字段定义层面明确禁止
- [ ] 事件位于领域层，无 Kafka、序列化或 Spring 依赖

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |
| skillReferences | Hamster Blueprint |

