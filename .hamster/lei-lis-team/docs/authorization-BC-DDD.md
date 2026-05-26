**CDP 多租户平台**

**Identity & Access Layer**

DDD 设计与实施文档

_Sessions A - E 完整设计记录 | 版本 1.0_

# **目录**

**第一章** 项目背景与架构目标

**第二章** 战略设计 - 域与限界上下文（Session A）

**第三章** Context Map - BC 间协作关系（Session B）

**第四章** 战术设计 - Authorization BC 内部结构（Session C）

**第五章** 服务边界决策（Session D）

**第六章** API 契约与 Tenant Context 传播（Session E）

**第七章** 架构债务与演进路径

# **第一章 项目背景与架构目标**

## **1.1 项目定位**

本项目构建一个 CDP（Customer Data Platform）多租户数据源管理平台，对标 Salesforce Data Cloud 的平台组件能力。平台需要适配数量庞大的异构数据源类型，支持两大数据源类别：租户自有数据源（BYOD / BYODS，平台仅充当连接代理）以及平台托管数据源（由平台统一管理生命周期）。

Identity & Access Layer 是整个平台的安全基础，它回答两个核心问题：Who are you（认证），以及 What can you do（授权）。本文档记录了通过 Domain-Driven Design（DDD）方法对这一层进行系统化设计的全过程。

## **1.2 技术栈约束**

| **组件**   | **技术选型**                  | **职责说明**                          |
| ---------- | ----------------------------- | ------------------------------------- |
| API 网关   | Envoy Gateway                 | JWT 验证 + 静态 RBAC + Header 注入    |
| 身份提供商 | Keycloak                      | OIDC / JWT 签发，Realm 级角色管理     |
| 应用框架   | Spring Boot 3.x + Java 21     | Virtual Threads，Spring Security ABAC |
| 数据库     | PostgreSQL Schema-per-Tenant  | 控制平面元数据隔离                    |
| 消息队列   | Kafka（Spring Cloud Stream）  | 领域事件异步投递                      |
| 可靠投递   | Outbox Pattern + Debezium CDC | 事件与状态变更原子性保证              |

## **1.3 核心架构决策总览**

整个 Identity & Access Layer 建立在一个"双层授权"的核心架构决策之上：

- Envoy Gateway 在入口处执行粗粒度 RBAC，判断"JWT 持有的平台角色是否有权访问该 API 端点"，完全基于 JWT Claims 的静态匹配，无数据库查询
- Spring Security 在方法级执行细粒度 ABAC，判断"在满足 RBAC 前提下，具体资源访问是否满足策略条件"，需要资源属性参与评估

_⚠ 这两层的职责边界不可混淆：RBAC 在 Envoy 处完成，ABAC 在应用层完成。任何试图在 Envoy 层做动态数据库查询（如 Ext-Authz 模式）的方案都会在关键转发路径上引入不可接受的延迟。_

# **第二章 战略设计 - 域与限界上下文**

## **2.1 Domain 识别**

通过事件风暴（Event Storming）和领域专家访谈，识别出 Identity & Access Layer 涉及三个 Domain：

| **Domain 名称**   | **类型**          | **说明**                                                                                         |
| ----------------- | ----------------- | ------------------------------------------------------------------------------------------------ |
| Authorization     | Core Domain ⭐    | 平台差异化能力所在。多租户细粒度授权策略评估，自定义角色管理。无法外购替代，是平台的核心竞争力。 |
| Tenant Management | Supporting Domain | 租户生命周期、成员关系管理。业务上必要，但不构成差异化竞争优势，可考虑外购 SaaS 替代。           |
| Audit             | Generic Subdomain | 操作审计日志记录。完全通用，市面上有成熟解决方案，长期可替换为第三方服务。                       |

## **2.2 限界上下文（Bounded Context）划分**

在三个 Domain 的基础上，划定五个 Bounded Context（BC）。每个 BC 内部维护自己独立的 Ubiquitous Language，同一个词在不同 BC 里可以有不同含义：

