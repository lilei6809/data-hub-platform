<context>
# Overview

本 PRD 定义 Identity & Access Layer 中 **Tenant IAM Provisioning / Identity Provisioning BC** 的第一版交付范围。

该 BC 解决的问题是：新租户被创建后，平台如何可靠、可重试、可观测地在身份系统中建立租户身份基础设施，使后续登录、租户上下文传播、Authorization BC 初始化和审计链路拥有稳定前提。

Tenant IAM Provisioning BC 不是 Tenant Management BC 的附属函数，也不是直接调用 Keycloak API 的脚本。它是一个独立的 Supporting Domain Bounded Context，负责将“租户已创建”这一外部事实，调和成 Keycloak Shared Realm 中的稳定身份事实：

- 租户有对应 Keycloak Organization。
- 平台 Realm Role 已存在并可用于 JWT 角色传播。
- 初始租户管理员用户存在。
- 初始租户管理员属于该 Organization。
- 初始租户管理员拥有 `TENANT_ADMIN` Realm Role。
- 本地系统记录 provisioning desired state、步骤完成情况、失败原因和重试状态。
- 成功后发布 `TenantIamProvisioningCompleted`，失败耗尽重试后发布 `TenantIamProvisioningFailed`。

第一版实现必须坚持以下边界：

- Tenant Management BC 是租户元数据和生命周期 Source of Truth。
- IAM Provisioning BC 不直接读写 Tenant Management BC 或 Authorization BC 的数据库。
- IAM Provisioning BC 不创建 Authorization BC 的 Policy、Role、RoleAssignment。
- Authorization BC 自主消费 `TenantIamProvisioningCompleted` 并初始化空 Policy。
- Keycloak SDK、HTTP 状态码、Representation 类型不得进入 Domain 或 Application 核心。
- OAuth2 Client 注册使用平台级 Shared Client，不进入每租户 provisioning 流程。

本 PRD 同时采用基础 PRD 模板和 Repository Planning Graph（RPG）模板：先描述产品需求和核心能力，再给出 capability tree、结构映射、依赖链、开发阶段、测试策略、架构决策、风险和附录，便于 Task Master 后续解析成依赖明确的任务图。

# Core Features

## 1. Tenant IAM Desired State

系统需要用 `TenantIamDesiredState` Aggregate Root 表达“这个租户在身份系统中期望达到的状态”，而不是把一次请求直接映射成一组 Keycloak Admin API 调用。

第一版最小输入：

- `tenantId`
- `tenantName`
- `tier`
- `adminEmail`
- `correlationId`
- `sourceEventId`

Aggregate 需要记录：

- 宏观状态：`PENDING`、`IN_PROGRESS`、`AWAITING_RETRY`、`COMPLETED`、`FAILED`
- 子目标状态：Organization 已创建、平台角色已验证、Admin User 已创建、Admin User 已加入 Organization、`TENANT_ADMIN` 已分配
- 远端标识：`organizationIdentity`、`adminUserIdentity`
- 重试治理：`retryCount`、`lastAttemptAt`、`nextRetryAt`、`failedStep`、`lastFailureReason`
- 审计字段：`createdAt`、`updatedAt`

该模型重要，因为 IAM provisioning 是长流程、远端副作用流程和事件驱动流程。系统必须能在进程崩溃、消息重复、Keycloak 短暂不可用、步骤部分成功后继续从已达成状态恢复。

## 2. Event Intake and ACL Translation

系统需要消费上游租户生命周期事实事件，并在 Consumer 边界将外部事件翻译成本 BC 内部 Command。

DDD 源文档以 `TenantCreated` 为触发事件；当前 identity-access agent guide 中使用 `TenantInfrastructureProvisionedEvent`。第一版实现必须将事件命名作为可配置集成契约处理，核心内部命令统一为：

```text
StartTenantIamProvisioningCommand
```

事件入口需要：

- 校验事件必需字段。
- 将上游字段翻译为 `TenantId`、`TenantName`、`TierLevel`、`AdminEmail` 等 Value Object。
- 通过 `sourceEventId` 和 `tenantId` 做幂等认领。
- 不让上游 DTO 进入 Domain 层。

该特性重要，因为 Customer / Supplier 关系中上游事件属于上游语言。ACL 翻译层保护本 BC 的 Ubiquitous Language，不把 Tenant Management BC 的字段命名、事件版本和技术格式泄漏进核心模型。

## 3. Idempotent Provisioning Step Pipeline

系统需要把 Keycloak provisioning 实现为可恢复的 Step Pipeline。每个步骤都必须采用 ensure / desired-state 语义。

第一版步骤：

- `CreateOrganizationStep`
- `VerifyPlatformRolesStep`
- `CreateAdminUserStep`
- `JoinOrganizationStep`
- `AssignTenantAdminRoleStep`
- `MarkProvisioningCompletedStep`
- `PublishProvisioningCompletedEventStep`

每个步骤需要满足：

- 子目标已达成时直接跳过。
- Keycloak 返回 409 时查询既有对象并视为可恢复成功。
- 远端对象已存在但本地状态未记录时能够补齐本地状态。
- 步骤成功后尽快持久化 Aggregate 状态。
- 任一步骤失败后记录 `failedStep` 和失败原因。

该特性重要，因为 Kafka 与 Debezium 提供 at-least-once 投递，系统必须接受重复消费和重复执行，通过幂等步骤达到业务上的“最终恰好一次”效果。

## 4. Keycloak Admin Port and Adapter

应用核心不能直接依赖 Keycloak SDK。系统需要定义 `KeycloakAdminPort`，用业务语言表达 IAM Provisioning BC 需要身份系统完成的能力。

第一版 Port 方法：

- `createOrganization(tenantId, tenantName)`
- `verifyPlatformRoles(roles)`
- `createAdminUser(organizationIdentity, adminUserSpec)`
- `joinOrganization(organizationIdentity, userIdentity)`
- `assignTenantAdminRole(userIdentity)`

Adapter 需要处理：

