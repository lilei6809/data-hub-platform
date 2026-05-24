---
id: "15830654-027c-431a-884d-108742e83435"
entity_type: "task"
entity_id: "40131d07-d700-4b53-99bc-acd3616a9c75"
title: "实现真实 Keycloak Organization Membership 操作 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-163"
parent_task_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:17:38.64263+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现真实 Keycloak Adapter 的 `ensureOrganizationMembership`，幂等地保证 user 属于指定 organization。

## 实施步骤

1. 在真实 adapter 中实现 `ensureOrganizationMembership(organizationId, userId)`。
2. 查询 user 是否已是 organization 成员（通过 Organizations API 列出成员或按 user 反查）。
3. 已是成员则直接返回成功；不是成员则调用 add member 接口添加。
4. 添加期间遇 409 视为已存在，按成功返回。
5. 编写单元测试覆盖：首次添加、已是成员、409 三种路径。

## 验收标准

- 不是成员时正确添加
- 已是成员时不重复调用
- 409 视为成功
- 单元测试覆盖三种路径

## 技术约束

- 幂等
- 409 等价 success## Details

**Scope**: ensureOrganizationMembership 方法的 lookup、add、冲突处理

**Out of Scope**: Organization 创建、User 创建、Role 分配；membership 除名语义

**Constraints**: 对重复调用完全幂等, 遇 409 视为成功而非错误, 不允许重复写入同一个 membership

## Acceptance Criteria

- [ ] 实现 `ensureOrganizationMembership(organizationId, userId)`，在 user 不属于 organization 时添加成员关系
- [ ] user 已是成员时方法返回成功，不调用 add 接口也不抛错
- [ ] 添加期间遇 409 Conflict 时视为成功，不向上抛冲突
- [ ] 单元测试验证：首次添加、重复添加、409 冲突三种路径都以成功返回收场

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

