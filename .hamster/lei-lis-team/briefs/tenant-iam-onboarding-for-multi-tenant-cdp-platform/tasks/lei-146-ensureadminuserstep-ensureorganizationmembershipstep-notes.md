---
id: "59f903ea-c7ae-44c8-a738-9d950016e1a4"
entity_type: "task"
entity_id: "6c212524-0cc4-482a-be40-926cf64412f1"
title: "实现 EnsureAdminUserStep 与 EnsureOrganizationMembershipStep - Notes"
status: "todo"
priority: "high"
display_id: "LEI-146"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:30.626724+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现租户初始管理员的 ensure 步骤与其 Organization 成员关系的 ensure 步骤。

## 实现思路

1. 实现 `EnsureAdminUserStep`：

- 通过 KeycloakAdminPort 按 email 查询/创建用户。
- 不存在则按临时凭证策略创建；已存在或 409 Conflict 时查询复用。
- 将 adminUserId 写入 ExecutionContext。

1. 实现 `EnsureOrganizationMembershipStep`：

- 从 ExecutionContext 取 organizationId 与 adminUserId。
- 通过 Port 确保用户已是该 Organization 成员；已存在视为成功。

1. 日志输出严格屏蔽临时凭证、密码与 secret。
2. 编写 Step 级单元测试覆盖幂等场景。

## 验收标准

- 用户首次创建后 adminUserId 进入 ExecutionContext。
- 重复执行不创建重复用户；409 Conflict 被识别。
- 成员关系不存在则建立、已存在则通过。
- 临时凭证不出现在日志中。
- 单元测试覆盖以上场景。

## 技术约束

- Step 内部不出现 Keycloak SDK 类型。
- 通过领域异常表达失败。
- 日志中只允许出现 `tenantId`、`adminUserId` 等非敏感字段。

## 范围边界

- **不**定义 KeycloakAdminPort 或 Fake Adapter（兄弟任务）。
- **不**实现 SecretStorePort（兄弟任务）。
- **不**实现其他 Step、Pipeline 组合或 Service 编排。## Details

**Scope**: EnsureAdminUserStep 与 EnsureOrganizationMembershipStep 的实现及单元测试

**Out of Scope**: KeycloakAdminPort、Fake Adapter、其他 Step、Pipeline 组合、Service 编排、SecretStore 实现

## Acceptance Criteria

- [ ] `EnsureAdminUserStep` 在用户不存在时创建并将 adminUserId 写入 ExecutionContext；存在时按 email 复用
- [ ] `EnsureAdminUserStep` 遇 409 Conflict 时查询同 email 用户并继续，不创建重复用户
- [ ] `EnsureOrganizationMembershipStep` 在关系不存在时建立绑定；已存在时直接视为成功
- [ ] 临时凭证、密码、敏感标识不出现在日志输出中
- [ ] 单元测试覆盖首次创建、已存在复用、409 冲突、已是成员 四种场景

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

