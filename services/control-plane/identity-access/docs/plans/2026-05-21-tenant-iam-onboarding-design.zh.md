# 租户 IAM Onboarding 设计文档

## 1. 设计目标

本设计的目标是把租户 IAM Onboarding 实现为一个可扩展、可重试、可验证的状态同步流程，而不是一次性的 Keycloak 创建脚本。

第一版只完成最小 IAM 闭环：

- Keycloak 中存在租户对应的 `Organization`。
- `Organization` 包含 `tenant_id` 和 `tier` 属性。
- 存在租户初始管理员用户。
- 初始管理员属于该 `Organization`。
- 初始管理员拥有 `TENANT_ADMIN` 角色。
- 本地系统记录 IAM 初始化完成状态。

这个设计同时服务于项目的学习目标：通过 IAL 的设计与实施，建立对 SaaS 多租户认证授权体系的完整理解。

## 2. 问题本质

Tenant Onboarding 的本质不是“创建几个 Keycloak 对象”，而是把一个业务租户正式注册进身份系统，使后续认证、授权、租户隔离都拥有稳定依据。

在 SaaS 多租户系统中，一个租户能安全运行，至少需要回答三个问题：

- 这个用户是谁：由 Keycloak User 和 JWT `sub` 表达。
- 这个用户属于哪个租户：由 Keycloak Organization 和 JWT `tenant_id` 表达。
- 这个用户能做什么：由 Role、Envoy RBAC 和服务内 ABAC 共同表达。

因此，Onboarding 创建的是“身份事实”，不是简单的配置数据。

## 3. 核心设计思想

采用 **Desired State Reconciliation** 模型。

也就是说，系统先描述“这个租户应该具备什么 IAM 状态”，然后逐步检查并修正 Keycloak 与本地系统中的实际状态，直到实际状态与期望状态一致。

不要把流程理解成：

```text
create organization
create user
assign role
finish
```

应该理解成：

```text
tenant acme-corp 应该拥有:
  Organization: acme-corp
  attributes:
    tenant_id = acme-corp
    tier = STANDARD
  admin user: admin@acme.com
  admin membership: admin 属于 acme-corp
  admin role: TENANT_ADMIN

每个步骤检查当前状态:
  已存在 -> 跳过或校正
  不存在 -> 创建
  创建冲突 -> 查询已有对象并继续
```

这样做的直接收益是：流程可以安全重试。

如果第一次执行时 `Organization` 创建成功，但 `Admin User` 创建失败，第二次执行不应该因为 `Organization` 已存在而失败，而应该识别出它已经存在，然后继续创建用户、加入组织、分配角色。

## 4. 第一版输入模型

第一版最小输入：

```json
{
  "tenantId": "acme-corp",
  "tier": "STANDARD",
  "adminEmail": "admin@acme.com"
}
```

虽然第一版输入很小，但领域模型需要为未来扩展预留空间：

```text
TenantIamDesiredState
  tenantId
  tier
  adminUser
  identityMode
  identityProviders
  realmStrategy
  policies
```

第一版默认值：

- `identityMode`: `LOCAL_ONLY`
- `realmStrategy`: `SHARED_REALM`
- `identityProviders`: 空
- `policies`: 空

未来扩展方向：

- `BROKERED_IDP`: 租户接入外部 IdP，例如 Okta、Azure AD、SAML。
- `DEDICATED_REALM`: 超大租户使用独立 Keycloak Realm。
- `MFA_REQUIRED`: 租户或 Organization 强制 MFA。

这里的关键原则是：第一版行为保持小，但模型形状要允许后续增长。

## 5. 架构形态

采用 Ports and Adapters 结构：

```text
TenantIamProvisioningService
  -> TenantIamProvisioningStep[]
  -> KeycloakAdminPort
  -> TenantIamStateRepository
  -> SecretStorePort
  -> EventPublisher
```

职责划分：

- `TenantIamProvisioningService`：应用用例，负责编排 onboarding 流程。
- `TenantIamProvisioningStep`：一个个可独立执行、可重试的幂等步骤。
- `KeycloakAdminPort`：描述系统需要 Keycloak 完成什么事情，不暴露 Keycloak SDK 细节。
- `TenantIamStateRepository`：维护本地 IAM provisioning 状态。
- `SecretStorePort`：为未来 BYO IdP 和服务账号密钥管理预留边界。
- `EventPublisher`：发布 IAM provisioning 成功或失败事件。

应用核心不能直接依赖 Keycloak SDK、Kafka Client、Vault Client 或数据库实现细节。这样做可以让核心逻辑更容易测试，也方便未来替换外部实现。

## 6. Step Pipeline 设计

第一版 Step Pipeline：

```text
EnsureOrganizationStep
EnsureOrganizationAttributesStep
EnsureAdminUserStep
EnsureOrganizationMembershipStep
EnsureTenantAdminRoleStep
MarkIamProvisionedStep
PublishTenantIamProvisionedEventStep
```

每个 Step 的语义都是 `ensure`，不是 `create`。

`ensure` 的含义是：

- 目标对象不存在时创建。
- 目标对象已存在时复用。
- 目标关系已存在时视为成功。
- 属性不一致时按规则校正。
- Keycloak 返回 `409 Conflict` 时，不直接失败，而是查询已有对象并继续。

未来新增能力时，只添加新的 Step：

```text
ConfigureIdentityProviderStep
ConfigureMfaPolicyStep
ConfigureDedicatedRealmStep
ConfigureProtocolMapperStep
ConfigureClientAudienceStep
ConfigureTenantRoleMappingsStep
```

这样主流程不需要频繁改动，扩展点清晰。

## 7. 状态机设计

不要只使用一个布尔字段，例如 `iamProvisioned`。

推荐使用显式状态：

```text
PENDING_IAM
IAM_PROVISIONING
IAM_PROVISIONED
IAM_FAILED
```

推荐状态字段：

```text
tenantId
iamStatus
lastAttemptAt
provisionedAt
failureCode
failureMessage
retryCount
workflowCorrelationId
```

状态流转：

```text
PENDING_IAM -> IAM_PROVISIONING
IAM_PROVISIONING -> IAM_PROVISIONED
IAM_PROVISIONING -> IAM_FAILED
IAM_FAILED -> IAM_PROVISIONING
```

注意：IAM provisioning 成功不等于租户已经 `ACTIVE`。

按照现有 IAL 设计，IAM provisioning 只是推动租户进入后续初始化阶段，例如 `PENDING_ACTIVATION`。Connector Registry、DataSource、Connection Governance 等其他 Bounded Context 可能还需要继续初始化。

## 8. Port 设计

### 8.1 KeycloakAdminPort

`KeycloakAdminPort` 应该暴露意图型操作，而不是暴露 Keycloak SDK 的低层接口。

第一版操作：

```text
ensureOrganization(tenantId, attributes)
ensureUser(email, temporaryCredentialPolicy)
ensureOrganizationMembership(organizationId, userId)
ensureRealmRole(realmRoleName)
ensureUserRealmRole(userId, realmRoleName)
```

未来操作：

```text
ensureIdentityProvider(tenantId, idpConfig)
ensureProtocolMapper(clientId, mapperConfig)
ensureClientAudience(clientId, audience)
ensureMfaPolicy(tenantId, policy)
```

### 8.2 TenantIamStateRepository

负责本地 provisioning 状态：

```text
markProvisioningStarted(tenantId)
markProvisioned(tenantId)
markFailed(tenantId, failure)
findStatus(tenantId)
```

### 8.3 EventPublisher

负责发布后续流程需要消费的事件：

```text
publishTenantIamProvisioned(event)
publishTenantIamProvisioningFailed(event)
```

### 8.4 SecretStorePort

为未来 BYO IdP 和服务账号凭证预留：

```text
readSecret(path)
writeSecret(path, value)
```

第一版可以不实现真实 Vault，但边界要提前保留。

## 9. 事务边界

不要把 Keycloak Admin API 调用包进数据库事务。

原因是 Keycloak 是外部系统，不参与本地数据库事务。即使本地事务回滚，Keycloak 中已经创建的对象也不会自动回滚。

正确做法：

- 远程 Keycloak 调用依赖幂等性保障。
- 本地 DB 状态更新使用本地事务。
- 失败后通过状态机和重试继续 reconcile。

