<context>
# Overview

本 PRD 定义 Identity & Access Layer（IAL）中第一阶段要交付的 **Tenant IAM Onboarding** 能力。

IAL 服务于 SaaS Multi-Tenant CDP 数据源管理平台，负责认证、租户身份隔离、授权上下文传播、外部 IdP 接入边界，以及租户生命周期中的 IAM 初始化。

当前阶段的核心问题不是“调用 Keycloak 创建几个对象”，而是建立一个可扩展、可重试、可验证的租户身份初始化流程，使新租户在平台中拥有稳定的身份事实：

- 租户在 Keycloak Shared Realm `cdp` 中有对应 Organization。
- Organization 携带 `tenant_id` 和 `tier` 属性。
- 初始租户管理员用户存在。
- 初始租户管理员属于该 Organization。
- 初始租户管理员拥有 `TENANT_ADMIN` 角色。
- 本地系统记录 IAM provisioning 状态。

该能力的价值是让后续认证授权链路拥有可靠前提：Keycloak 能签发带 `tenant_id` 和 `roles` 的 JWT，Envoy Gateway 能验证并注入可信 Header，下游 Spring Boot 服务能基于租户上下文执行 ABAC。

# Core Features

## 1. Tenant IAM Desired State

系统需要用一个稳定的领域模型描述租户期望拥有的 IAM 状态，而不是把请求直接映射为 Keycloak Admin API 调用。

第一版最小输入：

- `tenantId`
- `tier`
- `adminEmail`

模型需要预留未来扩展字段：

- `identityMode`：第一版默认 `LOCAL_ONLY`，未来支持 `BROKERED_IDP`。
- `realmStrategy`：第一版默认 `SHARED_REALM`，未来支持 `DEDICATED_REALM`。
- `identityProviders`：第一版为空，未来支持 Okta、Azure AD、SAML。
- `policies`：第一版为空，未来支持 MFA、密码策略、登录策略。

这个特性重要，因为 SaaS IAM onboarding 不是单次动作，而是把业务租户映射成身份系统中的持久事实。模型先稳定，后续功能才能通过扩展字段和步骤演进，而不是重写主流程。

## 2. Idempotent Provisioning Step Pipeline

系统需要把 onboarding 实现为可组合的 Step Pipeline。每个步骤都必须采用 `ensure` 语义，而不是 `create` 语义。

第一版步骤：

- `EnsureOrganizationStep`
- `EnsureOrganizationAttributesStep`
- `EnsureAdminUserStep`
- `EnsureOrganizationMembershipStep`
- `EnsureTenantAdminRoleStep`
- `MarkIamProvisionedStep`
- `PublishTenantIamProvisionedEventStep`

每个步骤需要满足：

- 目标对象不存在时创建。
- 目标对象已存在时复用。
- 目标关系已存在时视为成功。
- 属性不一致时按规则校正。
- Keycloak 返回 `409 Conflict` 时查询已有对象并继续。

这个特性重要，因为租户 onboarding 可能因网络、Keycloak 短暂故障、本地状态写入失败或事件重复投递而重试。系统必须通过幂等 reconciliation 保证重复执行安全。

## 3. Keycloak Admin Port

应用核心不能直接依赖 Keycloak SDK。系统需要定义 `KeycloakAdminPort`，用意图型方法表达平台需要 Keycloak 完成的事情。

第一版 Port 方法：

- `ensureOrganization(tenantId, attributes)`
- `ensureUser(email, temporaryCredentialPolicy)`
- `ensureOrganizationMembership(organizationId, userId)`
- `ensureRealmRole(roleName)`
- `ensureUserRealmRole(userId, roleName)`

未来扩展方法：

- `ensureIdentityProvider(tenantId, idpConfig)`
- `ensureProtocolMapper(clientId, mapperConfig)`
- `ensureClientAudience(clientId, audience)`
- `ensureMfaPolicy(tenantId, policy)`

这个特性重要，因为它把领域用例与 Keycloak 具体 API、SDK 版本、异常类型、分页查询、冲突处理隔离开，使核心流程可测试、可替换、可演进。

## 4. Local Provisioning State

系统需要维护租户 IAM provisioning 状态，不能只使用布尔字段。

第一版状态：

- `PENDING_IAM`
- `IAM_PROVISIONING`
- `IAM_PROVISIONED`
- `IAM_FAILED`

状态记录应包含：

- `tenantId`
- `iamStatus`
- `lastAttemptAt`
- `provisionedAt`
- `failureCode`
- `failureMessage`
- `retryCount`
- `workflowCorrelationId`

