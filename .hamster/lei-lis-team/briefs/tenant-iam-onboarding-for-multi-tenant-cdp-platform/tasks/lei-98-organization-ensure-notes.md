---
id: "2215217c-2bd1-4a0f-bdd6-003818f954d1"
entity_type: "task"
entity_id: "18f2b09f-9c07-40f0-b120-87aebf24a8ff"
title: "实现 Organization 的 ensure 与属性校正 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-98"
parent_task_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:41.767359+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

在真实 Keycloak Admin API 上实现 `ensureOrganization` 的幂等创建、复用与 attributes 校正。

## Implementation Approach

1. 实现 Port 的 `ensureOrganization(tenantId, attributes)`：先按 `tenant_id` attribute 查询 Organization。
2. 命中则返回已有 Organization 标识；未命中则调用 Keycloak Organizations API 创建。
3. 捕获创建过程中的 `409 Conflict` 分支：重新按 `tenant_id` 查询并复用结果。
4. 对 Organization Attributes 执行 diff：仅当实际属性与 desired 不一致时写入校正，避免重复无效更新。
5. 所有调用通过子任务 1 提供的 Admin Client 完成，不直接管理 token。

## Acceptance Criteria

- 首次执行创建 Organization 并写入 `tenant_id`、`tier`
- 重复执行不产生重复对象、不抛 409
- 并发 409 场景下回退到查询复用
- 属性差异可被检测并校正
- 查询基于 attribute 过滤，结果稳定

## Technical Constraints

- 必须使用 Shared Realm `cdp`
- 不得使用本地数据库事务包裹此远程调用
- 所有副作用必须可重复执行

## Code Patterns to Follow

- `ensure` 语义：查询 → 创建/复用 → 校正
- 409 Conflict 回退查询模式

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: 真实 Keycloak Adapter 中 Organization 的查询、创建、复用以及 attributes 校正逻辑；409 冲突下的重查复用

**Out of Scope**: Port 接口定义和 Fake Adapter（sibling 9b66bd20）；异常到领域错误的映射（单独子任务）；User/Membership/Role 操作（其他子任务）；Step 编排（sibling 4585bfc9）

## Acceptance Criteria

- [ ] 首次调用 `ensureOrganization` 在 Keycloak Shared Realm `cdp` 中创建 Organization，并写入 `tenant_id` 和 `tier` 属性
- [ ] 重复调用同一 `tenantId` 不会创建重复 Organization，也不会产生 409 错误向上抛
- [ ] 并发创建导致 Keycloak 返回 409 Conflict 时，适配器重新按 `tenantId` 查询并复用已有 Organization
- [ ] Organization 属性不一致时（例如 tier 变更）能被检测并校正为 desired 状态
- [ ] 查询使用稳定的检索方式（例如按 attribute `tenant_id` 过滤），避免依赖不稳定的分页顺序

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |
| skillReferences | Hamster Blueprint |

