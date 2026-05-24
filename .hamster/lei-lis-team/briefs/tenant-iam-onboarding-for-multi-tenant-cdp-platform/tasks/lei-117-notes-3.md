---
id: "5a6a03fb-6390-4132-9991-1c105a3b4ab0"
entity_type: "task"
entity_id: "b92179a6-59c9-4987-9c4d-3c30d74af615"
title: "编写事件契约与发布行为的验证场景 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-117"
parent_task_id: "3fed74d7-7fa9-40f7-9015-70ea177ecf46"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:52.480237+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

编写验证场景，锁定事件契约（输入/输出事件类型 + EventPublisher Port + In-Memory 实现）的语义边界，确保 MVP 中事件驱动协作可被观察。

## Implementation Approach

1. 创建测试类，针对事件契约本身编写场景化测试。
2. 场景 A：构造 `TenantInfrastructureProvisionedEvent`，断言其字段可被映射构造出合法的 `TenantIamDesiredState`。
3. 场景 B：使用 `InMemoryEventPublisher` 发布 `TenantIamProvisionedEvent`，断言可读回并字段相等。
4. 场景 C：发布 `TenantIamProvisioningFailedEvent`，断言可读回，且 `failureCode`、`retryable`、`correlationId` 一致。
5. 场景 D：用同一 `correlationId` 构造输入事件、Desired State 与输出事件，断言三者 correlationId 相等。
6. 场景 E：调用 `InMemoryEventPublisher.failNextPublish()`，再 publish，断言抛 `EventPublishException`。

## Acceptance Criteria

- 输入事件字段映射 Desired State 的测试通过
- 成功事件与失败事件的发布-读回测试通过
- correlationId 贯穿测试通过
- 发布失败语义测试通过
- 所有测试不依赖完整 Provisioning Service 编排

## Technical Constraints

- 测试不得引入真实 Kafka 或外部依赖
- 测试只验证事件边界契约，不验证 Step Pipeline 或 Service 状态机

## Code Patterns to Follow

- 与兄弟任务测试风格一致的 Given/When/Then 命名

## Scope Boundaries

**In scope**：事件契约层面的验证测试。

**Out of scope**：端到端 onboarding 测试、Step 幂等测试、Kafka 适配器测试。## Details

**Scope**: 针对事件类型、Port 语义、In-Memory 适配器组合行为的验证测试；correlationId 传递验证；发布失败语义验证。

**Out of Scope**: 端到端 Provisioning Service onboarding 流程测试（Service 兄弟任务）、Step Pipeline 幂等性测试（Pipeline 兄弟任务）、Kafka 集成测试（Phase 3 兄弟任务）。

## Acceptance Criteria

- [ ] 测试验证 TenantInfrastructureProvisionedEvent 的字段足以构造 TenantIamDesiredState（字段映射不缺不多）
- [ ] 测试验证发布 TenantIamProvisionedEvent 后 InMemoryEventPublisher 中能读到该事件，且所有字段与发布时一致
- [ ] 测试验证发布 TenantIamProvisioningFailedEvent 后可读到事件，failureCode、retryable、correlationId 保留
- [ ] 测试验证同一 correlationId 可从输入事件贯穿到输出事件
- [ ] 测试验证当 InMemoryEventPublisher 开启失败模拟时，publish 抛出 EventPublishException，不被静默吞掉

## Context

| Field | Value |
|-------|-------|
| category | testing |
| complexity | 3 |

