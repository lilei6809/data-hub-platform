---
id: "123aa247-ce7c-4710-b2db-ff27a76d4bb7"
entity_type: "task"
entity_id: "c604531c-1d38-4117-bad0-d712a7df2c0f"
title: "入站事件去重与重复消费安全策略 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-153"
parent_task_id: "3f23a260-b3b2-4b13-bba0-38e8f6f01035"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:55.077918+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

在 Kafka Consumer 层实现重复事件识别与状态感知的处理策略，保证 at-least-once 投递下 onboarding 安全。

## Implementation Approach

1. 在 Consumer 处理流程开始时，通过 `TenantIamStateRepository` 查询当前 `tenantId` 的 IAM 状态。
2. 根据状态决策：

- `IAM_PROVISIONED` → 记录"重复事件已忽略"日志，提交 offset，不调用应用服务
- `IAM_PROVISIONING` → 拒绝/延迟（例如不提交 offset 触发重新投递，或抛出可重试异常）
- `IAM_FAILED` 或 `PENDING_IAM` → 正常调用应用服务

1. 决策日志必须包含 `tenantId`、`correlationId`、`currentStatus`、`action`。
2. Step Pipeline 的 ensure 语义作为最终防线（即使决策放行，重复执行仍然安全）。
3. 编写集成测试验证三种状态下的行为。

## Acceptance Criteria

- 同一事件重复消费不会破坏租户 IAM 状态
- 已 PROVISIONED 时短路返回
- PROVISIONING 时避免并发竞争
- FAILED 时支持重试
- 测试覆盖三种状态分支

## Technical Constraints

- 决策必须基于持久化的本地状态，不能基于内存缓存
- 不可假定 Consumer 单实例运行（必须考虑并发消费场景）
- 不引入额外去重存储（复用已有的 IAM 状态作为去重依据）

## Code Patterns to Follow

- 与兄弟任务 "Tenant IAM Provisioning Service 与本地状态机" 定义的状态枚举对齐
- 状态短路决策属于 Consumer 适配器职责，不污染应用服务## Details

**Scope**: Consumer 层对重复 TenantInfrastructureProvisionedEvent 的识别与安全处理策略，基于本地 IAM 状态的路径安排

**Out of Scope**: Step Pipeline 的 ensure 语义实现（兄弟任务）；TenantIamStateRepository 实现（兄弟任务）；Publisher 端去重；Outbox Pattern

## Acceptance Criteria

- [ ] 重复消费同一 TenantInfrastructureProvisionedEvent（同 tenantId + correlationId）不会重复创建 Keycloak 对象也不会报错
- [ ] 当本地状态已为 IAM_PROVISIONED 时，Consumer 短路返回，不触发完整 pipeline
- [ ] 当本地状态为 IAM_PROVISIONING 时，重复事件不会造成并发状态机竞争
- [ ] 当本地状态为 IAM_FAILED 时，重复事件能触发重试路径
- [ ] 集成测试覆盖：重复投递不可变事件、并发重复、失败后重试三种场景

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

