# Claude Code Instructions

## Task Master AI Instructions
**Import Task Master's development workflow commands and guidelines, treat as if import is in the main CLAUDE.md file.**
@./.taskmaster/CLAUDE.md

---

# Data Hub Platform — 项目上下文

## 项目定位

SaaS 多租户 CDP（Customer Data Platform）平台，控制面（control-plane）负责租户生命周期管理。目前主要开发模块：**IAM Provisioning Service**。

---

## iam-provisioning-service

**路径**：`services/control-plane/identity-access/iam-provisioning-service/`

**职责**：接收上游 `TenantInfrastructureProvisionedEvent`，幂等地在 Keycloak 中完成租户 IAM 资源的初始化（Organization、Admin 用户、Realm Role 绑定、组织成员关系），并发布 `TenantIamProvisionedEvent` 或 `TenantIamProvisioningFailedEvent`。

### 架构风格：六边形架构（Ports & Adapters）

```
interfaces/          ← Inbound Adapters（HTTP Controller、Kafka Consumer）
application/
  port/in/           ← Driving Ports（Use Case 接口）
  port/out/          ← Driven Ports（Repository、EventPublisher、KeycloakAdminPort、SecretStorePort）
  service/           ← Application Services（TenantIamOnboardingService、TenantIamProvisioningService）
  pipeline/          ← Step Pipeline（TenantIamProvisioningStep、StepExecutionContext）
  mapper/            ← 事件到领域模型的翻译（TenantIamDesiredStateMapper）
domain/
  model/             ← 聚合根 & 领域对象（TenantIamProvisioningState、TenantIamDesiredState）
  valueobject/       ← 值对象（TenantId、Email、CorrelationId、Tier 等）
  event/             ← 领域事件（TenantIamProvisionedEvent、TenantIamProvisioningFailedEvent）
infrastructure/
  keycloak/          ← KeycloakAdminPort 实现（RealKeycloakAdminPort、FakeKeycloakAdminPort）
  messaging/         ← EventPublisher 实现（InMemoryEventPublisher，Kafka 实现待做）
  persistence/       ← Repository 实现（InMemoryTenantIamProvisioningStateRepository，JDBC 待做）
  secrets/           ← SecretStorePort 实现（InMemorySecretStore、VaultSecretStore）
config/              ← Spring 配置（KeycloakAdapterConfiguration、VaultSecretStoreConfiguration）
```

---

### 核心事件流

```
上游 BC（Tenant Management）
  │  TenantInfrastructureProvisionedEvent（Kafka → 待实现）
  ▼
TenantIamOnboardingEventHandler（interfaces/messaging，薄适配器）
  │  handle(event)
  ▼
TenantIamOnboardingService（application/service，翻译 + 委托）
  │  mapper.from(event) → TenantIamDesiredState
  │  provisionUseCase.provisionTenantIam(desiredState, correlationId)
  ▼
TenantIamProvisioningService（application/service，Step Pipeline 协调）
  │  findOrInitById → markInProgress → save → [Steps] → markCompleted → save
  ▼
Step Pipeline（按 @Order 顺序执行）
  1. EnsureOrganizationStep   (@Order(1)) → 产出 organizationId 到 StepExecutionContext
  2. EnsureAdminUserStep      (@Order(2)) → 产出 userId
  3. EnsureTenantAdminRoleStep(@Order(3)) → 绑定 TENANT_ADMIN / data_engineer / viewer realm roles
  4. EnsureOrganizationMembershipStep(@Order(4)) → 绑定用户到 Organization
  ▼
EventPublisher.publish(TenantIamProvisionedEvent)   ← 成功路径
EventPublisher.publish(TenantIamProvisioningFailedEvent) ← 失败路径
```

---

### 状态机：TenantIamProvisioningState

```
IAM_PENDING
  │ markInProgress()
  ▼
IAM_IN_PROGRESS
  ├──[成功] markCompleted()   → IAM_COMPLETED（终态，再次触发直接跳过）
  ├──[可重试失败] markAwaitRetry() → IAM_AWAITING_RETRY（指数退避，最大 5 次）
  │                          │ markInProgress()
  │                          └──────────────→ IAM_IN_PROGRESS（重入）
  └──[不可重试/超次] markFailed() → IAM_FAILED（终态，需人工介入）
```

**重要约束**：
- `FAILED` 是终态，不允许自动重新进入，需人工操作状态转移
- `markInProgress` 仅在 `PENDING` 或 `AWAITING_RETRY` 下有效
- `markCompleted` 前校验所有 4 个子目标 checkpoint 均已完成

---

### Step Pipeline 关键设计

**StepExecutionContext**：不可变值对象，携带 `tenantId`、`correlationId`、`Optional<OrganizationId>`、`Optional<UserId>`。每个 Step 通过 `withOrganizationId()` / `withUserId()` 返回新实例，不修改原对象。

**TenantIamProvisioningCheckpoint**（enum）：
```
ORGANIZATION_CREATED → ADMIN_USER_CREATED → TENANT_ADMIN_ROLE_ASSIGNED → ORGANIZATION_MEMBERSHIP_CREATED
```
每个 Step 完成后立即调用 `markStepCompleted(checkpoint, now)` 并 `repository.save()`，实现断点续传。

