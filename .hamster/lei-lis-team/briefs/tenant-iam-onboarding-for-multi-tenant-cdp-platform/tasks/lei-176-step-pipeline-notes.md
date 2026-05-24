---
id: "ee7b199d-da40-49f9-b893-4abe3f46c02c"
entity_type: "task"
entity_id: "fd1b1f4d-171b-4623-b220-7070b1b7dca0"
title: "编写 Step Pipeline 端到端幂等与中断恢复验证测试 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-176"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:19:06.158604+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

通过组合真实 Step 实现与 Fake Adapter，编写端到端测试证明 Pipeline 满足 PRD 验收场景。

## 实现思路

1. 准备测试装置：使用兄弟任务提供的 Fake Keycloak Adapter、In-Memory TenantIamStateRepository、In-Memory EventPublisher。
2. 装配默认 7-Step Pipeline。
3. 编写测试用例：

- 首次 onboarding → 验证 Fake Keycloak 中所有事实存在。
- 重复 onboarding → 验证无重复对象创建。
- 模拟中断：执行到某 Step 后注入失败 → 再次完整执行，验证已有对象被复用、剩余 Step 完成。
- 验证 Organization `tenant_id`、`tier` 属性内容。
- 验证 adminUser 角色与成员关系。

1. 测试关注 Pipeline 黑盒行为，不重复 Step 单元测试。

## 验收标准

- 5 项 PRD 验收场景均有对应自动化测试。
- 测试只依赖 Fake/In-Memory 实现，不需要真实 Keycloak。
- 测试稳定可重复运行。

## 技术约束

- 测试不引入真实 Kafka、Vault、Keycloak。
- 中途失败注入通过测试替身实现（如可配置失败的 Fake Adapter 或 Spy Step）。

## 范围边界

- **不**包含 Service 层状态机/失败事件流转测试（兄弟任务）。
- **不**包含 Step 自身单元测试（其他子任务覆盖）。
- **不**包含真实 Keycloak 或 Kafka 集成测试（后续阶段）。## Details

**Scope**: Pipeline 级黑盒测试：首次执行、重复执行、中途失败后恢复、Organization 属性正确、管理员角色与成员关系正确

**Out of Scope**: Step 内部单元测试（各 Step 子任务负责）、Service 层的状态机测试（兄弟任务）、Kafka / 真实 Keycloak 集成测试（兄弟任务）

## Acceptance Criteria

- [ ] 测试证明首次执行后 Fake Keycloak 中存在 Organization、Admin User、Membership、Role Assignment
- [ ] 测试证明重复执行同一 Desired State 不产生重复对象且所有 Step 通过
- [ ] 测试证明模拟中途失败后，重试能从已有 Organization 继续完成剩余 Step
- [ ] 测试验证 Organization 属性 `tenant_id`、`tier` 与 Desired State 一致
- [ ] 测试验证 adminUser 拥有 `TENANT_ADMIN` 角色且属于目标 Organization

## Context

| Field | Value |
|-------|-------|
| category | testing |
| complexity | 4 |

