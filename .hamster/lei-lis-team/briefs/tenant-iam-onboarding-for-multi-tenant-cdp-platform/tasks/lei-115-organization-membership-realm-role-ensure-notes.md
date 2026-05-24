---
id: "5e212530-a3da-4640-b86e-bc7fc239e22e"
entity_type: "task"
entity_id: "2be1a6e2-71f0-4f9b-b753-9101a4142d89"
title: "实现 Organization Membership 与 Realm Role 的 ensure 操作 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-115"
parent_task_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:40.949865+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

在真实 Keycloak Admin API 上实现 Organization Membership、Realm Role 创建以及用户 Realm Role 分配三个 ensure 操作。

## Implementation Approach

1. `ensureOrganizationMembership(organizationId, userId)`：调用 Organizations Members API；若返回 409 或类似 "already member"，回退查询成员关系并视为成功。
2. `ensureRealmRole(roleName)`：先按 `roleName` 查询 realm role；不存在则创建；创建 409 则回退查询。
3. `ensureUserRealmRole(userId, roleName)`：先获取用户已分配的 realm roles；包含则直接成功；否则按 Keycloak `role-mappings/realm` API 添加。
4. 三个方法统一返回 void 或语义化结果；不向上抛出 "已存在" 类异常。

## Acceptance Criteria

- Membership 幂等：首次加入、重复加入均成功
- Realm Role 幂等：首次创建、重复 ensure 均成功
- 用户角色绑定幂等：首次绑定、重复绑定均成功
- 409/204/静默成功统一收敛为 ensure 成功
- 集成验证表明三者可安全重试

## Technical Constraints

- 三个操作不得共用本地事务
- 不在此任务处理 composite role、client role、group 联动

## Code Patterns to Follow

- `ensure` 语义
- 409 回退查询统一收敛

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: 真实 Adapter 中 Organization Membership、Realm Role 创建、用户 Realm Role 分配三个操作的幂等实现

**Out of Scope**: Port 接口与 Fake Adapter（sibling 9b66bd20）；Organization 与 User 本身的 ensure（前面子任务）；Step 编排（sibling 4585bfc9）；未来的 Composite Role、Client Role、Group 联动

## Acceptance Criteria

- [ ] `ensureOrganizationMembership` 首次将 userId 加入 organizationId；重复调用不产生错误也不产生重复成员事实
- [ ] `ensureRealmRole` 首次创建 `TENANT_ADMIN` Realm Role；重复调用复用已有角色，不抛 409
- [ ] `ensureUserRealmRole` 首次将 Realm Role 绑定到用户；重复调用保持成功且不重复写入
- [ ] 三个操作在 Keycloak 返回 409 或 204、或幂等跳过时都被统一收敛为“ensure 成功”
- [ ] 三个操作都能在集成环境中被验证为安全可重试

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |
| skillReferences | Hamster Blueprint |

