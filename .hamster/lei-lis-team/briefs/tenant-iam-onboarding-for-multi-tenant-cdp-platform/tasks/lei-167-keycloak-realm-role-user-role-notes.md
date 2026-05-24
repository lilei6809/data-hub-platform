---
id: "4eb722df-87d0-4171-aeb4-bc04bc7a869d"
entity_type: "task"
entity_id: "5ceeb2d8-10a8-4b4e-b0c9-87901ac47011"
title: "实现真实 Keycloak Realm Role 与 User Role 分配操作 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-167"
parent_task_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:18:00.572224+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现真实 Keycloak Adapter 的 `ensureRealmRole` 与 `ensureUserRealmRole`，幂等地确保 realm role 存在及 user 拥有该 role。

## 实施步骤

1. 实现 `ensureRealmRole(roleName)`：先按 roleName 查询 realm role；命中则返回，未命中则创建；创建期间 409 退回查询。
2. 实现 `ensureUserRealmRole(userId, roleName)`：先查询 user 已分配 realm roles，命中则返回成功；未命中则调用 role-mappings 接口分配；分配期间 409 视为成功。
3. 编写单元测试，覆盖每个方法的三种路径（首次、已存在/已分配、409）。

## 验收标准

- 两方法实现并幂等
- 409 视为成功
- 单元测试覆盖每个方法的三种路径

## 技术约束

- 仅 realm role，不处理 client role
- ensure 语义## Details

**Scope**: ensureRealmRole 与 ensureUserRealmRole 两个方法的 lookup、create、assign、409 退回逻辑

**Out of Scope**: RoleName 领域校验（在领域模型任务）；Composite role、client role、Authorization BC 的 RoleAssignment 聚合

**Constraints**: ensure 语义, 仅处理 realm role，不处理 client role, 409 视为成功

## Acceptance Criteria

- [ ] 实现 `ensureRealmRole(roleName)`：不存在时创建，已存在时复用，创建遇 409 退回查询
- [ ] 实现 `ensureUserRealmRole(userId, roleName)`：user 尚未拥有时分配，已拥有时跳过，分配遇 409 视为成功
- [ ] 两个方法重复调用不产生重复 role 或重复分配
- [ ] 单元测试为每个方法覆盖：首次创建/分配、已存在/已分配、409 三种路径

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

