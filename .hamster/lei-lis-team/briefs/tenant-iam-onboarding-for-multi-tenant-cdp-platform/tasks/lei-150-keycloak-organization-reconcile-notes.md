---
id: "f4766f03-2e6a-452a-ab87-9cdfb95c8da6"
entity_type: "task"
entity_id: "dc9118f3-55c3-4b55-a0a4-9deed03065bf"
title: "实现真实 Keycloak Organization 操作（创建、查询、属性 reconcile） - Notes"
status: "todo"
priority: "high"
display_id: "LEI-150"
parent_task_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:45.054944+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 2026-05-26 Adapter 职责确认

本任务是 409 fallback 与属性 reconcile 的正确归属位置。`ensureOrganization(...)` 的真实 Keycloak 实现必须在 Adapter 内完成：

- lookup by tenant identity
- create when missing
- create 409 后再次 lookup 并复用
- attributes 不一致时按 Desired State 校正
- SDK/HTTP 异常翻译为端口级 `KeycloakOperationException` 子类型

Step 不实现上述细节，只调用 `KeycloakAdminPort.ensureOrganization(...)`。

## 摘要

实现真实 Keycloak Adapter 的 `ensureOrganization` 能力，覆盖 Shared Realm `cdp` 下 Organization 的幂等创建、按 tenantId 查询和 `tenant_id`/`tier` 属性 reconciliation。

## 实施步骤

1. 在真实 adapter 实现类中实现 `ensureOrganization(tenantId, attributes)` 方法。
2. 优先按 `tenant_id` 属性在 realm `cdp` 下查询 Organization；命中则进入属性 reconcile 分支。
3. 未命中时创建 Organization，写入 `tenant_id` 与 `tier` 属性；若创建抛出 409 Conflict，则退回查询并复用。
4. 属性 reconcile：对每个 desired attribute，缺失则写入，值不一致则按 desired state 覆盖，并记录调试日志（不打印敏感信息）。
5. 编写针对 mock SDK 客户端的单元测试，覆盖：首次创建、已存在复用、属性需要校正、创建期间 409 退回查询四种路径。

## 验收标准

- `ensureOrganization` 完整实现且对重复调用幂等
- 首次创建写入 `tenant_id` 和 `tier`
- 已存在时按 `tenant_id` 复用并 reconcile 属性差异
- 409 Conflict 回退查询而非抛出
- 单元测试覆盖四种路径

## 技术约束

- ensure 语义，不允许 create-only 行为
- 属性必须包含 `tenant_id` 与 `tier`，且与 desired state 保持一致
- 异常应由 Adapter 翻译为端口级异常；不得向 Application Service 或 Step 泄漏原始 SDK 异常

## 代码模式

- ensure 步骤模式：先 lookup 再 create，create 期间冲突时再 lookup 复用## Details

**Scope**: Organization create/lookup-by-tenantId/attributes ensure 三个能力，以及在 `ensureOrganization` Port 方法下的完整实现

**Out of Scope**: ensureUser、ensureOrganizationMembership、ensureRealmRole、ensureUserRealmRole与异常映射详细实现（各自独立子任务）

**Constraints**: 使用 ensure 语义，重复调用不产生重复 Organization, Organization 必须在 Shared Realm `cdp` 下创建, Organization attributes 必须包含 `tenant_id` 和 `tier`，与 desired state 不一致时以 desired state 为准

## Acceptance Criteria

- [ ] 实现 `ensureOrganization(tenantId, attributes)`：Organization 不存在时创建并写入 `tenant_id`、`tier` 属性，返回其 Keycloak organization id
- [ ] 已存在时（通过 `tenant_id` 定位）不重复创建，返回现有 organization id，同请求重复调用结果一致
- [ ] 属性与 desired state 不一致时能按 desired state 校正（缺失则补齐，值不一致则覆写）并记录校正动作
- [ ] 在创建期间遇到 409 Conflict 时，实现能退回查询并复用已有对象，不向上抛冲突
- [ ] 提供针对真实实现的单元测试（可使用 mock SDK 客户端）验证上述四种路径

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 7 |
