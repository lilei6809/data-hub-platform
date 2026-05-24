---
id: "191ded42-ef97-4b43-a509-69567d82a8b3"
entity_type: "task"
entity_id: "50656e8b-7257-4728-b7e5-6bcdd8f42dde"
title: "实现 EnsureTenantAdminRoleStep 角色 ensure 与角色分配 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-151"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:49.50062+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现 `EnsureTenantAdminRoleStep`，确保平台预定义角色 `TENANT_ADMIN` 存在并已绑定到租户初始管理员。

## 实现思路

1. 通过 KeycloakAdminPort 的角色 ensure 方法：不存在则创建 `TENANT_ADMIN` Realm Role。
2. 通过 KeycloakAdminPort 的角色分配 ensure 方法：将 `TENANT_ADMIN` 绑定到 ExecutionContext 中的 adminUserId；已绑定视为成功。
3. 处理 409 Conflict 为已存在并继续。
4. 编写 Step 级单元测试覆盖五种幂等场景。

## 验收标准

- 首次执行创建角色与绑定关系。
- 重复执行幂等通过，无重复绑定。
- 409 Conflict 被识别。
- 单元测试覆盖以上场景。

## 技术约束

- Step 内部不出现 Keycloak SDK 类型。
- 仅处理平台预定义 `TENANT_ADMIN` 角色，不涉及租户自定义角色。
- 不配置 JWT Protocol Mapper（由后续阶段处理）。

## 范围边界

- **不**实现 KeycloakAdminPort 或 Fake Adapter（兄弟任务）。
- **不**涉及 Authorization BC 的 Role/RoleAssignment 聚合（兄弟任务）。
- **不**实现其他 Step、Pipeline 组合或 Service 编排。## Details

**Scope**: EnsureTenantAdminRoleStep 的实现与单元测试，覆盖角色创建与用户角色分配两个 ensure 调用

**Out of Scope**: 其他 Step、KeycloakAdminPort 接口、Fake Adapter、租户自定义角色 / Authorization BC 的 Role 聚合（兄弟任务）、JWT Protocol Mapper 配置

## Acceptance Criteria

- [ ] Step 首次执行时创建 `TENANT_ADMIN` Realm Role 并绑定到 adminUser
- [ ] 重复执行时识别角色与分配都已存在，不产生重复绑定，409 Conflict 被视为成功
- [ ] Step 仅通过 KeycloakAdminPort 调用外部能力，不出现 Keycloak SDK 类型
- [ ] 单元测试覆盖：角色不存在 / 角色已存在 / 用户未分配 / 用户已分配 / 409 冲突

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |

