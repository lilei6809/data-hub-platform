---
id: "024d063f-71e0-4591-bb8d-ff86b7094825"
entity_type: "task"
entity_id: "20bbf70c-0891-4704-9312-b6a6f5c1871f"
title: "实现 EnsureTenantAdminRoleStep 完成角色 ensure 与用户绑定 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-116"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:44.023+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 2026-05-26 Step/Adapter 边界调整

`TENANT_ADMIN` 当前按平台预置 Realm Role 处理。Tenant provisioning pipeline 不负责创建平台级 Realm Role。

当前 Step 语义调整为：

- `EnsureTenantAdminRoleStep` 只调用 `ensureUserRealmRole(adminUserId, TENANT_ADMIN)`。
- `TENANT_ADMIN` 是否存在由平台 bootstrap/realm import 保证。
- 如果真实 Keycloak 中缺少 `TENANT_ADMIN`，Adapter 应抛出非重试配置错误，而不是在租户 onboarding 中创建角色。
- 角色绑定已存在、绑定时 409、重复绑定 no-op 都由 Adapter 在 `ensureUserRealmRole(...)` 内处理。

## 摘要

实现 Pipeline 中负责 `TENANT_ADMIN` Realm Role 的 Ensure 步骤，包含角色存在性与用户绑定两次 ensure 调用。

## 实现要点

- 调用 `KeycloakAdminPort.ensureUserRealmRole(adminUserId, "TENANT_ADMIN")` 保证用户拥有平台预置角色。
- 不在 tenant provisioning 中调用 `ensureRealmRole("TENANT_ADMIN")` 创建平台角色。
- 409、已存在绑定均由 Adapter 按 ensure 语义视为成功。
- 角色名作为常量或枚举显式表达，遵循 `RoleName` 大写+下划线约束。

## 验收标准

- 首次执行完成用户与预置 RealmRole 的绑定。
- 重复执行不产生重复创建或重复绑定。
- 409 不泄漏到 Step；Adapter 应按 Port 契约消解。
- `TENANT_ADMIN` 名称在代码中具有单一来源。

## 技术约束

- 仅消费 `KeycloakAdminPort`，不直接调用 SDK。
- 不处理租户自定义角色（这属于 Authorization BC 的 Role 聚合，由 sibling 任务负责）。

## 范围边界

- **包含**：EnsureTenantAdminRoleStep 的全部实现。
- **不包含**：Port 接口与实现、其他 Ensure 步骤、自定义角色、Authorization BC 聚合、Pipeline 组装、状态/事件步骤。## Details

**Scope**: EnsureTenantAdminRoleStep 的实现，包括 Realm Role ensure 与 User-Role 绑定 ensure 两部分

**Out of Scope**: KeycloakAdminPort 接口定义与实现、其他 Ensure 步骤、租户自定义角色、Authorization BC 的 Role 聚合、JWT 中的角色映射、Pipeline 组装

## Acceptance Criteria

- [ ] 步骤调用 `ensureUserRealmRole(adminUserId, "TENANT_ADMIN")`，使用 KeycloakAdminPort 分配平台预置角色
- [ ] 绑定已存在时步骤返回成功，不产生异常
- [ ] Keycloak 返回 409 Conflict 时由 Adapter 查询已有绑定后继续；Step 不包含 409 分支
- [ ] 重复执行不会产生重复的 RealmRole 创建请求或重复的角色绑定
- [ ] `TENANT_ADMIN` 作为平台规定的角色名常量或枚举被显式表达，遵循大写+下划线命名规则

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |
| skillReferences | Hamster Blueprint |