这个特性重要，因为远程 Keycloak 操作不参与本地数据库事务。系统必须通过显式状态机表达当前进度、失败原因和可重试性。

## 5. Event Boundary

长期目标是事件驱动 onboarding。IAM provisioning 由 `TenantInfrastructureProvisionedEvent` 触发，成功后发布 `TenantIamProvisionedEvent`，失败后发布 `TenantIamProvisioningFailedEvent`。

第一版可以先通过本地 runner 或测试直接调用 `TenantIamProvisioningService`，但领域模型和应用服务边界必须按事件驱动方式设计。

这个特性重要，因为 IAL 不是孤立功能。租户生命周期还涉及基础设施、Connector Registry、DataSource、Connection Governance 等上下游上下文，事件边界能避免服务之间直接耦合。

## 6. Verification Scenarios

第一版必须能验证：

- 第一次执行 onboarding 会创建 Organization、Admin User、Membership、Role Assignment。
- 第二次执行同一个 onboarding 请求不会创建重复对象，也不会因为冲突失败。
- Organization 创建成功后流程失败，重试能从已有 Organization 继续。
- Organization 包含正确的 `tenant_id` 和 `tier`。
- 初始管理员可以通过 email 查询到。
- 初始管理员属于目标 Organization。
- 初始管理员拥有 `TENANT_ADMIN`。
- 本地状态最终变为 `IAM_PROVISIONED`。

这些验证重要，因为本项目的目标不仅是完成 IAL 功能，还要建立对 SaaS 多租户认证授权的知识体系。每个重要行为都应能通过最小场景被观察和解释。

# User Experience

## User Personas

- 平台开发者：实现 IAL onboarding 逻辑，需要清楚边界、状态、幂等规则和测试方式。
- 平台管理员：需要新租户创建后自动拥有可登录的初始租户管理员。
- 租户管理员：收到平台提供的初始账号后，可以登录并管理本租户资源。
- 后续系统集成方：依赖 `TenantIamProvisionedEvent` 继续执行租户激活、连接器初始化或数据源治理初始化。

## Key User Flows

### Flow 1: 新租户 IAM 初始化

1. 上游租户基础设施流程完成，产生 `TenantInfrastructureProvisionedEvent`。
2. IAL 将事件转换为 `TenantIamDesiredState`。
3. `TenantIamProvisioningService` 标记本地状态为 `IAM_PROVISIONING`。
4. Step Pipeline 逐步 reconcile Keycloak 与本地状态。
5. 成功后本地状态变为 `IAM_PROVISIONED`。
6. 系统发布 `TenantIamProvisionedEvent`。

### Flow 2: 重复事件或重试

1. 系统再次收到同一租户 onboarding 请求。
2. 每个 `ensure` Step 检查已有对象和关系。
3. 已存在的 Organization、User、Membership、Role Assignment 被复用。
4. 流程继续完成，不创建重复对象。
5. 本地状态保持或更新为 `IAM_PROVISIONED`。

### Flow 3: 中途失败后恢复

1. Organization 创建成功。
2. Admin User 创建或角色分配阶段失败。
3. 系统记录 `IAM_FAILED`、失败原因和 retry count。
4. 重试时从 Desired State 重新 reconcile。
5. 已存在的 Organization 被识别并复用。
6. 剩余步骤继续执行直到完成。

## UI/UX Considerations

第一版不要求实现管理 UI。对开发者和运维人员的可观察性要求更重要：

- 日志中必须包含 `tenantId` 和 `correlationId`。
- 失败状态必须能说明失败阶段和原因。
- 不允许把 Keycloak service account secret、临时密码或 IdP secret 打印到日志。
- 后续如果加入管理 UI，应展示租户 IAM 状态、最近失败原因、重试次数和最近更新时间。
</context>

<PRD>
# Technical Architecture

## System Components

第一版推荐采用 Ports and Adapters 结构：

```text
TenantIamProvisioningService
  -> TenantIamProvisioningStep[]
  -> KeycloakAdminPort
  -> TenantIamStateRepository
  -> SecretStorePort
  -> EventPublisher
```

组件职责：

- `TenantIamProvisioningService`：应用用例，负责加载 Desired State、编排 Step Pipeline、维护流程状态。
- `TenantIamProvisioningStep`：幂等步骤接口，每个实现负责一个可验证的 IAM 事实。
- `KeycloakAdminPort`：应用核心访问 Keycloak 的抽象边界。
- `TenantIamStateRepository`：本地 provisioning 状态读写边界。
- `SecretStorePort`：为未来 Vault 与 BYO IdP 密钥管理预留边界。
- `EventPublisher`：为 Kafka 或其他事件基础设施预留边界。

## Data Models

第一版核心模型：

```text
TenantIamDesiredState
  tenantId
  tier
  adminUser
  identityMode
  identityProviders
  realmStrategy
  policies

AdminUser
  email
  temporaryCredentialPolicy

TenantIamProvisioningState
  tenantId
  iamStatus
  lastAttemptAt
  provisionedAt
  failureCode
  failureMessage
  retryCount
  workflowCorrelationId
```

枚举：

```text
IdentityMode
  LOCAL_ONLY
  BROKERED_IDP

RealmStrategy
  SHARED_REALM
  DEDICATED_REALM

TenantIamStatus
  PENDING_IAM
  IAM_PROVISIONING
  IAM_PROVISIONED
  IAM_FAILED
```

## APIs and Integrations

第一版内部用例入口：

```text
provisionTenantIam(TenantIamDesiredState desiredState, CorrelationId correlationId)
```

长期事件输入：

```text
TenantInfrastructureProvisionedEvent
  tenantId
  tier
  adminEmail
  correlationId
```

长期事件输出：

```text
TenantIamProvisionedEvent
  tenantId
  organizationId
  adminUserId
  correlationId

TenantIamProvisioningFailedEvent
  tenantId
  failureCode
  retryable
  correlationId
```

Keycloak 集成第一版可以先使用 Fake 或 In-Memory Adapter 完成核心流程与测试。真实 Keycloak Adapter 放入后续阶段。

## Infrastructure Requirements

第一版不要求真实 Kafka、Vault、Redis 或 Keycloak 可用，但代码边界必须允许后续接入：

- Keycloak Admin API credentials 未来必须来自 Vault。
- 真实 Keycloak Admin API 调用不得放入本地数据库事务。
- Kafka 事件消费和发布必须通过 Port 隔离。
- 日志必须支持按 `tenantId` 和 `correlationId` 排查。
- 所有外部副作用必须能通过幂等操作安全重试。

# Development Roadmap

## MVP Scope

MVP 只实现 Tenant IAM Onboarding 的最小闭环：

- 创建 `TenantIamDesiredState` 及相关值对象和枚举。
- 创建 `TenantIamProvisioningService`。
- 创建 `TenantIamProvisioningStep` 接口和第一版 Step Pipeline。
- 创建 `KeycloakAdminPort` 接口。
- 创建 Fake 或 In-Memory Keycloak Adapter，用于验证幂等语义。
- 创建 `TenantIamStateRepository` 抽象和内存实现。
- 创建 `EventPublisher` 抽象和内存或 no-op 实现。
- 实现状态流转：`PENDING_IAM`、`IAM_PROVISIONING`、`IAM_PROVISIONED`、`IAM_FAILED`。
- 编写单元测试覆盖首次执行、重复执行、中途失败后重试、状态更新和关键属性校验。

## Phase 2: Real Keycloak Adapter

- 接入 Keycloak Admin API。
- 实现 Organization 创建和查询。
- 实现 Organization attributes 写入和校正。
- 实现 User 创建和查询。
- 实现 Organization membership 管理。
- 实现 Realm Role 创建和用户角色分配。
- 处理 `409 Conflict`、404、5xx、网络超时等异常。
- 将 Keycloak 具体异常映射为领域错误。

## Phase 3: Event-Driven Integration

- 接入 Kafka 消费 `TenantInfrastructureProvisionedEvent`。
- 接入 Kafka 发布 `TenantIamProvisionedEvent`。
- 接入 Kafka 发布 `TenantIamProvisioningFailedEvent`。
- 增加 correlationId 贯穿日志和事件。
- 增加重复事件处理策略。

## Phase 4: Secret and Enterprise IAM Extensions

- 接入 Vault 或 SecretStore 实现。
- 支持 BYO IdP 配置模型。
- 支持 `ConfigureIdentityProviderStep`。
- 支持 MFA policy step。
- 支持 dedicated realm strategy 的扩展点。

## Explicitly Out of MVP Scope

- 真实外部 IdP 接入。
- MFA 策略落地。
- Dedicated Realm-per-Tenant 实现。
- 管理 UI。
- 审计日志系统。
- Envoy Gateway 配置改造。
- 下游服务 ABAC 实现。