| **BC 名称**              | **Domain 类型** | **职责说明**                                                 |
| ------------------------ | --------------- | ------------------------------------------------------------ |
| Connector Registry BC    | Supporting      | 连接器类型注册表，管理平台支持的数据源连接器元数据           |
| DataSource Lifecycle BC  | Supporting      | 数据源的创建、激活、停用、归档全生命周期                     |
| Connection Governance BC | Core            | 连接池动态管理，租户级资源隔离（Bulkhead）                   |
| Tenant Configuration BC  | Supporting      | 租户级配置管理，包括 TenantTier（Starter/Growth/Enterprise） |
| Health Monitoring BC     | Generic         | 数据源连接健康检查与告警                                     |

Identity & Access Layer 聚焦于以下三个核心 BC：

### **Tenant Management BC**

**角色：**Upstream / Supplier（上游 / 供应方）

负责租户的完整元数据管理：Tenant 实体（tenantId、tenantName、status、tier）、TenantMember 实体（userId、email、membershipRole）、以及 TenantStatus 值对象（PROVISIONING / ACTIVE / SUSPENDED / TERMINATED）。

此 BC 是数据链的权威来源（Source of Truth）--任何需要"这个用户是否属于这个租户"信息的下游 BC，都必须通过事件或 API 从此 BC 获取，不能自行维护租户成员关系的副本。

### **Authorization BC（Core Domain）**

**角色：**Downstream，同时作为 authorization-service 对外的 Upstream

平台差异化能力的核心承载者。管理平台预定义角色与租户自定义角色的评估逻辑，是 ABAC 策略的存储与执行引擎。详细的战术设计见第四章。

### **Audit BC**

**角色：**Downstream / Conformist（顺从方）

异步消费来自 Authorization BC 和 Tenant Management BC 的领域事件，持久化为可查询的审计日志。采用 Conformist 模式意味着它直接使用上游的事件 Schema，不做 ACL 翻译，上游 Schema 变更时它必须跟随更新。

# **第三章 Context Map - BC 间协作关系**

## **3.1 Context Map 全景**

Context Map 描述各 BC 之间的协作模式，以及数据流的方向和信任关系。以下是完整的协作关系矩阵：

| **协作关系**                            | **模式**                        | **说明**                                                                                                                                                           |
| --------------------------------------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Keycloak → 我们的系统                   | OHS / Published Language        | Keycloak 是外部系统，通过 OIDC 标准协议（JWT）向下游提供身份信息。ACL 翻译层（KeycloakIdentityTranslator）隔离 Keycloak 内部格式变化对 Domain 的影响               |
| Tenant Management BC → Authorization BC | Customer / Supplier             | Tenant Management BC（上游）发布 TenantMembershipChanged 事件；Authorization BC（下游）消费事件，Authorization BC 侧有 ACL 翻译层防止上游变化直接影响 Domain Model |
| Authorization BC → Audit BC             | Published Language / Conformist | Authorization BC 以 Published Language 形式发布 AuthorizationDecisionMade 事件；Audit BC 以 Conformist 模式直接采用上游的事件 Schema                               |
| Tenant Management BC → Audit BC         | Published Language / Conformist | Tenant Management BC 发布 TenantLifecycleEvent；Audit BC 以 Conformist 模式消费                                                                                    |

## **3.2 ACL 翻译层的设计**

Anti-Corruption Layer（ACL，防腐层）是 DDD 中保护 Domain Model 不受外部系统格式污染的核心手段。在我们的架构里，有两个 ACL 翻译组件：

**KeycloakIdentityTranslator：**位于 Envoy Gateway 和应用内 Filter 之间。将 JWT Claims（Keycloak 的发布格式）翻译成系统内部的 AuthenticatedIdentity 概念。当 Keycloak 升级或修改 Claim 格式时，只需修改这一个翻译层，不影响任何 Domain 代码。

**TenantContextFilter：**将 Envoy 注入的 HTTP Header（X-Tenant-ID、X-User-ID、X-Platform-Roles）翻译成 Spring Security 的 TenantAwareAuthentication 对象和 TenantContextHolder 中的值。

# **第四章 战术设计 - Authorization BC 内部结构**

## **4.1 Aggregate 识别**

Authorization BC 内部确认三个 Aggregate Root，它们各自有独立的事务边界，相互之间只通过 ID 引用：

### **Role Aggregate**

