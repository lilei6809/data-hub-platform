---
id: "2d47d560-ae1a-4c19-8e86-10c80a687785"
entity_type: "task"
entity_id: "d84964c9-93d6-4ae9-a67e-0fded5f77ff6"
title: "编写端到端 onboarding 编排验证场景 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-122"
parent_task_id: "bba824b0-b333-4d0c-9e34-db881377477a"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:59:04.233761+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

为 `TenantIamProvisioningService` 编写端到端编排级测试，验证 PRD MVP 关键路径：首次成功、重复执行、中途失败后重试、失败语义与日志卫生。

## 实现思路

1. 编写测试装配助手 `TenantIamProvisioningTestHarness`：

- 装配 `InMemoryTenantIamStateRepository`
- 装配 Fake Keycloak Adapter（由兄弟父任务提供）
- 装配真实 Step Pipeline（由兄弟父任务提供 Step 实现）
- 暴露注入「故障 Step」的能力，用来模拟特定步骤抛错

1. 测试场景：

- **首次成功**：执行后断言 state.status=IAM_PROVISIONED、retryCount=0、provisionedAt 非空，Fake Keycloak 中可查到 Organization/User/Membership/Role。
- **重复执行幂等**：连续两次调用，第二次不应触发 Step 内部副作用计数器，state 保持 IAM_PROVISIONED。
- **中途失败 + 重试**：第一次注入故障 Step → 状态转为 IAM_FAILED、failureCode/Message 已写入；移除故障注入后再次调用 → 状态 IAM_PROVISIONED、retryCount=1。

1. 日志卫生测试：捕获日志输出，断言含有 tenantId 与 correlationId 字段，不含敏感字段（用敏感字段值匹配反向断言）。
2. 测试不连接真实 Keycloak、Kafka、Vault。

## 验收标准

- 测试装配助手可一键拼装最小可运行 onboarding
- 覆盖首次成功、重复执行、失败后重试三条核心路径
- 断言 retryCount、failureCode、provisionedAt 等关键字段
- 日志断言确保结构化字段存在与敏感字段缺席
- 全部测试本地可运行

## 技术约束

- 测试只使用内存与 Fake 实现，不依赖外部基础设施
- 不重复 Step 内部幂等单测（由兄弟父任务覆盖）
- 测试聚焦服务编排，不验证事件契约

## 相关技能

- Hamster Blueprint## Details

**Scope**: 服务级端到端验证场景、测试装配助手、可注入的故障 Step 测试替身、验证最终状态与关键字段

**Out of Scope**: Step 内部幂等单测（兄弟父任务）、Fake Keycloak Adapter 本身的测试（兄弟父任务）、事件发布验证（兄弟父任务）、真实 Keycloak 集成测试（兄弟父任务）、Kafka 集成测试

## Acceptance Criteria

- [ ] 提供一个集成测试装配辅助，能一错在内存中拼装 Service + In-Memory Repository + Fake Adapter + 真实 Step Pipeline
- [ ] 测试覆盖首次成功、重复执行、中途失败后重试成功三条场景
- [ ] 验证重试场景下 retryCount 正确自增、failureCode 与 failureMessage 被写入后可清除或保留为最后一次状态
- [ ] 验证重复执行同一 desired state 不会在 IAM_PROVISIONED 状态下重复调用 Step Pipeline
- [ ] 测试验证日志中出现 tenantId 与 correlationId，并断言不包含敏感凭证字段
- [ ] 所有测试可在本地 CI 里不依赖真实 Keycloak、Kafka、Vault 运行

## Context

| Field | Value |
|-------|-------|
| category | testing |
| complexity | 5 |
| skillReferences | Hamster Blueprint |