# Logical Dependency Chain

开发顺序必须从领域核心到外部适配，避免一开始就被 Keycloak SDK 或 Kafka 细节绑住。

1. 定义领域模型：`TenantIamDesiredState`、`AdminUser`、状态枚举、策略枚举。
2. 定义 Ports：`KeycloakAdminPort`、`TenantIamStateRepository`、`EventPublisher`、`SecretStorePort`。
3. 定义 Step 接口和执行上下文。
4. 实现 In-Memory/Fake Adapters，先让核心流程可测试。
5. 实现第一版 Step Pipeline。
6. 实现 `TenantIamProvisioningService` 的状态流转和错误处理。
7. 编写幂等性测试和失败恢复测试。
8. 再接入真实 Keycloak Adapter。
9. 再接入 Kafka 和 Vault。

最小可用结果不是 UI，而是一个可运行、可测试、可重复执行的 onboarding 用例。它必须能证明租户身份事实被正确创建，并且重复执行不会破坏状态。

# Risks and Mitigations

## Risk 1: 把流程写成一次性创建脚本

如果直接按顺序调用 `createOrganization -> createUser -> assignRole`，重复事件、部分失败和重试都会导致冲突或脏状态。

缓解方式：所有步骤使用 `ensure` 语义，并用测试证明重复执行安全。

## Risk 2: 应用核心直接依赖 Keycloak SDK

这会让领域逻辑难以测试，也会把 Keycloak API 细节泄漏到用例层。

缓解方式：使用 `KeycloakAdminPort` 隔离外部系统，真实 Keycloak Adapter 后置。

## Risk 3: 错误使用本地事务包裹远程调用

Keycloak 不参与本地数据库事务。本地事务回滚无法回滚 Keycloak 对象。

缓解方式：远程调用依赖幂等 reconciliation，本地事务只用于本地状态更新。

## Risk 4: 状态模型过于简单

只使用 `iamProvisioned=true/false` 无法表达处理中、失败、重试次数和失败原因。

缓解方式：使用显式状态机和失败字段。

## Risk 5: 过早接入 BYO IdP、MFA、Dedicated Realm

这些能力很重要，但会扩大第一版复杂度，导致最小闭环无法稳定完成。

缓解方式：第一版只保留模型和 Step 扩展点，不实现真实企业 IAM 扩展。

## Risk 6: 忘记 IAL 整体信任边界

Onboarding 创建身份事实，但不负责请求认证。不能把下游服务变成 JWT 验证者，也不能用角色替代租户资源归属校验。

缓解方式：所有设计和测试说明必须持续强调：Keycloak 签发身份，Envoy 验证并注入 Header，下游服务消费 Header 并执行 ABAC。

# Appendix

## Key Architecture Decisions

- 使用 Keycloak Shared Realm `cdp`。
- 使用 Organization-per-Tenant 作为租户身份边界。
- Organization attributes 包含 `tenant_id` 和 `tier`。
- `tenant_id` 未来通过 Protocol Mapper 进入 JWT。
- `tier` 不进入 JWT，未来通过动态查询链路获取。
- Envoy Gateway 负责 JWT 验证和路由级 RBAC。
- Spring Boot 下游服务不解析 JWT，只消费 Envoy 注入的可信 Header。
- 资源级授权必须校验 `resource.tenantId == currentTenantId`。

## MVP Acceptance Criteria

- 可以通过一个 `TenantIamDesiredState` 执行完整 onboarding。
- 第一次执行后，Fake Keycloak 中存在 Organization、Admin User、Membership、Role Assignment。
- 第二次执行同一请求不会创建重复对象。
- 模拟中途失败后再次执行可以完成剩余步骤。
- 本地状态能正确记录 `IAM_PROVISIONING`、`IAM_PROVISIONED`、`IAM_FAILED`。
- 失败不会泄漏 secret、临时密码或敏感凭证到日志。
- 测试覆盖每个 Step 的幂等行为。

## Suggested Taskmaster Task Count

建议 Taskmaster 解析本 PRD 时生成 10 到 14 个顶层任务。任务粒度应围绕领域模型、Ports、Step Pipeline、Provisioning Service、Fake Adapter、状态机、测试、真实 Keycloak Adapter 准备和事件边界拆分。

## References

- `IAL_PRD_身份与访问层需求文档.md`
- `IAL_SAD_身份与访问层架构实施文档.md`
- `docs/plans/2026-05-21-tenant-iam-onboarding-design.zh.md`
- `agent.md`
</PRD>
