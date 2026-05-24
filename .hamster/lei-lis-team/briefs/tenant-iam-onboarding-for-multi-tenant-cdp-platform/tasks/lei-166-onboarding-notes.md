---
id: "704cabc8-6f61-412d-b82f-e0ac87287a91"
entity_type: "task"
entity_id: "cf114670-540a-43f4-9458-6ea17aef3a4a"
title: "编写端到端 onboarding 编排与重试场景测试 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-166"
parent_task_id: "bba824b0-b333-4d0c-9e34-db881377477a"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:17:50.213+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 概述

围绕 `TenantIamProvisioningService` 编写端到端编排测试，证明状态机能正确收敛并支撑重试入口。

## 实施方式

1. 测试装配：

- In-Memory `TenantIamStateRepository`。
- 兄弟任务提供的真实 Step Pipeline 实现。
- 兄弟任务提供的 Fake/In-Memory KeycloakAdminPort。
- no-op EventPublisher。

1. 用例 1：首次 onboarding。构造 `TenantIamDesiredState`，调用 `provisionTenantIam`；断言 Fake Keycloak 状态 + 本地状态 = IAM_PROVISIONED。
2. 用例 2：重复执行。连续调用两次；断言 Fake Keycloak 中对象计数未增加，无异常，状态保持 IAM_PROVISIONED。
3. 用例 3：中途失败后重试。

- 借助可配置 Fake Keycloak 或可插拔失败 Step，让第一次执行在某一步抛出领域异常。
- 断言状态机切到 IAM_PROVISIONING → IAM_FAILED，retryCount=1，failureCode/message 已记录。
- 清除失败注入后重试；断言剩余步骤继续执行，最终状态为 IAM_PROVISIONED，retryCount=2，Organization 未重复创建。

1. 用例 4：关键属性校验。Organization 包含 `tenant_id` 与 `tier`；admin user 可按 email 查询并拥有 `TENANT_ADMIN` 角色，是 Organization 成员。

## 验收标准

- 三条用户流被独立测试覆盖。
- 状态机字段（status、retryCount、failureCode、provisionedAt）在每个用例中显式断言。
- 测试可在不依赖外部系统的情况下运行。

## 技术约束

- 不引入真实 Keycloak、Kafka、PostgreSQL。
- 测试组合点为 ProvisioningService，不直接调用 Step。
- 不复制 Step 自身的幂等单元测试。

## 范围边界

- ✅ 针对 ProvisioningService 的端到端场景测试。
- ❌ Step 单元测试、Fake Keycloak 自身测试、真实 Keycloak 集成测试。## Details

**Scope**: 针对 TenantIamProvisioningService 的端到端场景测试：首次成功、重复执行、中途失败后重试、状态机最终收敛。测试可使用兄弟任务交付的 Step 实现与 Fake Keycloak Adapter。

**Out of Scope**: 针对单个 Step 的幂等性单元测试（幂等 Step Pipeline 任务负责）、Fake KeycloakAdminPort 本身的幂等语义测试（Keycloak Port 任务负责）、事件发布补丁发送测试（事件边界任务负责）、真实 Keycloak 集成测试。

## Acceptance Criteria

- [ ] 测试类使用 ProvisioningService 作为唯一入口，装配真实 Step Pipeline、Fake KeycloakAdminPort、In-Memory TenantIamStateRepository
- [ ] “首次执行”测试验证：ProvisioningService 调用后 Fake Keycloak 中出现 Organization、Admin User、Membership、Role Assignment，且本地状态为 IAM_PROVISIONED
- [ ] “重复执行”测试验证：同一 desiredState 连续执行两次，Fake Keycloak 对象数量不变，不抛出冲突，本地状态仍为 IAM_PROVISIONED
- [ ] “中途失败后重试”测试验证：注入一个在第 N 个 Step 抛错的场景（例如第一次 ensureAdminUser 失败），首次执行后状态为 IAM_FAILED、retryCount=1、failureCode 非空；去除故障后重试，状态推进为 IAM_PROVISIONED，retryCount=2，且 Organization 未被重复创建
- [ ] 测试验证 Organization attributes 包含 tenant_id 与 tier，初始管理员可按 email 查到且拥有 TENANT_ADMIN 角色
- [ ] 测试不依赖真实 Keycloak、Kafka 或数据库，可在 CI 中独立执行

## Context

| Field | Value |
|-------|-------|
| category | testing |
| complexity | 5 |

