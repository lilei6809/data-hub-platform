---
id: "9e17509e-d400-45bd-84f7-ac6ae65cea50"
entity_type: "task"
entity_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
title: "真实 Keycloak Admin API Adapter 可承接生产 onboarding - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-75"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:53:18.36739+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 平台运维人员可以在真实 Keycloak 上完成租户 IAM onboarding。

把 MVP 阶段使用的 Fake Adapter 替换为真实 Keycloak Admin API 适配器，让同一套 Step Pipeline 与状态机不修改即可对接生产 Keycloak。

## Experience

平台运维人员得到一个 `RealKeycloakAdapter`：对接 Shared Realm `cdp`，完整覆盖 Organization、User、Membership、Realm Role、Role Assignment 的 ensure 语义，并把 409 / 404 / 5xx / 超时映射为领域错误。Port 契约测试与 Fake Adapter 共享，确保行为对齐。

## Interaction

1. 配置真实 Keycloak 连接与服务账户（凭证来自 SecretStorePort）。
2. Provisioning Service 仍调用 `KeycloakAdminPort`，无需感知 Adapter 切换。
3. 真实 Adapter 在 Testcontainers 或预发 Keycloak 上端到端通过 Port 契约测试与 onboarding 场景测试。
4. 任何 Keycloak SDK 异常被映射为受限的领域错误，绝不向上层泄漏。## Details

**User Capability**: 平台运维人员可以把 Fake Adapter 替换为真实 Keycloak Admin API 适配器，在 Shared Realm `cdp` 中完成 Organization、User、Membership、Realm Role、Role Assignment 的创建与复用，并能正确处理常见外部异常。

**Business Value**: 让 MVP 验证过的 onboarding 流程能在真实 Keycloak 上落地，同时保持领域层不感知 SDK 细节。

**Functional Requirements**:
- 实现 `RealKeycloakAdapter implements KeycloakAdminPort`，对接真实 Keycloak Admin API：
  - Organization 创建、查询、attributes 写入与校正。
  - User 创建（含 temporary credential policy）、按 email 查询。
  - Organization membership 创建与查询。
  - Realm Role 创建与查询。
  - 用户角色分配与查询。
- 异常映射策略：
  - `409 Conflict` → 查询已有对象后继续，对外返回 REUSED。
  - `404 Not Found`、`5xx`、网络超时 → 映射为受限的领域错误类型。
  - 任何 Keycloak SDK 异常不得向上层泄漏。
- 必须通过共享 Port 契约测试（与 Fake Adapter 同一套）。
- Keycloak Admin credentials 通过 `SecretStorePort` 获取（依赖 Phase 4 任务的 Port 抽象；本任务可先使用配置注入，但不能写死）。
- 与 Shared Realm `cdp`、Organization-per-Tenant 的架构决策对齐。

**Data Model & Structure**:
- 复用已有领域类型；不引入新的领域模型。
- Adapter 内部可使用 DTO 对接 Keycloak API，仅在 Adapter 边界出现。

**Technical Approach**:
- 使用官方 Keycloak Admin Client SDK 或 REST 调用，二选一并在任务详情中固定。
- 实现连接池、超时、重试（仅对幂等读操作）。
- 严格区分"创建-冲突回退-查询"与"直接查询"两条路径，保证 ensure 语义。

**Scope - INCLUDED**:
- 真实 Adapter 实现与配置。
- 异常映射与日志（含 `tenantId`、`correlationId`，不含 secret）。
- 与 Fake Adapter 共享的 Port 契约测试在真实 Keycloak（或 Testcontainers）上通过。

**Scope - EXCLUDED**:
- Vault 接入（Phase 4 任务，本任务通过 SecretStorePort 调用）。
- BYO IdP / MFA / Dedicated Realm（Phase 4 任务）。
- Kafka 事件接入（Phase 3 任务）。
- Step Pipeline 与 Service 修改（依赖任务已完成）。

**Success Criteria**:
- 在 Keycloak Testcontainers 环境中端到端 onboarding 通过。
- 重复执行不产生重复对象；中途失败重试可继续。
- Adapter 对外抛出的全部异常均为领域错误类型。

**Constraints & Considerations**:
- 严禁在日志中打印 Keycloak service account secret、临时密码。
- 严禁把 Keycloak 调用包裹在本地数据库事务中。
- 调用必须始终承载 `correlationId`（透传到日志/请求头）。

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | Keycloak Admin Port 与 Fake Adapter 可隔离外部身份系统, Tenant IAM Provisioning Service 与本地状态机可端到端编排 onboarding |