| **元素类型**   | **名称 / 值** | **说明**                                               |
| -------------- | ------------- | ------------------------------------------------------ |
| Aggregate Root | Role          | 租户自定义角色的定义，携带角色名、描述、所属租户       |
| Value Object   | RoleName      | 不可变，格式约束：大写字母+下划线，如 REGIONAL_STEWARD |
| Value Object   | TenantId      | 不可变，格式：slug 字符串，如 tenant-acme-corp         |
| 特征           | 写少读多      | 角色定义的修改频率极低，是管理操作，不在热路径上       |

### **RoleAssignment Aggregate**

| **元素类型**   | **名称 / 值**    | **说明**                                                         |
| -------------- | ---------------- | ---------------------------------------------------------------- |
| Aggregate Root | RoleAssignment   | 主体（用户）与角色的绑定关系，包含 assignedAt、expiresAt、status |
| Value Object   | SubjectId        | 对应 JWT 的 sub Claim（Keycloak User UUID）                      |
| Value Object   | AssignmentStatus | 枚举：ACTIVE / EXPIRED / REVOKED                                 |
| 特征           | 授权热路径       | 每次 ABAC 评估都需要查询，是整个 BC 访问频率最高的 Aggregate     |

### **Policy Aggregate**

| **元素类型**   | **名称 / 值**         | **说明**                                                      |
| -------------- | --------------------- | ------------------------------------------------------------- |
| Aggregate Root | Policy                | 租户的 ABAC 策略载体，携带核心业务方法 evaluate()             |
| Entity（内部） | PolicyRule            | 策略内的单条规则，有自己的 ID 但无独立生命周期                |
| Value Object   | Permission            | 格式 resource:action，如 datasource:read，不可变              |
| Value Object   | AuthorizationDecision | 枚举：ALLOW / DENY，携带 denyReason                           |
| 业务约束       | 单一生效策略          | 一个租户在任意时刻只有一个生效的 Policy，这是已确认的业务约束 |

## **4.2 Value Object 设计原则**

Value Object 的两个核心特征是不可变性（Immutability）和基于值的相等性（Value-based Equality）。以下 Value Object 的设计体现了这两个特征：

record Permission(String resource, String action) {

public Permission { // Compact Constructor 做格式验证

if (!resource.matches("\[a-z\]\[a-zA-Z\]+")) throw new IllegalArgumentException();

if (!action.matches("\[a-z\]\[a-zA-Z\]+")) throw new IllegalArgumentException();

}

public String value() { return resource + ":" + action; }

}

Java 21 的 Record 天然满足不可变性和值相等性，是实现 Value Object 的最佳语言特性。Permission("datasource","read").equals(Permission("datasource","read")) 永远为 true，因为相等性基于字段值，不基于对象引用。

## **4.3 Domain Service：AuthorizationEvaluationService**

当某些业务逻辑无法自然归属于某个 Aggregate 时（通常是跨 Aggregate 的协调逻辑），引入 Domain Service 来承载。AuthorizationEvaluationService 的职责是协调跨 Aggregate 的评估编排：

AuthorizationDecision evaluate(

SubjectId subjectId,

TenantId tenantId,

Set&lt;String&gt; platformRoles, // 已从 JWT 提取，Domain Service 不感知 JWT

Permission permission,

Map&lt;String, Object&gt; resourceAttributes

) → AuthorizationDecision

执行序列：① 查询 RoleAssignmentRepository 获取自定义角色 → ② 合并平台角色与自定义角色 → ③ 构建 EvaluationContext → ④ 查询 PolicyRepository 获取租户策略 → ⑤ 调用 Policy.evaluate() → ⑥ 向 Outbox 写入 AuthorizationDecisionMade 事件。

_⚠ Domain Service 本身对 JWT 解析一无所知。platformRoles 已由 Application Service 从 SecurityContext 中提取完毕后才传入。这保证了 Domain 层不依赖任何基础设施概念（HTTP、JWT）。_

## **4.4 Repository 接口定义**

Repository 的接口定义在 Domain 层，实现在 Infrastructure 层。Schema-per-Tenant 的隔离机制完全封装在 Infrastructure Layer，Domain Layer 的接口对此透明。

