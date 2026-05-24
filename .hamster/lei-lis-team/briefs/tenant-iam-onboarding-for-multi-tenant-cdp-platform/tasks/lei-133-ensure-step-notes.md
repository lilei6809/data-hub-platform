---
id: "f20533d2-d80e-4c37-9bd2-a8880605f33d"
entity_type: "task"
entity_id: "ac6429ec-8097-4b92-b4ed-0df6d3fedea6"
title: "编写每个 Ensure Step 的幂等单元测试 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-133"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:00:52.806852+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

为 7 个 Ensure 步骤分别编写步骤级幂等单元测试，验证 ensure 语义在每个步骤上单独成立。

## 实现要点

- 每个步骤的测试至少包含：
- 首次执行 → 创建对应事实并写入 Context。
- 重复执行 → 复用现有对象，无重复副作用。
- Keycloak 相关步骤增加 409 Conflict 场景测试，验证步骤通过查询已有对象后继续。
- `EnsureOrganizationAttributesStep` 增加属性不一致 → 校正、属性一致 → no-op 两种场景。
- 通过断言关键事实验证 PRD 的可观察行为：
- Organization 的 `tenant_id` / `tier` 正确。
- Admin User 可由 email 查到。
- Admin User 是目标 Organization 的成员。
- Admin User 拥有 `TENANT_ADMIN`。
- 使用 sibling 任务交付的 Fake Keycloak Adapter 与 In-Memory Repository / EventPublisher。

## 验收标准

- 每个 Step 都有专属的幂等测试。
- Keycloak 相关步骤覆盖 409 场景。
- 属性步骤覆盖一致与不一致两条路径。
- 关键属性事实由断言显式验证。
- 测试无外部依赖。

## 技术约束

- 仅做步骤级别测试，端到端 Pipeline 与状态机行为不在本任务中验证。
- 测试不得依赖网络或真实 Keycloak。

## 范围边界

- **包含**：7 个步骤的单元级幂等测试。
- **不包含**：Pipeline 端到端测试、ProvisioningService 状态机测试、中途失败后整体重试测试、真实 Keycloak / Kafka 集成测试。## Details

**Scope**: 7 个 Ensure Step 的步骤级幂等单元测试，含 409 场景与属性校正场景

**Out of Scope**: Pipeline 端到端测试、ProvisioningService 状态机测试、中途失败后重试的整体场景、真实 Keycloak 集成测试、Kafka 集成测试

## Acceptance Criteria

- [ ] 7 个 Ensure Step 均有对应的单元测试，覆盖首次执行、重复执行两种场景
- [ ] 涉及 Keycloak 调用的步骤都有 409 Conflict 场景测试，验证步骤能查询已有对象后继续而不失败
- [ ] EnsureOrganizationAttributesStep 的测试覆盖属性完全一致（no-op）与属性不一致需要校正两种情形
- [ ] 测试验证关键事实：Organization 携带正确 tenant_id/tier、初始管理员可通过 email 查询到、管理员属于目标 Organization、管理员拥有 TENANT_ADMIN
- [ ] 测试使用 sibling 提供的 Fake/In-Memory Adapter 与内存 Repository/EventPublisher，不起动真实 Keycloak 或中间件

## Context

| Field | Value |
|-------|-------|
| category | testing |
| complexity | 5 |
| skillReferences | Hamster Blueprint |