- Admin Token 获取与缓存。
- `ReentrantLock` 防止并发刷新 token 惊群。
- 80% TTL 安全窗口。
- Keycloak HTTP/SDK 异常到 Domain Exception 的翻译。
- Create-then-handle 409 的幂等策略。
- Keycloak `OrganizationRepresentation`、`UserRepresentation` 等类型隔离在 Infrastructure 层。

该特性重要，因为 Keycloak 是 Generic Domain 外部系统。Port/Adapter 隔离能降低测试成本、控制 SDK 升级影响，并为未来替换 Auth0、Okta、Cognito 预留空间。

## 5. Local Persistence, Retry, and Outbox

系统需要在 PostgreSQL 中持久化 desired state、重试调度信息和出站集成事件。

第一版需要：

- `tenant_iam_desired_state` 表。
- `tenant_id` 唯一约束，用于重复消费认领。
- `next_retry_at` 字段，用于数据库轮询重试。
- `source_event_id` 和 `correlation_id` 字段，用于链路追踪。
- Outbox 记录 `TenantIamProvisioningCompleted` 和 `TenantIamProvisioningFailed`。

重试实现采用：

- `AWAITING_RETRY` + `nextRetryAt`
- `@Scheduled` 轮询
- `SELECT ... FOR UPDATE SKIP LOCKED`
- Exponential Backoff with Jitter
- 最大重试次数默认 5 次

该特性重要，因为重试计划不能只存在 JVM 内存里，也不能拆到 Redis 里造成状态分裂。IAM provisioning 的状态影响租户是否可用，必须住在主数据库里。

## 6. Published Language Events

系统需要将 IAM provisioning 的结果以事实型事件发布给下游 BC。

第一版事件：

- `TenantIamProvisioningCompleted`
- `TenantIamProvisioningFailed`

`TenantIamProvisioningCompleted` 至少包含：

- `eventId`
- `tenantId`
- `organizationId`
- `adminUserId`
- `occurredAt`
- `correlationId`

`TenantIamProvisioningFailed` 至少包含：

- `eventId`
- `tenantId`
- `failedStep`
- `failureCode`
- `failureMessage`
- `retryCount`
- `occurredAt`
- `correlationId`

该特性重要，因为 Authorization BC、Audit BC、Tenant Management BC 都只能通过事件自主响应 IAM provisioning 结果。IAM Provisioning BC 不命令下游做什么，只陈述自己已经完成或失败的事实。

## 7. Authorization BC Handoff Contract

IAM Provisioning 完成后，Authorization BC 应消费 `TenantIamProvisioningCompleted` 并初始化空 Policy。该行为不属于 IAM Provisioning BC 的实现范围，但事件契约必须支持它。

PRD 约束：

- IAM Provisioning BC 不直接调用 Authorization BC API。
- IAM Provisioning BC 不写 Authorization BC 数据库。
- Authorization BC 初始化空 Policy，默认 DENY。
- Authorization BC 使用自己的 Consumer ACL 将外部事件翻译成内部 `InitializePolicyCommand`。
- Authorization BC 的 Policy 写入需要唯一约束和 `ON CONFLICT DO NOTHING`。

该特性重要，因为这是 Identity & Access Layer 的闭环：新租户身份基础设施就绪后，授权基础设施也能最终就绪，但两个 BC 之间保持时间解耦。

## 8. Admin Operations and Observability

系统需要提供运维可见性和人工恢复入口。

第一版需要：

- 查询某租户 provisioning 状态。
- 查询失败原因、失败步骤、重试次数、下次重试时间。
- 受保护的手动 retry API。
- 结构化日志包含 `tenantId`、`correlationId`、`sourceEventId`、`stepName`。
- Micrometer 指标统计成功、失败、重试、耗时、卡在 PENDING 或 AWAITING_RETRY 的数量。

该特性重要，因为 IAM provisioning 失败意味着租户无法登录或无法完成激活。平台运维必须能快速定位、手动恢复，并知道问题卡在 Keycloak、数据库、事件发布还是状态机。

# User Experience

## User Personas

- 平台开发者：实现 IAM Provisioning BC，需要明确领域边界、状态机、幂等规则、Keycloak Port 和测试场景。
- 平台管理员：创建新租户后，需要看到 IAM 初始化状态并在失败时手动重试。
- 租户管理员：新租户完成 provisioning 后，能用初始管理员身份登录平台。
- Authorization BC 开发者：依赖 `TenantIamProvisioningCompleted` 初始化空 Policy。
- Audit / Operations 开发者：依赖 Completed / Failed 事件记录审计和告警。
- 安全工程师：需要确认没有明文密码、没有每租户 Client 扩散、没有跨 BC 数据库访问。

## Key User Flows

### Flow 1: 新租户 IAM Provisioning 成功

1. 上游发布 `TenantCreated` 或 `TenantInfrastructureProvisionedEvent`。
2. IAM Provisioning Consumer 接收事件并交给 ACL Translator。
3. ACL Translator 生成 `StartTenantIamProvisioningCommand`。
4. Application Service 插入 `TenantIamDesiredState(PENDING)`，利用 `tenant_id` 唯一约束完成幂等认领。
5. Pipeline 标记 `IN_PROGRESS`。
6. 系统创建或复用 Keycloak Organization。
7. 系统验证平台 Realm Role 存在。
8. 系统创建或复用初始管理员用户。
9. 系统将管理员加入 Organization。
10. 系统给管理员分配 `TENANT_ADMIN` Realm Role。
11. Desired State 标记 `COMPLETED`。
12. Outbox 写入 `TenantIamProvisioningCompleted`。
13. 下游 BC 自主消费完成事件。

### Flow 2: 重复事件消费

1. Kafka 或 Debezium 重复投递同一租户事件。
2. 第二个 Consumer 尝试插入 `TenantIamDesiredState(PENDING)`。
3. 数据库唯一约束触发冲突。
4. Consumer 将其视为重复事件，记录日志后跳过。
5. 不重复创建 Keycloak Organization、User、Membership 或 Role Assignment。

### Flow 3: 中途失败后自动重试

1. Organization 创建成功。
2. Admin User 创建阶段 Keycloak 超时。
3. Aggregate 记录 `failedStep=CreateAdminUserStep`、`retryCount=1`、`nextRetryAt`。
4. 状态进入 `AWAITING_RETRY`。
5. RetryScheduler 到期后用 `FOR UPDATE SKIP LOCKED` 认领记录。
6. Pipeline 重新运行，跳过 Organization 步骤。
7. 系统继续 Admin User、Membership、Role Assignment 步骤。
8. 成功后发布 Completed 事件。

### Flow 4: 重试耗尽后人工恢复

1. Provisioning 连续失败达到最大重试次数。
2. 状态进入 `FAILED`。
3. 系统发布 `TenantIamProvisioningFailed` 并发出 CRITICAL 告警。
4. 平台管理员查看失败步骤和错误原因。
5. 外部问题修复后，管理员调用手动 retry API。
6. 系统清空失败状态并重新进入 Pipeline。

## UX Considerations

第一版不要求实现完整 Admin Console UI，但必须提供 API、日志和指标支持未来 UI：

- 状态名称应面向运维可理解，不暴露 Keycloak SDK 异常。
- 失败信息需要分为 machine-readable `failureCode` 和 human-readable `failureMessage`。
- 手动 retry 需要具备审计记录。
- 任何明文密码或 client secret 不得出现在响应、日志或事件中。
</context>

<PRD>
# Problem Statement

当前平台已经明确使用 Keycloak Shared Realm + Organization-per-Tenant 作为租户身份隔离模型，但新租户注册后仍需要一个可靠流程把 Tenant Management 的租户事实转化为 Keycloak 中的身份事实。

如果直接在 Tenant Management BC 中调用 Keycloak Admin API，会产生三类问题：

- 职责混杂：租户元数据生命周期被 Keycloak API、SDK 版本、安全策略变化污染。
- 失败域混杂：Keycloak 短暂不可用会影响租户记录创建。
- 一致性薄弱：数据库写入与 Kafka 事件发布存在双写问题，重复事件和部分成功难以恢复。

成功的第一版系统应该做到：

- 重复事件不会重复创建 Keycloak 资源。
- 任意步骤失败后可自动重试并从已完成步骤继续。
- Keycloak 依赖被 Port/Adapter 隔离。
- 本地状态和出站事件通过 Outbox 保证一致性。
- Authorization BC 可以通过事件异步闭环初始化空 Policy。

# Target Users

- IAL 服务开发者：需要可执行任务图和明确模块边界。
- 平台运维：需要查看和恢复租户 IAM provisioning。
- 下游 BC 开发者：需要稳定事件契约。
- 安全评审人员：需要验证信任边界、凭据边界和跨 BC 边界。

# Success Metrics

- 同一 `tenantId` 的重复事件处理不会创建重复 desired state 记录。
- Step Pipeline 对同一租户重复执行 N 次，Keycloak Fake Adapter 中 Organization、User、Membership、Role Assignment 都保持唯一。
- Organization 创建成功后进程失败，重试能跳过 Organization 并完成剩余步骤。
- Keycloak 409 被视为可恢复幂等路径，而不是系统失败。
- `TenantIamProvisioningCompleted` 只在本地 state 成功变为 `COMPLETED` 的同一事务 outbox 中写入。
- `TenantIamProvisioningFailed` 只在重试耗尽或不可恢复错误后发布。
- Domain 和 Application 层没有 Keycloak SDK 类型、HTTP status code 或 Kafka 注解。

# Functional Decomposition

## Capability Tree

### Capability: Event Intake and Boundary Translation

接收上游租户事实事件，并转换成本 BC 内部命令。

#### Feature: Source Event Listener

- **Description**: 消费 `TenantCreated` 或当前集成事件名，并触发 IAM provisioning。
- **Inputs**: Kafka message、event headers、event payload。
- **Outputs**: 原始事件 DTO 或拒绝原因。
- **Behavior**: 解析事件、校验必需字段、记录 `sourceEventId` 和 `correlationId`。

#### Feature: ACL Translator

- **Description**: 将上游事件翻译为 `StartTenantIamProvisioningCommand`。
- **Inputs**: 上游事件 DTO。
- **Outputs**: 内部 Command，包含 `TenantId`、`TenantName`、`TierLevel`、`AdminEmail`。
- **Behavior**: 完成字段映射、Value Object 构造、基础格式校验。

#### Feature: Idempotent Claim

- **Description**: 通过 `tenant_id` 唯一约束认领 provisioning 处理权。
- **Inputs**: `StartTenantIamProvisioningCommand`。
- **Outputs**: 新建 `TenantIamDesiredState(PENDING)` 或重复事件跳过。
- **Behavior**: 直接 INSERT，不使用先 SELECT 再 INSERT 的竞态模式。

### Capability: Desired State Domain Model

用领域模型表达 provisioning 的目标状态、进度和失败治理。

#### Feature: TenantIamDesiredState Aggregate

- **Description**: 记录租户 IAM provisioning 的宏观状态、子目标状态和远端身份标识。
- **Inputs**: Command、Step 执行结果、失败信息。
- **Outputs**: 可持久化 Aggregate。
- **Behavior**: 保护状态转移，提供 mark/record 方法，不暴露任意 setter。

#### Feature: ProvisioningStatus State Machine

- **Description**: 管理 `PENDING -> IN_PROGRESS -> AWAITING_RETRY -> COMPLETED/FAILED` 状态转换。
- **Inputs**: Pipeline 启动、步骤成功、步骤失败、重试耗尽、管理员 retry。
- **Outputs**: 新状态和状态变更时间。
- **Behavior**: 拒绝非法状态转换，保留 failedStep 和 failureReason。

#### Feature: Retry Policy

- **Description**: 计算重试次数、退避时间和告警等级。
- **Inputs**: 当前 retryCount、failureCode。
- **Outputs**: `nextRetryAt`、是否重试耗尽、告警级别。
- **Behavior**: 使用 Exponential Backoff with Jitter，默认最大 5 次。

### Capability: Idempotent Provisioning Pipeline

按 desired state 顺序驱动 Keycloak provisioning 步骤。

#### Feature: Pipeline Orchestrator

- **Description**: 顺序执行 provisioning steps 并在每步成功后保存状态。
- **Inputs**: `tenantId`。
- **Outputs**: `COMPLETED`、`AWAITING_RETRY` 或 `FAILED` 的 desired state。
- **Behavior**: 跳过已达成步骤，捕获业务异常，写入 outbox event。

#### Feature: ProvisioningStep Contract

- **Description**: 为所有步骤提供统一接口。
- **Inputs**: `TenantIamDesiredState`。
- **Outputs**: 子目标完成标志或步骤异常。
- **Behavior**: 每个步骤实现 `isAlreadyAchieved`、`execute`、`markAchieved`。

#### Feature: Keycloak Provisioning Steps

- **Description**: 实现 Organization、Role、User、Membership、Role Assignment 的 ensure 行为。
- **Inputs**: Desired State、KeycloakAdminPort。
- **Outputs**: 更新后的 Desired State。
- **Behavior**: 调用 Port，处理已存在对象，更新远端标识和子目标状态。

### Capability: Keycloak Integration Boundary

隔离 Keycloak Admin API，并把外部系统行为翻译成本 BC 语义。

#### Feature: KeycloakAdminPort

- **Description**: 定义 Application 层依赖的身份系统业务能力接口。
- **Inputs**: Value Objects 和业务规格对象。
- **Outputs**: `OrganizationIdentity`、`UserIdentity` 或 void。
- **Behavior**: 接口不包含 Keycloak SDK、HTTP、realm path 等技术细节。

#### Feature: FakeKeycloakAdminAdapter

- **Description**: 为 MVP 测试提供内存版 Keycloak 行为。
- **Inputs**: Port 方法调用。
- **Outputs**: 可查询的内存 Organization、User、Membership、Role Assignment。
- **Behavior**: 模拟 409、超时和已存在对象，支持幂等测试。

#### Feature: KeycloakAdminAdapter

- **Description**: 调用真实 Keycloak Admin REST API。
- **Inputs**: Port 方法调用、Vault 中的 client credentials。
- **Outputs**: Domain Value Objects 或 Domain Exceptions。
- **Behavior**: 管理 Admin Token 缓存，翻译错误，处理 409 幂等路径。

### Capability: Persistence, Outbox, and Retry Scheduling

持久化 provisioning 状态、可靠发布事件并恢复失败任务。

#### Feature: Desired State Repository

- **Description**: 持久化和查询 `TenantIamDesiredState`。
- **Inputs**: Aggregate。
- **Outputs**: DB rows 或 Aggregate。
- **Behavior**: 使用 Spring Data JDBC；`tenant_id` 唯一；支持 `findReadyForRetry`。

#### Feature: Flyway Migrations

- **Description**: 创建 desired state、索引和 outbox 所需 schema。
- **Inputs**: SQL migration。
- **Outputs**: PostgreSQL schema。
- **Behavior**: 包含唯一约束、状态字段、重试字段、审计字段。

#### Feature: Outbox Publisher

- **Description**: 在本地事务中写入 Completed/Failed 集成事件。
- **Inputs**: Domain event。
- **Outputs**: outbox row。
- **Behavior**: 与 desired state 状态更新同事务提交，供 Debezium CDC 投递。

#### Feature: RetryScheduler

- **Description**: 周期性扫描到期的 `AWAITING_RETRY` 记录并重新驱动 Pipeline。
- **Inputs**: 当前时间。
- **Outputs**: 被认领的 tenant IDs。
- **Behavior**: 使用 `FOR UPDATE SKIP LOCKED` 避免多实例重复重试。

### Capability: Published Language and Downstream Handoff

以稳定事件契约通知下游 BC。

#### Feature: TenantIamProvisioningCompleted Event

- **Description**: 表达租户 IAM 身份基础设施已就绪。
- **Inputs**: Completed desired state。
- **Outputs**: outbox event payload。
- **Behavior**: 包含 tenant、organization、admin user、occurredAt、correlationId。

#### Feature: TenantIamProvisioningFailed Event

- **Description**: 表达租户 IAM provisioning 已失败且需要下游或人工响应。
- **Inputs**: Failed desired state。
- **Outputs**: outbox event payload。
- **Behavior**: 包含 failedStep、failureCode、failureMessage、retryCount。

#### Feature: Authorization Handoff Contract

- **Description**: 为 Authorization BC 初始化空 Policy 提供事件输入。
- **Inputs**: Completed event。
- **Outputs**: 不在本 BC 内直接处理。
- **Behavior**: 只发布事实，不调用 Authorization BC API。

### Capability: Admin Operations and Observability

提供状态查询、人工恢复和运行指标。

#### Feature: Provisioning Status Query

- **Description**: 查询租户 IAM provisioning 当前状态和失败详情。
- **Inputs**: `tenantId`。
- **Outputs**: 状态响应 DTO。
- **Behavior**: 返回 status、steps、retryCount、nextRetryAt、failureCode。

#### Feature: Manual Retry API

- **Description**: 管理员触发失败租户重新进入 Pipeline。
- **Inputs**: `tenantId`、operator identity、reason。
- **Outputs**: Accepted response 和审计记录。
- **Behavior**: 仅允许 Platform Admin；将 `FAILED` 重置为 `PENDING` 或 `IN_PROGRESS`。

#### Feature: Metrics and Structured Logging

- **Description**: 提供 provisioning 成功率、失败率、重试和卡顿状态。
- **Inputs**: Pipeline 生命周期事件。
- **Outputs**: logs、Micrometer metrics。
- **Behavior**: 所有日志携带 tenantId、correlationId、stepName。

# Structural Decomposition

## Repository Structure

当前 `identity-access` 目录尚未落地真实 Spring Boot service module。第一版实现建议创建明确 deployable service，避免把 IAM Provisioning 混入 token enrichment。

```text
services/control-plane/identity-access/
└── iam-provisioning-service/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/datahub/identity/iamprovisioning/
        │   │   ├── IamProvisioningApplication.java
        │   │   ├── interfaces/
        │   │   │   ├── messaging/
        │   │   │   └── admin/
        │   │   ├── application/
        │   │   │   ├── TenantIamProvisioningApplicationService.java
        │   │   │   ├── command/
        │   │   │   ├── port/
        │   │   │   └── step/
        │   │   ├── domain/
        │   │   │   ├── model/
        │   │   │   ├── event/
        │   │   │   ├── exception/
        │   │   │   └── repository/
        │   │   ├── infrastructure/
        │   │   │   ├── keycloak/
        │   │   │   ├── persistence/
        │   │   │   ├── outbox/
        │   │   │   └── scheduler/
        │   │   └── config/
        │   └── resources/
        │       ├── application.yml
        │       └── db/migration/
        └── test/
            └── java/com/datahub/identity/iamprovisioning/
```

If the service module is deferred, the same package structure can be first implemented under an existing identity-access service, but the package boundary must still reflect this deployable boundary.

## Module Definitions

### Module: domain/model

- **Maps to capability**: Desired State Domain Model
- **Responsibility**: Own aggregate, value objects, status enum, retry policy and invariant checks.
- **Exports**:
  - `TenantIamDesiredState`
  - `ProvisioningStatus`
  - `TenantId`
  - `OrganizationIdentity`
  - `UserIdentity`
  - `RetryPolicy`

### Module: domain/event

- **Maps to capability**: Published Language and Downstream Handoff
- **Responsibility**: Define completed/failed events in business language.
- **Exports**:
  - `TenantIamProvisioningCompleted`
  - `TenantIamProvisioningFailed`

### Module: application/command

- **Maps to capability**: Event Intake and Boundary Translation
- **Responsibility**: Internal commands consumed by Application Service.
- **Exports**:
  - `StartTenantIamProvisioningCommand`
  - `ManualRetryTenantIamProvisioningCommand`

### Module: application/port

- **Maps to capability**: Keycloak Integration Boundary and Persistence Boundary
- **Responsibility**: Define ports required by Application layer.
- **Exports**:
  - `KeycloakAdminPort`
  - `TenantIamDesiredStateRepository`
  - `OutboxEventPublisher`

### Module: application/step

- **Maps to capability**: Idempotent Provisioning Pipeline
- **Responsibility**: Implement provisioning steps using ports.
- **Exports**:
  - `ProvisioningStep`
  - `CreateOrganizationStep`
  - `VerifyPlatformRolesStep`
  - `CreateAdminUserStep`
  - `JoinOrganizationStep`
  - `AssignTenantAdminRoleStep`

### Module: application service

- **Maps to capability**: Pipeline orchestration
- **Responsibility**: Coordinate command handling, pipeline execution, retry transitions and outbox writes.
- **Exports**:
  - `TenantIamProvisioningApplicationService`

### Module: interfaces/messaging

- **Maps to capability**: Event Intake and ACL Translation
- **Responsibility**: Consume Kafka events and translate external schema to internal command.
- **Exports**:
  - `TenantLifecycleEventListener`
  - `TenantLifecycleEventTranslator`

### Module: interfaces/admin

- **Maps to capability**: Admin Operations
- **Responsibility**: Expose status and retry endpoints.
- **Exports**:
  - `TenantIamProvisioningAdminController`

### Module: infrastructure/keycloak

- **Maps to capability**: Keycloak Integration Boundary
- **Responsibility**: Implement `KeycloakAdminPort` against real Keycloak or fake adapter.
- **Exports**:
  - `KeycloakAdminAdapter`
  - `FakeKeycloakAdminAdapter`
  - `KeycloakAdminTokenProvider`

### Module: infrastructure/persistence

- **Maps to capability**: Persistence and Retry Scheduling
- **Responsibility**: Spring Data JDBC repository, row mapper, SQL queries.
- **Exports**:
  - `JdbcTenantIamDesiredStateRepository`
  - `TenantIamDesiredStateRow`

### Module: infrastructure/outbox

- **Maps to capability**: Outbox publishing
- **Responsibility**: Persist outbox events in same transaction as state changes.
- **Exports**:
  - `JdbcOutboxEventPublisher`
  - `OutboxEventRow`

### Module: infrastructure/scheduler

- **Maps to capability**: Retry scheduling
- **Responsibility**: Poll due retry records and call Application Service.
- **Exports**:
  - `RetryScheduler`

# Dependency Chain

## Foundation Layer (Phase 0)

No dependencies beyond Java 21 and test libraries.

- **domain-model**: Provides value objects, aggregate, status enum and retry policy.
- **domain-events**: Provides completed/failed event classes.
- **application-commands**: Provides internal command objects.
- **domain-exceptions**: Provides business exceptions and failure codes.

## Port and Contract Layer (Phase 1)

- **application-ports**: Depends on [domain-model, domain-events, domain-exceptions].
- **provisioning-step-contract**: Depends on [domain-model, application-ports].
- **published-language-schema**: Depends on [domain-events].

## Application Layer (Phase 2)

- **provisioning-steps**: Depends on [provisioning-step-contract, application-ports, domain-model].
- **pipeline-orchestrator**: Depends on [domain-model, provisioning-steps, application-ports, domain-events].
- **manual-retry-use-case**: Depends on [pipeline-orchestrator, domain-model].

## Infrastructure Layer (Phase 3)

- **jdbc-desired-state-repository**: Depends on [application-ports, domain-model, Flyway schema].
- **jdbc-outbox-publisher**: Depends on [application-ports, domain-events, Flyway schema].
- **fake-keycloak-adapter**: Depends on [application-ports, domain-model].
- **keycloak-admin-adapter**: Depends on [application-ports, domain-model, domain-exceptions, config].
- **retry-scheduler**: Depends on [jdbc-desired-state-repository, pipeline-orchestrator].

## Interface Layer (Phase 4)

- **message-listener-and-translator**: Depends on [application-commands, pipeline-orchestrator].
- **admin-controller**: Depends on [manual-retry-use-case, jdbc-desired-state-repository].
- **health-and-metrics**: Depends on [pipeline-orchestrator, repository, scheduler].

## End-to-End Layer (Phase 5)

- **happy-path-flow**: Depends on [message-listener, fake-keycloak-adapter, jdbc repositories, outbox].
- **retry-flow**: Depends on [retry-scheduler, fake-keycloak-adapter failure injection].
- **outbox-contract-flow**: Depends on [pipeline-orchestrator, jdbc-outbox-publisher].

# Development Roadmap

## MVP Requirements

MVP must deliver a working IAM Provisioning BC slice using Fake Keycloak Adapter and real local PostgreSQL persistence.

MVP includes:

- Domain model and state machine.
- Flyway migration for desired state and outbox.
- Application Service and Step Pipeline.
- Fake Keycloak Adapter with failure injection.
- Repository and outbox implementation.
- RetryScheduler using database polling.
- Message listener boundary or direct test runner that simulates event intake.
- Admin status and retry endpoints.
- Unit and integration tests for idempotency and retry.

MVP does not include:

- Full Keycloak production adapter unless needed for integration validation.
- BYO IdP provisioning.
- Per-tenant OAuth2 Client.
- Admin Console UI.
- Authorization BC implementation beyond event contract.

## Future Enhancements

- Real Keycloak Admin REST Adapter with Testcontainers Keycloak integration tests.
- Vault-backed service account secret loading.
- External IdP provisioning for Enterprise tenants.
- Protocol Mapper verification during platform initialization.
- Full Debezium outbox integration test.
- Operator dashboard for failed provisioning and retry.
- TenantIamProvisioningFailed consumer in Tenant Management BC.

# Implementation Roadmap

## Phase 0: Domain Foundation

**Goal**: Establish the core language and invariants of IAM Provisioning BC.

**Entry Criteria**: Existing DDD and PRD docs are available.

**Tasks**:

- [ ] Implement value objects and enums (depends on: none)
  - Acceptance criteria: invalid tenant IDs, emails and statuses are rejected.
  - Test strategy: unit tests for constructors and equality.

- [ ] Implement `TenantIamDesiredState` aggregate (depends on: value objects)
  - Acceptance criteria: legal state transitions pass; illegal transitions fail.
  - Test strategy: unit tests for mark and record methods.

- [ ] Implement retry policy (depends on: value objects)
  - Acceptance criteria: retry count maps to backoff, max retry and alert level.
  - Test strategy: deterministic jitter abstraction in tests.

**Exit Criteria**: Domain model compiles and unit tests prove state invariants.

**Delivers**: Developers can model a tenant IAM provisioning lifecycle without Keycloak, Kafka or DB.

## Phase 1: Ports, Commands and Step Contracts

**Goal**: Define Application layer contracts before infrastructure exists.

**Entry Criteria**: Phase 0 complete.

**Tasks**:

- [ ] Implement command objects (depends on: domain foundation)
- [ ] Define `KeycloakAdminPort` (depends on: domain foundation)
- [ ] Define repository and outbox ports (depends on: domain foundation, domain events)
- [ ] Define `ProvisioningStep` interface (depends on: KeycloakAdminPort)

**Exit Criteria**: Application contracts compile without infrastructure dependencies.

**Delivers**: Stable seams for TDD, fake adapters and production adapters.

## Phase 2: Pipeline and Fake Keycloak

**Goal**: Make the core provisioning flow executable in memory.

**Entry Criteria**: Phase 1 complete.

**Tasks**:

- [ ] Implement provisioning steps (depends on: step contract, KeycloakAdminPort)
- [ ] Implement `TenantIamProvisioningApplicationService` (depends on: steps, repository port, outbox port)
- [ ] Implement Fake Keycloak Adapter (depends on: KeycloakAdminPort)
- [ ] Add failure injection to Fake Adapter (depends on: Fake Adapter)

**Exit Criteria**: Tests can run happy path, duplicate run and partial failure retry without PostgreSQL or real Keycloak.

**Delivers**: Usable core flow with observable domain state.

## Phase 3: Persistence and Outbox

**Goal**: Make desired state, retry state and integration events durable.

**Entry Criteria**: Phase 2 complete.

**Tasks**:

- [ ] Add Flyway migration for `tenant_iam_desired_state` (depends on: domain model)
- [ ] Add Flyway migration or local table for outbox events (depends on: domain events)
- [ ] Implement JDBC desired state repository (depends on: migrations, repository port)
- [ ] Implement JDBC outbox publisher (depends on: migrations, outbox port)
- [ ] Add repository integration tests (depends on: JDBC repository)

**Exit Criteria**: Desired state and outbox rows persist correctly in PostgreSQL.

**Delivers**: Durable state machine ready for retry and event publishing.

## Phase 4: Retry and Admin Operations

**Goal**: Recover failed provisioning automatically and manually.

**Entry Criteria**: Phase 3 complete.

**Tasks**:

- [ ] Implement `RetryScheduler` database polling (depends on: JDBC repository, Application Service)
- [ ] Add `FOR UPDATE SKIP LOCKED` query (depends on: JDBC repository)
- [ ] Implement status query endpoint (depends on: JDBC repository)
- [ ] Implement manual retry endpoint (depends on: Application Service)
- [ ] Add metrics and structured logs (depends on: Application Service)

**Exit Criteria**: Failed records move to retry, scheduler re-drives due records, admins can inspect and retry failed tenants.

**Delivers**: Operable provisioning workflow.

## Phase 5: Event Interface and Contract Tests

**Goal**: Connect the BC to upstream and downstream event boundaries.

**Entry Criteria**: Phase 4 complete.

**Tasks**:

