---
id: "5a184fff-3769-46a3-b527-7cddd52edaa4"
entity_type: "task"
entity_id: "84d4e472-cfee-4df6-b6b2-9addc7603ebf"
title: "SecretStore 与企业 IAM 扩展点可承接 BYO IdP 与 MFA - Notes"
status: "todo"
priority: "low"
display_id: "LEI-77"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:54:05.546259+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 平台运维人员可以集中管理凭证，并扩展 BYO IdP 与 MFA 能力。

把敏感凭证迁移到 SecretStore，并通过扩展 Step 与 Port 方法承接企业级 IAM 场景（BYO IdP、MFA、Dedicated Realm 扩展点），整个过程不动 MVP 主流程。

## Experience

平台运维人员得到 `SecretStorePort` 与 Vault Adapter；平台开发者得到扩展的 `KeycloakAdminPort` 方法（IdentityProvider、ProtocolMapper、ClientAudience、MfaPolicy）与新增 Step（`ConfigureIdentityProviderStep`、`EnsureMfaPolicyStep`）。`identityMode = BROKERED_IDP` 与 `policies` 中的 MFA 策略现在会被真实驱动，Dedicated Realm 留有清晰的扩展点。

## Interaction

1. 平台凭证从配置迁移到 SecretStore，所有 Adapter 通过 Port 获取。
2. 携带 IdP / MFA 的 Desired State 触发对应 Step。
3. 新 Step 仍走 ensure 语义，重复执行安全。
4. 任何 secret 都不出现在日志 / 事件 / 异常文本中。## Details

**User Capability**: 平台运维人员可以使用 `SecretStorePort` 在 Vault（或等价系统）中管理 Keycloak Admin、外部 IdP secret 等敏感凭证，平台开发者可以在不重写主流程的前提下扩展 BYO IdP、MFA 策略以及为 Dedicated Realm 策略预留扩展点。

**Business Value**: 把企业级 IAM 能力（BYO IdP、MFA、Dedicated Realm）作为扩展点引入，不污染 MVP 主流程，同时把敏感凭证从配置文件迁移到集中式 SecretStore。

**Functional Requirements**:
- 定义 `SecretStorePort`：`getSecret(reference) -> SecretValue`，并提供 Vault Adapter 实现以及 In-Memory/Test Adapter。
- 把 Keycloak Admin credentials、外部 IdP secret 通过 `SecretStorePort` 提供，禁止配置明文写死。
- 扩展 `KeycloakAdminPort` 方法并实现：
  - `ensureIdentityProvider(tenantId, idpConfig)`
  - `ensureProtocolMapper(clientId, mapperConfig)`
  - `ensureClientAudience(clientId, audience)`
  - `ensureMfaPolicy(tenantId, policy)`
- 扩展 Desired State 模型实际行为：`identityMode = BROKERED_IDP` 时驱动 IdP 配置；`policies` 中的 MFA 策略驱动 MFA Step。
- 实现新 Step：`ConfigureIdentityProviderStep`、`EnsureMfaPolicyStep`。
- 为 `realmStrategy = DEDICATED_REALM` 预留扩展点（接口与文档即可，无需完整实现）。
- 任何 secret 不得写入日志、事件 payload 或异常 message。

**Data Model & Structure**:
- `SecretReference`、`SecretValue`（自动擦除 / `toString` 屏蔽）。
- `IdentityProviderConfig`、`MfaPolicy`、`ProtocolMapperConfig`、`ClientAudienceConfig`。

**Technical Approach**:
- Vault Adapter 通过 AppRole 或等价机制鉴权。
- Step 仍遵循 ensure 语义；MFA / IdP 配置变更通过校正模式收敛。

**Scope - INCLUDED**:
- `SecretStorePort` 抽象 + Vault Adapter + Test Adapter。
- Keycloak Port 扩展方法实现。
- 新增 Step：IdentityProvider、MFA。
- Dedicated Realm 扩展点（接口/文档）。

**Scope - EXCLUDED**:
- Dedicated Realm 的完整运行实现（仍属未来工作）。
- Authorization BC 的策略评估（"Authorization Service PDP"任务）。
- Tenant Context 传播（独立任务）。

**Success Criteria**:
- BYO IdP onboarding 测试场景通过：租户携带 IdP 配置时被正确创建并幂等。
- MFA 策略可以被 ensure 到 Keycloak 上；重复执行无副作用。
- 所有敏感凭证只通过 `SecretStorePort` 获取，源代码与配置中不存在明文。

**Constraints & Considerations**:
- `SecretValue` 必须屏蔽默认序列化 / toString。
- 任何企业扩展都不得改变 MVP 路径下的行为。

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | 真实 Keycloak Admin API Adapter 可承接生产 onboarding |

