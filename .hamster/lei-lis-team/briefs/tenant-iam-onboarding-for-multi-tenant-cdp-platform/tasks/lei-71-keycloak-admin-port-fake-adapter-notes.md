---
id: "de12c755-6a01-446a-b096-fddee649d3b0"
entity_type: "task"
entity_id: "9b66bd20-cdbf-415d-98d9-cdd9e7abced2"
title: "Keycloak Admin Port 与 Fake Adapter 可隔离外部身份系统 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-71"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:51:31.247123+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 平台开发者可以通过意图型 Port 调用 Keycloak，并用 Fake Adapter 验证幂等语义。

把 Keycloak 隔离在应用核心之外，让领域用例只表达"需要 Keycloak 完成的事情"，而不绑定 SDK、API 版本和冲突处理细节。MVP 阶段使用内存 Fake Adapter 就能完整验证 onboarding 闭环。

## Experience

平台开发者得到一个意图型 `KeycloakAdminPort`，第一版方法围绕 `ensureOrganization`、`ensureUser`、`ensureOrganizationMembership`、`ensureRealmRole`、`ensureUserRealmRole`，全部采用 ensure 语义。配套的 `InMemoryKeycloakAdapter` 用内存数据模拟 Keycloak，可被配置返回 409 冲突或抛出受控异常，让 ensure 语义、冲突回退和失败路径都可以被测试。

## Interaction

1. 应用核心通过 Port 调用 Keycloak 能力，不引用 SDK 类型。
2. Fake Adapter 模拟真实 Keycloak 行为（存在/冲突/异常）。
3. Port 契约测试套件同时约束 Fake Adapter 与未来真实 Adapter。
4. 任何外部异常都被映射为领域错误类型后再上抛。## Details

**User Capability**: 平台开发者可以在应用核心中通过 `KeycloakAdminPort` 表达"需要 Keycloak 完成的事情"，并在测试与本地运行环境中使用一个内存 Fake Adapter 模拟 Keycloak 行为（包括幂等返回与冲突处理），不依赖任何真实 Keycloak。

**Business Value**: 把领域用例与 Keycloak 具体 SDK、API 版本、分页、异常类型、冲突处理隔离开，使核心 onboarding 流程可测试、可替换、可演进；同时让 MVP 闭环可在没有真实 Keycloak 的前提下被完整验证。

**Functional Requirements**:
- 定义 `KeycloakAdminPort` 接口，第一版方法：
  - `ensureOrganization(tenantId, attributes) -> OrganizationId`
  - `ensureUser(email, temporaryCredentialPolicy) -> UserId`
  - `ensureOrganizationMembership(organizationId, userId)`
  - `ensureRealmRole(roleName)`
  - `ensureUserRealmRole(userId, roleName)`
- 所有方法采用 `ensure` 语义：不存在则创建，存在则复用，关系已存在视为成功。
- Port 必须为未来扩展方法预留接口风格一致性：`ensureIdentityProvider`、`ensureProtocolMapper`、`ensureClientAudience`、`ensureMfaPolicy`（MVP 不实现，仅在文档/注释中标记）。
- 提供 `InMemoryKeycloakAdapter`（Fake）：
  - 用内存数据结构模拟 Organization、User、Membership、Role、Role Assignment。
  - 必须能模拟"对象已存在"场景，使 ensure 语义可被测试。
  - 必须能模拟"创建时返回 409 Conflict 然后查询已有对象"路径。
  - 必须能被注入式地配置成在某个方法上抛出可控异常（用于失败路径测试）。
- Port 抛出的异常必须是领域层定义的错误类型（如 `KeycloakOperationException`），不允许向上层泄漏 Keycloak SDK 异常类型。

**Data Model & Structure**:
- `OrganizationId`、`UserId` 值对象。
- `OrganizationAttributes(tenantId, tier)`。
- `TemporaryCredentialPolicy` 值对象（最小定义即可）。

**Technical Approach**:
- Ports and Adapters：Port 在领域/应用层，Adapter 在 infrastructure 层。
- Fake Adapter 与未来真实 Keycloak Adapter 共享同一个 Port 契约测试套件（contract tests），以便 Phase 2 的真实 Adapter 可以直接对齐验收。

**Scope - INCLUDED**:
- Port 接口定义。
- Fake/In-Memory Adapter 实现。
- Port 契约测试套件。
- 领域错误类型定义。

**Scope - EXCLUDED**:
- 真实 Keycloak Admin API 调用（由"Real Keycloak Admin API 集成"任务负责）。
- Step Pipeline 编排（由"Idempotent Step Pipeline"任务负责）。
- 状态机与本地持久化（由"Provisioning Service 与状态机"任务负责）。
- IdP / MFA / Dedicated Realm 行为（Phase 4 任务）。

**Success Criteria**:
- 应用核心代码中不出现任何 Keycloak SDK 类型。
- Fake Adapter 在重复调用同一 `ensureXxx` 时返回稳定结果，不创建重复对象。
- Port 契约测试可独立于真实 Keycloak 运行并全部通过。

**Constraints & Considerations**:
- 严禁把 Keycloak service account secret 写入日志。
- Adapter 异常映射必须覆盖 409 / 404 / 5xx / 超时四类语义。

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | Tenant IAM Desired State 领域模型可表达租户身份事实 |