| **Repository**           | **核心查询方法**                                  | **说明**                                                                  |
| ------------------------ | ------------------------------------------------- | ------------------------------------------------------------------------- |
| RoleAssignmentRepository | findActiveBySubjectAndTenant(SubjectId, TenantId) | 热路径，Infrastructure 层有 Caffeine L1 + Redis L2 两层缓存，写时主动失效 |
| PolicyRepository         | findByTenantId(TenantId)                          | 返回包含所有 PolicyRule 的完整 Aggregate；每租户单一生效策略              |
| RoleRepository           | findByTenantId(TenantId)                          | 返回租户所有自定义角色定义；低频管理操作                                  |

## **4.5 Domain Event 设计**

领域事件使用 Outbox Pattern 保证发布可靠性--事件记录与 Aggregate 状态变更在同一个 PostgreSQL 事务里写入，由 Debezium CDC 异步投递到 Kafka。

| **事件名称**              | **发布时机**                       | **核心字段**                                                                                           |
| ------------------------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------ |
| AuthorizationDecisionMade | 每次 Policy.evaluate() 完成后      | { decisionId, subjectId, tenantId, permission, decision, denyReason, appliedPolicyRuleId, occurredAt } |
| RoleAssigned              | RoleAssignment 创建时              | { assignmentId, subjectId, roleId, tenantId, assignedAt, assignedBy }                                  |
| RoleRevoked               | RoleAssignment 状态变为 REVOKED 时 | { assignmentId, subjectId, roleId, tenantId, revokedAt, revokedBy }                                    |
| PolicyRuleUpdated         | PolicyRule 被增删改时              | { policyId, ruleId, tenantId, changeType, changedBy, occurredAt }                                      |

# **第五章 服务边界决策**

## **5.1 决策框架：Conway's Law**

服务边界决策的第一原则来自 Conway's Law：系统架构是团队沟通结构的映射。在做任何"要不要拆分"的决策之前，优先问"哪个团队负责这块"，而不是优先问"访问频率有多高"。访问频率的问题由缓存解决，Conway's Law 的问题只有团队结构调整才能解决。

## **5.2 决策一：三个 Aggregate 合并成单一 authorization-service**

结论：Role、RoleAssignment、Policy 三个 Aggregate 合并部署为一个 authorization-service，而不是按访问频率拆分为三个服务。

**核心推导逻辑：**角色定义、角色绑定、策略评估三件事在任何真实组织里都由同一个团队负责，它们之间的耦合是领域本质耦合，不是应该被解耦的技术耦合。强行拆分引入了分布式系统的复杂性，却得不到任何团队自治的收益。

**访问频率差异的解法：**RoleAssignment 的高频读取由 Caffeine（L1）+ Redis（L2）两层缓存吸收，真正触达数据库的请求占比极小。缓存，而不是服务拆分，是解决访问频率差异的正确工具。

**生产环境佐证：**Google Zanzibar（Google 的全球授权系统）和 AWS IAM 都采用单一授权服务架构，将角色、绑定、策略的评估逻辑集中在一个服务中，通过水平扩展和缓存应对高并发，而非服务拆分。

## **5.3 决策二：Envoy 执行基于 JWT Claims 的静态 RBAC**

结论：Envoy Gateway 执行纯静态的 RBAC 匹配，不引入 Ext-Authz 模式，不在转发路径上做任何动态数据库查询。

**为什么不用 Ext-Authz：**Ext-Authz 的价值在于让 Envoy 在转发前做动态查询，但我们的粗粒度 RBAC 完全基于 JWT 的静态信息，引入 Ext-Authz 只会在每个请求的关键路径上增加一次同步 RPC 调用，没有任何收益，只有延迟代价。

**JWT 只携带平台预定义角色：**TENANT_ADMIN、DATA_ENGINEER、VIEWER 等平台角色进入 JWT；REGIONAL_STEWARD 等租户自定义角色不进 JWT。三个原因：Keycloak 不感知自定义角色；JWT 有效期造成角色变更授权滞后；大量自定义角色导致 JWT 体积失控。

## **5.4 决策三：Spring Security 分层 ABAC**

结论：ABAC 评估采用"角色预筛 + 取后评估"的两步模式。

| **阶段**                        | **说明**                                                                                                                              |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| @PreAuthorize 角色预筛          | 在数据库查询之前执行，基于 SecurityContext 中的角色信息做粗筛，拦截明显无权限的请求，避免无效的数据库 IO。不需要资源属性。            |
| authzClient.evaluate() 取后评估 | 在资源从数据库取出之后执行，此时 EvaluationContext 三个维度（Subject/Resource/Action）全部就绪，由 authorization-service 做最终裁决。 |

Thin Client SDK（JAR 包）封装所有对 authorization-service 的调用细节。所有 Policy.evaluate() 逻辑集中在 authorization-service 里执行，业务服务不包含任何授权评估逻辑的副本。这是 Google Zanzibar 核心设计哲学的体现：评估逻辑中心化，客户端轻量化。

## **5.5 决策四：Tenant Context 传播链以 Envoy 为信任边界**

结论：Envoy Gateway 是整个系统唯一的信任边界，所有来自客户端的身份声明在 Envoy 处被验证并替换，下游服务只信任 Envoy 注入的 Header，永远不信任客户端直接传来的任何身份相关 Header。

_⚠ 如果下游服务（Spring Boot）直接读取并信任客户端传来的 X-Tenant-ID Header，会产生 IDOR（Insecure Direct Object Reference）漏洞--任何客户端都可以伪造 Header，声称自己是任意租户，读取那个租户的数据。这是 OWASP Top 10 长期上榜的攻击向量。_

## **5.6 四个决策的依赖关系**

这四个决策之间存在明确的推导依赖，不是独立的平行决策：

- 决策二（JWT 只含平台角色）→ 决定了 Spring Security 必须自行查询自定义角色（决策三的前提）
- 决策二（Envoy 注入可信 Header）→ 是整条传播链的数据来源（决策四的数据基础）
- 决策四（SecurityContext 和 TenantContextHolder）→ 是 EvaluationContext 的原材料（决策三的执行条件）
- 决策一（单一 authorization-service）→ Thin Client SDK 只需要一个调用目标，Thin Client 模式才能成立（决策三的基础设施前提）

# **第六章 API 契约与 Tenant Context 传播**

## **6.1 JWT Claim 结构**

JWT 是数据链的源头，其内容必须满足三个约束：Envoy 够用（包含 RBAC 所需的全部静态信息）、体积够小（不携带可变业务状态）、有效期内不失效（不携带随时可能变更的自定义角色）。

| **Claim 名称**     | **Claim 类型**                         | **格式与来源**                                                                |
| ------------------ | -------------------------------------- | ----------------------------------------------------------------------------- |
| iss                | Registered（Keycloak 自动填充）        | Keycloak Realm URL，如 <https://auth.example.com/realms/cdp>                  |
| sub                | Registered（Keycloak 自动填充）        | Keycloak User UUID，全局唯一用户标识                                          |
| aud                | Registered（Keycloak 自动填充）        | 受众列表，必须包含我们的 Client ID（如 cdp-api）                              |
| exp / iat / jti    | Registered（Keycloak 自动填充）        | 过期时间、签发时间、JWT 唯一 ID                                               |
| tenant_id          | Private Claim（Protocol Mapper 注入）  | slug 格式，如 tenant-acme-corp；来自 Keycloak User Attribute，创建后永不变更  |
| realm_access.roles | Public Claim（内置 Realm Role Mapper） | 平台预定义角色数组，如 \["TENANT_ADMIN","DATA_ENGINEER"\]；不含租户自定义角色 |

**Protocol Mapper 配置位置：**推荐在 Keycloak 的 Client Scope 级别（而非单个 Client 级别）配置。创建名为 cdp-platform-claims 的 Client Scope，将 tenant_id 的 User Attribute Mapper 放入其中，设为 Default Scope。这样当平台拥有多个 Client 时，只需配置一次，所有 Client 自动继承，变更时只改一处。

## **6.2 Envoy Header 规范**

Envoy 在转发请求时执行三个有序动作：① 剥除客户端传来的所有身份相关 Header（防 IDOR）→ ② 验证 JWT 签名、exp、aud（失败则直接 401）→ ③ 从通过验证的 Claims 提取信息注入为可信 Header。

| **Header 名称**  | **示例值**                           | **格式说明**                                                                              |
| ---------------- | ------------------------------------ | ----------------------------------------------------------------------------------------- |
| X-Tenant-ID      | tenant-acme-corp                     | 单个 slug 字符串，来自 JWT tenant_id Claim；空值时 Envoy 拒绝请求（配置错误信号）         |
| X-User-ID        | a1b2c3d4-e5f6-7890-abcd-ef1234567890 | 单个 UUID 字符串，来自 JWT sub Claim                                                      |
| X-Platform-Roles | TENANT_ADMIN,DATA_ENGINEER           | 逗号分隔字符串，来自 JWT realm_access.roles 数组；Envoy 默认将数组 Claim 序列化为逗号分隔 |

**关键安全语义：**上表中列出的三个 Header 名称，Envoy 会确保它们只能来自自己的注入，即使客户端在请求中携带了同名 Header，也会在剥除阶段被删除。这一行为由 Envoy Gateway 的 SecurityPolicy 内置保证，无需额外配置。

## **6.3 authorization-service API 契约**

### **协议选型**

现阶段采用 REST（HTTP/1.1 + JSON）。gRPC 作为性能优化方向，在 ABAC 评估成为可测量性能瓶颈时切换。因为 Thin Client SDK 封装了所有调用细节，协议层对业务服务透明，切换成本极低。

### **核心端点：POST /api/v1/authorization/evaluate**

这是整个系统设计的核心端点，也是访问频率最高的端点。使用 POST 而非 GET，原因是评估请求携带结构化请求体，且请求体不应被记录在服务器日志里（避免敏感属性泄露）。

**请求体（EvaluationContext）：**

POST /api/v1/authorization/evaluate

{

"subject": {

"userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", // 来自 SecurityContext

"tenantId": "tenant-acme-corp", // 来自 SecurityContext

"platformRoles":\["TENANT_ADMIN","DATA_ENGINEER"\], // 来自 SecurityContext

"customRoles": \[\] // 可选：调用方已知则传入，避免重复查询

},

"resource": {

"type": "DataSource", // 用于 Policy condition 匹配

"id": "ds-88f9e2a1",

"attributes": { "region":"us-east-1", "dataClassification":"CONFIDENTIAL" }

},

"action": { "permission": "datasource:read" }, // resource:action 格式

"environment": {} // 占位，未来扩展

}

**响应体（AuthorizationDecision）：**

// ALLOW

{ "decision":"ALLOW", "appliedPolicyRuleId":"rule-de-read", "decidedBy":"PLATFORM_ROLE", "decisionId":"dec-f3e2..." }

// DENY

{ "decision":"DENY", "appliedPolicyRuleId":null, "denyReason":"NO_MATCHING_POLICY_RULE", "decisionId":"dec-a9b8..." }

### **HTTP 状态码语义**

| **HTTP 状态码**         | **语义**                                                                 |
| ----------------------- | ------------------------------------------------------------------------ |
| 200 OK                  | DENY 不是错误，是正常业务结果。decision 字段为 ALLOW 或 DENY，均返回 200 |
| 400 Bad Request         | 请求体格式非法，如 permission 字段不符合 resource:action 格式            |
| 401 Unauthorized        | 服务间身份凭证缺失（mTLS 或内部 API Key）                                |
| 503 Service Unavailable | authorization-service 内部错误（数据库不可用等）                         |

### **denyReason 枚举值**

| **枚举值**              | **含义**                                         |
| ----------------------- | ------------------------------------------------ |
| NO_MATCHING_POLICY_RULE | 找不到任何匹配的策略规则，最常见的拒绝原因       |
| SUBJECT_NOT_IN_TENANT   | 主体不属于该租户，可能是跨租户访问尝试，需要告警 |
| RESOURCE_NOT_IN_TENANT  | 资源不属于该租户，同上                           |
| POLICY_EVALUATION_ERROR | 策略评估过程中出现系统异常，触发熔断             |

_⚠ denyReason 使用枚举而非自由文本，是为了防止调用方对文本内容做字符串匹配。一旦使用枚举，API 的措辞变更不会破坏任何调用方的逻辑，只有枚举值的增删改才构成破坏性变更。_

## **6.4 TenantContextHolder 实现模式**

### **为什么需要 TenantContextHolder**

Repository 在查询数据库时需要知道当前请求属于哪个租户，才能把 JDBC 连接切换到正确的 PostgreSQL Schema。但不能通过方法参数逐层传递 tenantId--那会让每个方法签名都带上这个参数，整个代码库被租户上下文污染。TenantContextHolder 把租户上下文存储在与当前执行线程绑定的地方，整条调用链上的任何组件都可以随时取用，无需显式传参。

### **ThreadLocal vs ScopedValue**

| **实现方案**                | **说明**                                                                                                                                                                                                       |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ThreadLocal（当前阶段采用） | Spring Boot 3.x / Spring Security 仍基于 ThreadLocal。ScopedValue 在框架层面的支持尚未成熟（Spring Security 7 计划引入）。当前阶段用 ThreadLocal + finally 块强制清理，配合 TenantAwareExecutor 处理异步边界。 |
| ScopedValue（演进方向）     | Java 21 正式引入（JEP 446）。作用域结束自动清理，无内存泄漏风险；结构化并发模式下子线程自动继承父线程绑定。待 Spring 生态支持成熟后整体迁移。                                                                  |

### **TenantContextFilter 核心逻辑**

@Order(Ordered.HIGHEST_PRECEDENCE) // 必须在所有其他 Filter 之前执行

public class TenantContextFilter implements Filter {

public void doFilter(req, res, chain) {

String tenantId = request.getHeader("X-Tenant-ID"); // 已由 Envoy 验证，可信

String userId = request.getHeader("X-User-ID");

Set&lt;String&gt; roles = parseAndFilterRoles( // 过滤 Keycloak 内置角色

request.getHeader("X-Platform-Roles"));

try {

TenantContextHolder.setTenantId(tenantId);

SecurityContextHolder.getContext()

.setAuthentication(new TenantAwareAuthentication(userId, tenantId, roles));

chain.doFilter(request, response);

} finally {

TenantContextHolder.clear(); // 防止线程池复用时的上下文污染

SecurityContextHolder.clearContext(); // finally 块不可省略

}

}

}

### **异步边界的 TenantAwareExecutor**

ThreadLocal 不跨线程传播。当 Application Service 使用 CompletableFuture.supplyAsync() 时，异步任务运行在新线程上，TenantContextHolder.getTenantId() 会返回 null。解决方案是封装一个 TenantAwareExecutor，在提交任务时自动捕获并在执行时恢复上下文：

public class TenantAwareExecutor implements Executor {

public void execute(Runnable command) {

String tenantId = TenantContextHolder.getTenantId(); // 提交时（父线程）捕获

Authentication auth = SecurityContextHolder.getContext().getAuthentication();

delegate.execute(() -> {

try {

TenantContextHolder.setTenantId(tenantId); // 执行时（子线程）恢复

SecurityContextHolder.getContext().setAuthentication(auth);

command.run();

} finally { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

});

}

}

## **6.5 端到端数据流说明**

以下描述一次完整的 ABAC 评估请求的数据在每一跳的精确形态：

| **数据流节点**                          | **区域**       | **数据形态说明**                                                                                       |
| --------------------------------------- | -------------- | ------------------------------------------------------------------------------------------------------ |
| 跳 1 Client → Envoy                     | 不可信区域     | Authorization: Bearer &lt;JWT&gt;；客户端的 X-Tenant-ID 可能被伪造，将被剥除                           |
| 跳 2 Envoy 处理                         | 信任边界       | ① 剥除身份 Header ② 验证 JWT（签名/exp/aud）③ 注入三个可信 Header                                      |
| 跳 3 Envoy → Filter                     | 可信区域入口   | X-Tenant-ID: tenant-acme-corp / X-User-ID: &lt;UUID&gt; / X-Platform-Roles: TENANT_ADMIN,DATA_ENGINEER |
| 跳 4 Filter → SecurityContext           | Java 应用层    | TenantContextHolder 存储 tenantId；SecurityContextHolder 存储 TenantAwareAuthentication                |
| 跳 5 @PreAuthorize 粗筛                 | 第一道业务防线 | 基于 SecurityContext 角色，不需要资源属性；拦截明显无权限请求，避免无效数据库 IO                       |
| 跳 6 Application Service → DB           | Schema 路由    | HikariCP 通过 SET search_path TO tenant_acme_corp 路由到租户 Schema；Repository 接口对此透明           |
| 跳 7 Application Service → auth-service | ABAC 评估      | POST /api/v1/authorization/evaluate，携带完整 EvaluationContext（Subject + Resource 属性 + Action）    |
| 跳 8 authorization-service 评估         | PDP 执行       | 查 RoleAssignment → 查 Policy → Policy.evaluate() → 写 Outbox → 返回 AuthorizationDecision             |
| 跳 9 结果处理                           | PEP 执行       | ALLOW → 继续执行返回资源；DENY → 抛出 AccessDeniedException → 全局处理器返回 403                       |

### **Fail-Open vs Fail-Closed 策略**

当 authorization-service 返回 503 时，Thin Client SDK 的默认行为必须在实现前明确决定：

| **策略**                      | **说明**                                                                                                                                                                                          |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Fail-Closed（本平台默认选择） | authorization-service 不可用时，所有 ABAC 评估请求立即返回 503，业务服务向客户端返回 503（非 403），触发 PagerDuty 告警。理由：多租户数据隔离的优先级高于可用性，宁可业务中断，不允许未授权访问。 |
| Fail-Open（不采用）           | authorization-service 不可用时放行已通过 RBAC 的请求，ABAC 精细控制暂时失效。适用于可用性高于安全性的场景，但必须在 Audit 日志明确标记"降级模式"。本平台暂不采用。                                |

# **第七章 架构债务与演进路径**

## **7.1 已知架构债务**

以下几项在当前设计中已被识别为架构债务，记录在案但暂不处理，待触发条件满足时再演进：

| **架构债务**                            | **优先级** | **触发演进的条件**                                                                                                                                  |
| --------------------------------------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| EvaluationContext 缺少 Environment 维度 | 中         | 时间窗口、MFA 状态、来源 IP 等环境条件未纳入 ABAC 评估体系。触发条件：业务需要"仅允许工作时间访问"等时间维度策略时引入。                            |
| 服务间通信缺少 mTLS                     | 高         | 集群内部服务调用目前依赖网络层隔离（Kubernetes ClusterIP）。触发条件：零信任网络要求，或服务规模增长到需要引入 Service Mesh（Istio/Linkerd）时。    |
| TenantContextHolder 基于 ThreadLocal    | 低         | ScopedValue 在 Spring Security 7 中获得框架级支持后统一迁移。触发条件：升级 Spring Security 7。                                                     |
| 策略引擎使用自制 Policy.evaluate()      | 中         | 自制引擎在策略复杂度增长后维护成本升高。触发条件：PolicyRule 逻辑复杂到需要独立测试和版本管理时，引入 OPA（Open Policy Agent）Sidecar 替代。        |
| Audit BC 采用 Conformist 模式           | 低         | Audit BC 强依赖上游 Schema，上游变更时被迫跟随。触发条件：Audit BC 需要独立演进时，引入 ACL 翻译层将其从 Conformist 升级为 Customer/Supplier 关系。 |

## **7.2 XACML 四角色术语对照**

本文档中的组件对应 XACML 标准的四个角色，后续所有关于授权架构的讨论应使用这套标准术语：

| **XACML 角色**                     | **在本系统中的对应组件**                                                              |
| ---------------------------------- | ------------------------------------------------------------------------------------- |
| PEP（Policy Enforcement Point）    | @PreAuthorize 注解拦截点 + authzClient.evaluate() 调用点，位于 Application Service 层 |
| PDP（Policy Decision Point）       | authorization-service 内部的 Policy.evaluate() 逻辑，是授权决策的唯一权威执行者       |
| PIP（Policy Information Point）    | RoleAssignmentRepository + 资源属性查询的 Repository，为 PDP 提供评估所需的数据       |
| PAP（Policy Administration Point） | TENANT_ADMIN 通过管理 API 维护 PolicyRule 的界面，是策略的创作和管理入口              |

## **7.3 后续 Session 议程**

- Session F：实现阶段启动 - TenantContextFilter + TenantAwareExecutor 编码
- Session G：authorization-service 内部实现 - Policy.evaluate() + Outbox Pattern
- Session H：Thin Client SDK 设计 - Resilience4j 熔断 + Fail-Closed 实现
- Session I：OPA 引入评估 - 当前自制引擎的局限性分析 + Rego 策略语言入门
- Session J：可观测性集成 - OpenTelemetry 追踪授权评估链路 + Loki 审计日志查询

_文档版本 1.0 | 涵盖 Sessions A-E | 下次更新于 Session G 完成后_