**IamProvisioningException**：Pipeline Step 内部异常，携带 `step`、`failureCode`、`retryable`。由 `TenantIamProvisioningService` 捕获后根据 `retryable` 决定进入 `AWAITING_RETRY` 还是 `FAILED`。

---

### KeycloakAdminPort：ensure 语义

所有操作均**幂等**：
1. 不存在 → 创建
2. 已存在（含 409 Conflict）→ 查询已有资源，返回其 ID，不报错不重建
3. 属性不一致 → 按 DesiredState 校正

**实现切换**（通过 `cdp.keycloak.adapter.type`）：
- `real`（默认）：`RealKeycloakAdminPort`，使用 Keycloak Admin Client 真实 HTTP 调用
- `fake`：`FakeKeycloakAdminPort`，内存实现，含故障注入机制（`scheduledFailures`），用于单元/集成测试

**Vault 同理**（通过 `cdp.vault.adapter.type`）：
- `fake`（默认）：`InMemorySecretStore`，预填 keycloak client credential
- `real`：`VaultSecretStore`（存根，待实现）

---

### 当前基础设施实现状态

| 接口 | 生产实现 | 测试实现 | 状态 |
|------|---------|---------|------|
| `KeycloakAdminPort` | `RealKeycloakAdminPort` | `FakeKeycloakAdminPort` | ✅ 完成 |
| `TenantIamProvisioningStateRepository` | （JDBC 待实现） | `InMemoryTenantIamProvisioningStateRepository` | ⏳ 内存实现可用 |
| `EventPublisher` | （Kafka 待实现 — LEI-76） | `InMemoryEventPublisher` | ⏳ 内存实现可用 |
| `SecretStorePort` | `VaultSecretStore`（存根） | `InMemorySecretStore` | ⏳ Vault 存根 |
| Kafka Consumer | 待实现（LEI-81） | `TenantIamOnboardingEventHandler` 直调 | ❌ 未实现 |

**DB Schema**（`V1__create_iam_provisioning_schema.sql`）：
```sql
CREATE TABLE tenant_iam_provisioning_state (
    tenant_id VARCHAR(128) PRIMARY KEY,
    iam_status VARCHAR(64) NOT NULL,
    ...
);
```
注：Flyway 已配置，JDBC Repository 实现尚未写，目前运行时使用内存实现。

---

### 关键待做（TODO 标注在代码中）

- `TenantIamProvisioningService`：`// TODO: outbox pattern` — 事件发布目前直接调用，需引入 Outbox
- `TenantIamProvisioningService`：`// TODO: RetryScheduler 还未配置` — `markAwaitRetry` 后需调度器重新触发
- `TenantIamOnboardingEventHandler`：`// 本类当前不加 @KafkaListener` — LEI-76 补上
- `StepExecutionContext.withOrganizationId`：`// TODO: 配置写保护` — 防止同一 context 被多次写入不同值
- `VaultSecretStore`：存根，未实现真实 Vault 调用

---

### 测试结构

| 测试类 | 类型 | 说明 |
|--------|------|------|
| `TenantIamOnboardingEventHandlerTest` | 集成（内存） | 端到端事件流，验证成功/失败/幂等路径 |
| `KeycloakAdminPortContractTest`（抽象） | 契约 | Fake 和 Real 实现均继承此契约，保证行为一致 |
| `RealKeycloakAdminPortTest` | 集成（Testcontainers Keycloak） | 真实 Keycloak 容器验证 |
| `TenantIamProvisioningServiceTest` | 单元 | Step Pipeline 协调逻辑 |
| `TenantIamProvisioningStateTest` | 单元 | 状态机转换规则 |
| `InMemoryTenantIamProvisioningStateRepositoryTest` | 单元 | Repository 幂等语义 |
| 值对象测试 | 单元 | Email、TenantId、CorrelationId 等验证规则 |

---

### 配置速查

| 配置项 | 默认值 | 说明 |
|--------|-------|------|
| `cdp.keycloak.adapter.type` | `real` | `real` / `fake` |
| `cdp.vault.adapter.type` | `fake` | `real` / `fake` |
| `cdp.keycloak.admin.realm` | `cdp-auth-pool` | Shared Realm 名称 |
| `server.port` | `8083` | HTTP 端口 |
| `IAM_PROVISIONING_DB_URL` | `jdbc:postgresql://localhost:5432/iam_provisioning` | DB 连接 |

---

### 关键架构约定（勿违反）

1. **Application 层不依赖 Keycloak SDK 类型**：`KeycloakAdminPort` 是唯一边界，SDK 类型仅在 `infrastructure/keycloak/` 内可见
2. **Step 内异常必须翻译**：`KeycloakOperationException` → `IamProvisioningException`，不允许 SDK 异常泄漏到 application 层
3. **每步骤后立即持久化**：`markStepCompleted` + `repository.save()` 必须成对出现，保证断点续传
4. **correlationId 端到端贯穿**：从入站事件取，不在 IAM BC 内重新生成，贯穿状态记录、日志 MDC、输出事件
5. **EventPublisher 在发布后才抛异常**：失败路径先 `publish(failedEvent)` 再 `throw e`，保证下游可感知失败
6. **禁止把 secret 写入事件 payload 或日志**：临时密码、client credential 均不得出现在事件或结构化日志中