- [ ] Implement event listener and ACL translator (depends on: commands, Application Service)
- [ ] Implement duplicate event claim behavior (depends on: JDBC repository)
- [ ] Implement Completed and Failed event serialization (depends on: domain events, outbox)
- [ ] Add contract tests for event payloads (depends on: event serialization)
- [ ] Document event naming decision (`TenantCreated` vs `TenantInfrastructureProvisionedEvent`) (depends on: listener)

**Exit Criteria**: Event intake triggers pipeline, duplicate events are safe, outbox payloads match contract.

**Delivers**: End-to-end event boundary for Task Master follow-up tasks.

## Phase 6: Real Keycloak Adapter

**Goal**: Replace Fake Adapter with real Keycloak integration behind the same Port.

**Entry Criteria**: Phase 5 complete.

**Tasks**:

- [ ] Implement Admin Token provider (depends on: config, Vault/secret abstraction)
- [ ] Implement Organization API calls (depends on: token provider)
- [ ] Implement User, Membership and Role Assignment calls (depends on: token provider)
- [ ] Implement 409 recovery paths (depends on: Keycloak calls)
- [ ] Add integration tests with Keycloak test environment (depends on: adapter)

**Exit Criteria**: Real Keycloak adapter passes the same behavior tests as Fake Adapter.

**Delivers**: Production-ready IdP integration.

# Logical Dependency Chain

1. Domain model first, because all later modules depend on stable state names, value objects and invariants.
2. Ports second, because Application code should be written against interfaces before infrastructure.
3. Fake Adapter third, because idempotency and retry behavior must be testable without Keycloak.
4. Pipeline fourth, because it is the main business behavior and should be validated before persistence complexity.
5. Persistence fifth, because durable state and outbox are required for real event-driven operation.
6. Retry scheduler sixth, because it depends on persisted `AWAITING_RETRY` state.
7. Event listener seventh, because it should call an already-tested Application Service.
8. Admin operations eighth, because they operate over persisted state and Application Service commands.
9. Real Keycloak adapter last, because it should satisfy an already-proven Port contract.

# Test Strategy

## Test Pyramid

```text
        /\
       /E2E\          10% - Spring Boot flow with DB and fake/real adapters
      /------\
     /Integration\    30% - repository, outbox, scheduler, listener
    /------------\
   /  Unit Tests  \   60% - domain model, steps, retry policy, translators
  /----------------\
```

## Coverage Requirements

- Domain and Application layer branch coverage: 85% minimum.
- Infrastructure line coverage: 70% minimum.
- Critical path scenarios must be explicitly tested even if global coverage passes.

## Critical Test Scenarios

### TenantIamDesiredState

**Happy path**:

- Create PENDING state, mark IN_PROGRESS, mark each subgoal, mark COMPLETED.
- Expected: state is `COMPLETED`, all subgoal flags are true.

**Edge cases**:

- Long-running PENDING state can be queried.
- Expected: repository can identify stale PENDING records for alerting.

**Error cases**:

- Mark COMPLETED before required subgoals.
- Expected: domain exception.

### Event Intake

**Happy path**:

- Valid `TenantCreated` event translates to `StartTenantIamProvisioningCommand`.
- Expected: command carries strong typed IDs and admin email.

**Error cases**:

- Missing tenant ID or invalid email.
- Expected: event rejected with structured log and no desired state row.

**Duplicate cases**:

- Same tenant event delivered twice.
- Expected: first insert succeeds, second is treated as duplicate.

### Step Pipeline

**Happy path**:

- Run pipeline with Fake Keycloak.
- Expected: one Organization, one Admin User, one Membership, one Role Assignment, completed outbox event.

**Idempotency**:

- Run pipeline twice for same tenant.
- Expected: no duplicate remote objects and no failure.

**Partial failure**:

- Fail after Organization creation.
- Expected: retry skips Organization and completes remaining steps.

### KeycloakAdminPort

**Happy path**:

- Fake Adapter implements all Port methods.
- Expected: Application tests pass without infrastructure types.

**409 path**:

- Adapter simulates already-existing Organization.
- Expected: returns existing `OrganizationIdentity` and does not fail.

### Persistence and Retry

**Happy path**:

- Save desired state and reload.
- Expected: all status fields and identities preserved.

**Concurrency**:

- Two transactions attempt same tenant insert.
- Expected: one success, one unique constraint failure handled as duplicate.

**Scheduler**:

- Multiple scheduler instances poll due retry rows.
- Expected: `FOR UPDATE SKIP LOCKED` prevents duplicate claim.

### Outbox Events

**Completed event**:

- Pipeline completes.
- Expected: desired state and outbox row committed atomically.

**Failed event**:

- Retry exhausted.
- Expected: `TenantIamProvisioningFailed` outbox row includes failedStep and failureCode.

## Test Generation Guidelines

- Prefer AssertJ for readable assertions.
- Use JUnit 5.
- Keep Domain tests free of Spring context.
- Use Testcontainers PostgreSQL for repository and scheduler integration tests.
- Use Fake Keycloak Adapter for most Application integration tests.
- Real Keycloak tests should be isolated and not required for every unit-level run.

# Technical Architecture

## System Components

| Component | Responsibility |
| --- | --- |
| Event Listener | Consume upstream lifecycle event |
| ACL Translator | Translate upstream event to internal command |
| Application Service | Orchestrate provisioning pipeline and state transitions |
| TenantIamDesiredState | Own provisioning state and invariants |
| Provisioning Steps | Execute idempotent Keycloak target-state changes |
| KeycloakAdminPort | Business-language interface to IdP |
| KeycloakAdminAdapter | Real Keycloak implementation |
| FakeKeycloakAdminAdapter | Deterministic test implementation |
| DesiredStateRepository | Persist aggregate and retry state |
| OutboxPublisher | Persist integration events atomically |
| RetryScheduler | Re-drive due failed workflows |
| Admin Controller | Status query and manual retry |

## Data Models

### tenant_iam_desired_state