可以使用本地事务的操作：

- 标记 IAM provisioning 开始。
- 标记 IAM provisioning 完成。
- 记录失败原因。
- 更新 retry count。

不应该放入本地事务的操作：

- 创建 Keycloak Organization。
- 创建 Keycloak User。
- 加入 Organization。
- 分配 Keycloak Role。
- 配置外部 IdP。

## 10. 事件设计

输入事件：

```text
TenantInfrastructureProvisionedEvent
  tenantId
  tier
  adminEmail
  correlationId
```

成功输出事件：

```text
TenantIamProvisionedEvent
  tenantId
  organizationId
  adminUserId
  correlationId
```

失败输出事件：

```text
TenantIamProvisioningFailedEvent
  tenantId
  failureCode
  retryable
  correlationId
```

第一版可以先用测试或本地 runner 直接调用 `TenantIamProvisioningService`，但长期目标仍然是 Kafka 事件驱动。

## 11. 信任边界

Onboarding 创建身份事实，但不负责认证请求。

认证请求链路仍然是：

```text
Keycloak issues JWT
  -> Envoy validates JWT
  -> Envoy injects X-User-ID, X-Tenant-ID, X-User-Roles
  -> downstream services build SecurityContext from headers
```

`Organization.attributes.tenant_id` 很关键，因为它会通过 Keycloak Protocol Mapper 进入 JWT，成为下游服务识别租户的依据。

信任边界必须清晰：

- Keycloak 负责签发身份。
- Envoy 负责验证 JWT。
- Envoy 负责把 claim 转换成可信 Header。
- 下游服务只消费 Header，不解析 JWT。
- 下游服务仍必须执行资源归属校验，不能只依赖角色。

## 12. 最小验证场景

第一版必须验证：

- 第一次执行 onboarding 会创建 Organization、Admin User、Membership、Role Assignment。
- 第二次执行同一个 onboarding 请求不会创建重复对象，也不会因为冲突失败。
- 如果在 Organization 创建成功后流程失败，重试能够从已有 Organization 继续。
- Organization 中包含正确的 `tenant_id` 和 `tier`。
- 可以通过 email 解析到初始管理员用户。
- 初始管理员属于对应 Organization。
- 初始管理员拥有 `TENANT_ADMIN`。
- 本地状态最终变为 `IAM_PROVISIONED`。

后续端到端验证：

- 初始管理员登录后，JWT 中包含 `tenant_id`。
- JWT 中包含打平后的 `roles`。
- Envoy 能注入 `X-Tenant-ID` 和 `X-User-Roles`。
- 下游服务 ABAC 能拒绝跨租户资源访问。

## 13. 第一版实施范围

第一版实现：

- 可扩展的 `TenantIamDesiredState` 请求模型。
- Step Pipeline 抽象。
- `KeycloakAdminPort` 接口。
- Fake 或 In-Memory Keycloak Adapter，用于单元测试。
- 本地 provisioning state 抽象。
- 幂等 step 执行测试。

第一版暂缓：

- 真实 Keycloak Adapter。
- BYO IdP 配置。
- MFA 策略。
- Dedicated Realm 策略。
- Kafka 集成。
- Vault 集成。

## 14. 常见误区

避免以下设计错误：

- 把 onboarding 写成不可重试的创建脚本。
- 遇到 `409 Conflict` 就直接失败。
- 用 `iamProvisioned=true/false` 表达所有状态。
- 在本地数据库事务中调用 Keycloak Admin API。
- 让应用核心直接依赖 Keycloak SDK。
- 认为分配了 `TENANT_ADMIN` 就可以跳过资源 `tenantId` 校验。
- 过早实现 BYO IdP、MFA、Dedicated Realm，导致第一版无法闭环。

## 15. 设计原则

第一版应做到：行为小，形状强。

必须保留并强化这些概念：

- Tenant IAM Onboarding 创建的是持久身份事实。
- Organization 是 Shared Realm 模型下的租户身份边界。
- `tenant_id` 是 Keycloak 身份体系与业务数据隔离之间的桥。
- Role 决定用户能做什么，但不能替代租户归属校验。
- 对远程 IAM 系统做 provisioning 时，幂等 reconciliation 比事务回滚更可靠。

