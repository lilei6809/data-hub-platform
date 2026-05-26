---
id: "9cc5545a-68ef-46bf-89ed-391db9d5d797"
entity_type: "task"
entity_id: "8c75479f-d104-488f-8e31-fc15c1e6ab96"
title: "Tenant IAM Desired State 领域模型可表达租户身份事实 - Notes"
status: "done"
priority: "high"
display_id: "LEI-70"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:18:31.284893+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 平台开发者可以用稳定的领域模型描述租户期望拥有的 IAM 状态。

这是 Tenant IAM Onboarding 的领域基石。把"租户希望在身份系统中拥有的事实"建模为不可变值对象，后续幂等步骤、状态机、事件契约和真实 Keycloak 适配都以此为锚点演进，而不是把请求直接翻译成 Keycloak Admin API 调用。

## Experience

平台开发者得到一组无外部依赖的领域类型：`TenantIamDesiredState`、`AdminUser`、`IdentityMode`、`RealmStrategy`，以及 `TenantId`、`Email`、`RoleName`、`Permission` 等值对象。模型在构造期完成校验，非法输入立即被拒绝，第一版的默认值（`LOCAL_ONLY` / `SHARED_REALM` / 空 IdP / 空 policy）让最小输入仅需 `tenantId + tier + adminEmail`。

## Interaction

1. 用例层根据上游输入构造 `TenantIamDesiredState`。
2. 模型在构造期校验所有值对象（slug 格式、email、role 命名）。
3. 后续 Provisioning Service、Step Pipeline、事件映射都以该对象为唯一输入。
4. 未来 BYO IdP / MFA / Dedicated Realm 通过填充扩展字段实现，而不需要改造主流程。## Details

**User Capability**: 平台开发者可以构造一个 `TenantIamDesiredState` 值对象集合，描述某个租户期望在 Keycloak 中拥有的身份事实（Organization、Admin User、Role 等），并把它作为 onboarding 用例的唯一输入。

**Business Value**: 把"租户期望的 IAM 状态"建模为稳定领域对象，让后续幂等步骤、状态机、事件契约、真实 Keycloak Adapter、BYO IdP 扩展都以同一个模型为锚点演进，避免把请求直接映射为 Keycloak Admin API 调用。

**Functional Requirements**:
- 提供 `TenantIamDesiredState` 聚合根/值对象，至少包含：`tenantId`、`tier`、`adminUser`、`identityMode`、`realmStrategy`、`identityProviders`、`policies`。
- 提供 `AdminUser` 值对象，至少包含 `email` 和 `temporaryCredentialPolicy`。
- 提供枚举：`IdentityMode { LOCAL_ONLY, BROKERED_IDP }`、`RealmStrategy { SHARED_REALM, DEDICATED_REALM }`。
- 第一版默认值：`identityMode = LOCAL_ONLY`、`realmStrategy = SHARED_REALM`、`identityProviders = []`、`policies = []`。
- 值对象约束：`TenantId` 使用稳定 slug（如 `tenant-acme-corp`）；`RoleName` 使用大写下划线（如 `TENANT_ADMIN`、`REGIONAL_STEWARD`）；`Permission` 使用 `resource:action` 格式。
- 模型为不可变；非法输入（空 tenantId、非法 email、非法 slug 等）必须在构造期拒绝并抛出领域异常。
- 模型必须为后续扩展字段预留扩展点（identityProviders / policies），但 MVP 不实现这些扩展的行为。

**Data Model & Structure**:
- `TenantIamDesiredState(tenantId, tier, adminUser, identityMode, realmStrategy, identityProviders, policies)`
- `AdminUser(email, temporaryCredentialPolicy)`
- 枚举：`IdentityMode`、`RealmStrategy`
- 值对象：`TenantId`、`Tier`、`Email`、`RoleName`、`Permission`

**Technical Approach**:
- 采用纯领域层（无 Spring、无 Keycloak、无 Kafka 依赖）。
- Ports and Adapters 架构中的"内圈"。
- 不可变对象 + 工厂方法 / 静态构造器执行校验。

**User Workflows**:
- 平台开发者在用例层调用 `TenantIamDesiredState.of(...)` 构造期望状态。
- 后续 `TenantIamProvisioningService` 接收该对象作为输入。
- 后续事件 `TenantInfrastructureProvisionedEvent` 可以被映射成 `TenantIamDesiredState`。

**Scope - INCLUDED**:
- 上述领域模型、值对象、枚举的定义与构造校验。
- 领域单元测试覆盖构造校验与默认值。

**Scope - EXCLUDED**:
- Step Pipeline 执行逻辑（由"幂等 Step Pipeline"任务负责）。
- Keycloak Port 与 Adapter（由"Keycloak Admin Port 与 Fake Adapter"任务负责）。
- 本地状态机持久化（由"Provisioning Service 与状态机"任务负责）。
- 事件 schema 与发布（由"事件边界契约"任务负责）。
- BYO IdP / MFA / Dedicated Realm 的具体行为（Phase 4 任务）。

**Success Criteria**:
- 可以通过构造器创建一个仅含 `tenantId + tier + adminEmail` 的 `TenantIamDesiredState`，其余字段使用第一版默认值。
- 非法输入被构造期拒绝。
- 单元测试覆盖默认值、校验、值对象等价性。

**Constraints & Considerations**:
- 不引入对 Keycloak、Spring、Kafka 的依赖。
- 字段命名与 PRD 一致，避免后续重命名传播。