```sql
CREATE TABLE tenant_iam_desired_state (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    tenant_name VARCHAR(255) NOT NULL,
    tier VARCHAR(64) NOT NULL,
    admin_email VARCHAR(320) NOT NULL,
    overall_status VARCHAR(64) NOT NULL,
    organization_id VARCHAR(255),
    admin_user_id VARCHAR(255),
    organization_created BOOLEAN NOT NULL DEFAULT FALSE,
    platform_roles_verified BOOLEAN NOT NULL DEFAULT FALSE,
    admin_user_created BOOLEAN NOT NULL DEFAULT FALSE,
    admin_user_joined_organization BOOLEAN NOT NULL DEFAULT FALSE,
    tenant_admin_role_assigned BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    next_retry_at TIMESTAMPTZ,
    failed_step VARCHAR(128),
    failure_code VARCHAR(128),
    failure_message TEXT,
    source_event_id VARCHAR(128),
    correlation_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_tenant_iam_desired_state_tenant UNIQUE (tenant_id)
);
```

### outbox_events

If the service has no shared outbox table yet:

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128),
    published_at TIMESTAMPTZ
);
```

## APIs

### Status Query

```text
GET /admin/iam-provisioning/{tenantId}
```

Response contains status, step flags, retry info and failure details.

### Manual Retry

```text
POST /admin/iam-provisioning/{tenantId}/retry
```

Only Platform Admin can call this endpoint. The request should include an operator reason for auditability.

## Technology Stack

- Java 21
- Spring Boot 3.x
- Virtual Threads
- Spring Data JDBC
- PostgreSQL
- Flyway
- Kafka / Spring Cloud Stream
- Outbox Pattern + Debezium CDC
- Keycloak Admin REST API
- Micrometer / OpenTelemetry

## Key Decisions

**Decision: Desired State instead of Saga compensation**

- **Rationale**: IAM provisioning steps can be made idempotent.
- **Trade-offs**: Requires careful state persistence after each step.
- **Alternatives considered**: Saga rollback by deleting Organization/User, rejected due to compensation complexity and failure of compensation itself.

**Decision: Database polling retry instead of Redis delay queue**

- **Rationale**: Retry state is business-critical and must remain transactionally consistent with desired state.
- **Trade-offs**: Polling query exists, but volume is low and indexed.
- **Alternatives considered**: Redis Sorted Set, rejected due to dual-write/state-splitting risk.

**Decision: Platform-level Shared Client**

- **Rationale**: Organization carries tenant identity; Client represents platform application.
- **Trade-offs**: Per-tenant token customization deferred.
- **Alternatives considered**: Per-tenant OAuth2 Client, rejected for MVP due to O(N) client lifecycle and no current requirement.

**Decision: Realm Role for platform roles**

- **Rationale**: Stable JWT propagation through `realm_access.roles`.
- **Trade-offs**: Organization Role semantics are not used for platform authorization in MVP.
- **Alternatives considered**: Organization Role, deferred until token representation and downstream support are explicitly validated.

# Risks and Mitigations

## Technical Risks

**Risk**: Event trigger name mismatch between DDD (`TenantCreated`) and existing guide (`TenantInfrastructureProvisionedEvent`).

- **Impact**: Medium.
- **Likelihood**: Medium.
- **Mitigation**: Use internal `StartTenantIamProvisioningCommand` and isolate source event mapping in ACL.
- **Fallback**: Support both event types temporarily with explicit contract tests.

**Risk**: Keycloak Organization API behavior differs across versions.

- **Impact**: High.
- **Likelihood**: Medium.
- **Mitigation**: Keep all Keycloak details in Adapter and add integration tests against pinned Keycloak version.
- **Fallback**: Change Adapter implementation without changing Application layer.

**Risk**: Step success but local state save fails.

- **Impact**: High.
- **Likelihood**: Medium.
- **Mitigation**: Adapter methods must be idempotent; retry can rediscover existing remote object and update state.
- **Fallback**: Add reconciliation query methods to Port for recovery.

**Risk**: Outbox not wired to Debezium in local MVP.

- **Impact**: Medium.
- **Likelihood**: High.
- **Mitigation**: Persist outbox row first; Debezium integration can be separate phase.
- **Fallback**: Provide test-only outbox reader to assert event creation.

## Dependency Risks

- Tenant Management event schema may not be final.
- Keycloak service account permissions may not be ready.
- Shared outbox conventions may not yet exist across services.
- Root Maven aggregator may need a new service module entry.

## Scope Risks

- BYO IdP provisioning can expand the scope substantially. Keep it future enhancement.
- Real Keycloak adapter can delay MVP. Start with Fake Adapter and contract tests.
- Admin UI is not required for first PRD. Provide API and observability only.

# Appendix

## References

- `services/control-plane/identity-access/.taskmaster/docs/IAM provision BC-DDD.md`
- `services/control-plane/identity-access/docs/plans/2026-05-21-tenant-iam-onboarding-design.md`
- `services/control-plane/identity-access/agent.md`
- Root `AGENTS.md`

## Glossary

- **BC**: Bounded Context.
- **Desired State**: The target state the system keeps reconciling toward.
- **Outbox Pattern**: Pattern that writes integration events into the local DB transaction before async publication.
- **ACL**: Anti-Corruption Layer, translating external language into internal language.
- **OHS**: Open Host Service, an external system exposing a stable interface.
- **Published Language**: Public event schema used by downstream contexts.
- **Realm Role**: Keycloak realm-level role, visible in JWT role claims.
- **Organization**: Keycloak tenant-level identity boundary inside shared realm.

## Open Questions

1. Should the source event contract be finalized as `TenantCreated`, `TenantInfrastructureProvisionedEvent`, or both during migration?
2. Should IAM Provisioning BC become its own deployable `iam-provisioning-service`, or be temporarily hosted in an existing identity-access service until service boundaries are scaffolded?
3. What exact alerting backend will receive WARNING, ERROR and CRITICAL provisioning alerts?
4. Should Completed/Failed event schemas live in `contracts/` immediately or remain local until a second consumer is implemented?
5. What is the production Keycloak version to pin for Organization API integration tests?

</PRD>
