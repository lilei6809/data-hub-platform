# CDP Control Plane — 完整说明文档 v1.0

---

## 总体定位

Control Plane 是整个 CDP 平台的"神经中枢"，负责平台级的治理、策略、配置与运营。它与 Application Plane 的本质区别是：

> **Control Plane 决定"系统应该处于什么状态"，Application Plane 执行"实际的业务数据处理"。**

Control Plane 本身不碰租户的原始数据，它管理的是**元数据、配置、凭证、策略、身份**。

---

## 前置基础设施层（Platform Infrastructure）

这四个组件不属于任何一个业务层，而是整个 Control Plane 的共享基础设施。先理解它们，后续每一层的设计才能读懂。

---

### Envoy Gateway

**定位：** 所有流量的唯一入口，Control Plane 对外的边界。

Envoy Gateway 通过 Kubernetes CRD（SecurityPolicy、BackendTrafficPolicy、HTTPRoute）声明式地管理所有路由与安全策略，完全替代代码级的 Spring Security 过滤器链。

**核心职责：**

- **JWT 验证：** 与 Keycloak JWKS 端点集成，验证每一个入站请求的 token 签名与过期时间。`audiences` 字段必须显式配置，否则静默跳过 audience 校验——这是一个生产级安全陷阱。
- **`claimToHeaders` 转发：** 将 JWT 中的 `tenant_id`、`tier`、`feature_flags` 等 claim 提取出来，注入为 HTTP Header（如 `X-Tenant-ID`、`X-Tenant-Tier`），下游服务直接读 Header，对 Keycloak 完全无感知。
- **Rate Limiting：** 本地 Rate Limit（BackendTrafficPolicy，per-pod）用于基础防护；全局 Rate Limit 需要外挂 Redis 作为共享计数存储（RLS 架构）。
- **请求 Header 净化：** 客户端不能伪造 `X-Tenant-ID`，网关层必须在转发前强制删除客户端上送的同名 Header，再注入从 JWT 解析的可信值。
- **`defaultAction: Deny`：** 生产环境白名单模式，未被 HTTPRoute 显式允许的路径一律拒绝。

**不做什么：** Envoy Gateway 的 Circuit Breaker 是并发连接上限（maxConnections / maxPendingRequests），不是 Resilience4j 那样的失败率状态机。两者解决不同问题，不能混淆。

---

### Keycloak

**定位：** 外部身份提供商（IdP），Control Plane 的 Identity 层依赖它，但 Keycloak 本身不属于 Control Plane 内部服务。

**核心职责：**

- 为租户用户、平台管理员、服务账号颁发 JWT。
- 提供 JWKS 端点供 Envoy Gateway 做 token 签名验证。
- 管理 Realm、Client、User、Group 的基础 OIDC/OAuth2 生命周期。

**Keycloak 不负责什么：** Keycloak 原生 JWT 只携带通用身份信息（sub、email、roles），不包含 CDP 平台特有的 `tenant_id`、`tier`、`max_datasources`、`feature_flags` 等业务属性。这部分由 Token Enrichment Service 负责注入（见 Layer ②）。

---

### Kafka（Event Bus）

**定位：** 全平台异步事件总线，Control Plane 各层之间、Control Plane 与 Application Plane 之间的解耦通道。

**为什么需要 Kafka 而不是同步 RPC：** Control Plane 的大量操作是长时间、多步骤的编排流程（Onboarding、Offboarding、凭证轮换、Schema 迁移）。如果用同步 HTTP 调用串联这些步骤，任何一步的超时或失败都会导致整个流程回滚难度极高。Kafka 提供了天然的 checkpoint 机制——每一步完成后提交 offset，失败后从上一个 checkpoint 重试，而不是从头开始。

**核心 Topic 规划（生产最佳实践：按业务域划分，不按租户划分）：**

| Topic | 生产者 | 消费者 | 说明 |
|---|---|---|---|
| `tenant.lifecycle.events` | Onboarding/Offboarding Svc | Schema Provisioning、Billing、Keycloak Sync | 租户状态变更 |
| `datasource.state.events` | DataSource Config Svc | Application Plane Lifecycle BC | 数据源配置变更通知 |
| `credential.rotation.events` | Credential Lifecycle Svc | Connection Pool Mgr | 凭证轮换触发连接池重建 |
| `health.signal.events` | Application Plane Health BC | Health Signal Processor | 探测结果上报 |
| `usage.metering.events` | Application Plane（透明发出） | Usage Metering Service | 用量采集 |
| `policy.decision.events` | Policy Engine | Connection Pool Mgr | 连接池目标状态决策 |

> **生产禁忌：** 不按 tenant_id 动态创建 Topic。所有租户共享业务域 Topic，通过消息体内的 `tenant_id` 字段区分。动态创建 Topic 会导致 Broker 元数据爆炸，运维噩梦。

---

### Redis

**定位：** 高速缓存层，解决 Control Plane 中需要低延迟读取但不需要持久化强一致的数据场景。

**四个核心使用场景：**

**① Token Enrichment 缓存：** Token Enrichment Service 查询 PostgreSQL 中的租户属性是一个 IO 操作。每次请求都查库不可接受。Redis 以 `tenant:{tenant_id}:attributes` 为 key 缓存租户属性，TTL 与 JWT 过期时间对齐。租户 Tier 变更时，主动 invalidate 对应 key。

**② 全局 Rate Limit 计数：** Envoy Gateway 的 Global Rate Limit 需要一个跨 Pod 共享的原子计数器，Redis 的 `INCR` + `EXPIRE` 是标准实现。Per-pod 的 Local Rate Limit 不需要 Redis。

**③ Connection Pool 状态快照：** Policy Engine 做决策时需要读取"当前各租户连接池的实时状态"（已用连接数、当前温度、最后一次健康检查时间戳）。这些状态变化频繁、不需要持久化，放 Redis 而不是 PostgreSQL。

**④ API Key 验证缓存：** API Key Management 每次验证 Key 时不查 DB，先查 Redis。Key 吊销时同步清除缓存，确保吊销即时生效。

> **不应该放 Redis 的内容：** 连接器元数据（Connector Registry）、租户配置（DataSource Config）、凭证（绝对不能）。这些需要持久化和审计，属于 PostgreSQL + Vault 的职责。

---

## 六层业务服务详解

---

## Layer ① — Tenant Lifecycle Layer（租户生命周期层）

**本层回答的问题：** 租户怎么进来，怎么出去？

---

### Onboarding Service

**职责：** 新租户入驻的全流程编排器。它本身不执行具体操作，而是驱动一个状态机，按顺序协调其他服务完成各自的初始化动作。

**核心步骤（Saga 模式）：**

1. 在 Tenant Management Service 创建租户记录，状态设为 `PROVISIONING`
2. 调用 Schema Provisioning Service 创建 PostgreSQL Schema 并执行初始迁移
3. 同步到 Keycloak 创建对应的 Realm 或 Client
4. 调用 Token Enrichment Service 缓存该租户的初始属性
5. 通知 Billing 系统开始计费周期
6. 状态迁移至 `ACTIVE`，发布 `tenant.lifecycle.events`（`type: TENANT_ACTIVATED`）

**关键设计：** 每个步骤完成后写入 checkpoint（可用 PostgreSQL 的 Onboarding 状态表）。如果步骤 4 失败，重试时从步骤 4 继续而不是从头开始。这需要每个步骤具备**幂等性**——重复执行不产生副作用。

**状态机：**

```
PENDING → PROVISIONING → SCHEMA_CREATED → IDP_SYNCED → BILLING_REGISTERED → ACTIVE
                ↓（任意步骤失败）
           PROVISIONING_FAILED → （人工介入 or 自动回滚）
```

---

### Offboarding Service

**职责：** 租户注销的全流程编排器，与 Onboarding 对称设计，但复杂度更高，因为涉及 GDPR 被遗忘权。

**GDPR 被遗忘权要求清理的资源：**

| 资源 | 清理方式 | 负责服务 |
|---|---|---|
| PostgreSQL Schema | `DROP SCHEMA tenant_xxx CASCADE` | Schema Provisioning |
| Kafka 历史消息 | 按 tenant_id 过滤的 Compaction / 保留期到期 | 平台 Kafka 管理员策略 |
| Vault 凭证 | 删除对应 KV Path 下的所有 Secret | Credential Lifecycle |
| OTel/Loki 日志 | 日志保留策略 + 标记删除请求 | Observability 层 |
| Keycloak 用户 | 删除 Realm 或 User 记录 | Identity 层同步 |
| Redis 缓存 | 按 tenant_id 前缀批量 DEL | 各缓存持有者 |

**设计要点：** 清理操作不能是一次性事务，因为跨多个系统。必须用状态机 + checkpoint 机制，记录每个资源的清理状态。清理完成后保留一条**清理凭证记录**（记录"何时清理了什么"），这条记录本身不含个人数据，用于 GDPR 合规审计。

---

### Tenant Management Service

**职责：** 租户元数据的**唯一事实来源（Single Source of Truth）**。

**存储的核心字段：**

```
tenant_id        -- 全局唯一，不可变
tenant_name      -- 显示名
tier             -- STARTER / GROWTH / ENTERPRISE
status           -- PROVISIONING / ACTIVE / SUSPENDED / OFFBOARDING / TERMINATED
plan_config      -- max_datasources, max_connections, feature_flags
region           -- 数据主权要求的部署区域
created_at
contract_end_at
```

**关键约束：** 其他任何服务需要读取租户基础信息时，都应通过 Tenant Management Service 的 API 或 Redis 缓存获取，而不是各自维护一份副本。`tier` 和 `status` 的变更必须经过本服务，变更后发布事件，其他服务通过消费事件更新自己的缓存或本地副本。

---

## Layer ② — Identity & Access Layer（身份与访问层）

**本层回答的问题：** 系统怎么知道"是谁在操作，他有什么权限"？

---

### Token Enrichment Service

**职责：** 将 Keycloak 颁发的"薄" JWT 增强为携带平台业务属性的"厚" JWT，或者通过 ExtAuth 机制在请求通过 Envoy 时实时注入 Header。

**两种实现模式：**

**模式 A — JWT 增强（适合离线场景）：** 用户登录时，Keycloak 通过 Token Enrichment 回调（Mapper Protocol）调用本服务，本服务查询租户属性并写入 JWT Claims。JWT 里直接包含 `tenant_id`、`tier` 等字段。

**模式 B — ExtAuth 实时注入（推荐，更灵活）：** Envoy Gateway 配置 ExtAuth，每个请求到达时调用本服务做一次轻量查询（Redis 缓存，微秒级）。本服务返回额外的 HTTP Header，Envoy 将其注入后续转发的请求中。优点是 JWT 本身不需要修改，属性变更（比如升级 Tier）无需等 JWT 重新颁发即时生效。

**Redis 缓存策略：**

```
key:   tenant:{tenant_id}:enrichment
value: { tier, max_datasources, feature_flags, region }
TTL:   与 JWT 过期时间一致（通常 1h）
invalidate: 当 Tenant Management Service 发布 tier/status 变更事件时
```

---

### IAM Provisioning Service

**职责：** 在租户创建后，把平台租户身份事实幂等地落到 Keycloak shared realm 中，作为 Tenant IAM Onboarding 的执行服务。

**MVP 边界：**

- 固定使用 shared realm：`cdp-auth-pool`
- 暂不支持 dedicated realm
- tenant 在 Keycloak 中映射为 Organization
- tenant slug 使用 `tenant-***` 格式，并写入 Organization attribute
- Organization attributes 至少保存 `tenant_id`、`tier`、`status`
- 监听 `TenantCreated`，成功后发布 `TenantIamProvisionedEvent`

**Keycloak 资源模型：**

```
Organization: tenant-{slug}
Attributes:   tenant_id, tier, status
Realm roles:  TENANT_ADMIN, data_engineer, viewer
Admin user:   email 来自用户注册提交
Isolation:    通过 Organization membership 隔离，而不是租户前缀角色名
```

**服务账号：** 使用统一 confidential client `cdp-backend` 调用 Keycloak Admin API。client secret 由 Vault 注入，不进入 Git，不进入配置表。

---

### API Key Management

**职责：** 机器对机器（M2M）场景下的身份认证。当接入方是 ETL 工具、数据管道、外部系统时，不适合使用 OAuth2 用户登录流程，而是使用 API Key。

**生命周期管理：**

- **创建：** 生成高熵随机字符串，Hash 后存入 PostgreSQL（原始 Key 只在创建时返回一次，不持久化明文）
- **验证：** 请求携带 Key → Hash → 查 Redis 缓存 → 缓存未命中则查 PostgreSQL → 返回对应租户身份
- **轮换：** 支持新旧 Key 并行生效的过渡期（Grace Period），旧 Key 在 Grace Period 结束后失效
- **吊销：** 立即标记为 REVOKED，同步清除 Redis 缓存，确保吊销在下一次请求时即时生效
- **审计：** 每次 Key 使用记录访问日志（时间、来源 IP、租户、调用接口），存入 Loki

---

## Layer ③ — DataSource Governance Layer（数据源治理层）

**本层回答的问题：** 数据源本身的"配置信息"和"凭证"谁来管？

**这是 CDP 平台与普通 SaaS 平台最大的差异所在。** 普通 SaaS 的 Control Plane 不需要管理"连接器类型"和"凭证轮换"，但 CDP 必须。

---

### Connector Registry Service

**职责：** 维护平台级的连接器类型目录。这是**全局共享的元数据，不属于任何租户**。

对标 Salesforce Data Cloud 的 Connector Library。

**存储内容：**

```
connector_type_id    -- e.g. "mysql-8.x", "snowflake", "s3-iceberg"
display_name
category             -- RDBMS / OBJECT_STORAGE / DATA_WAREHOUSE / STREAMING
required_params      -- [{ name: "host", type: "string", required: true }, ...]
optional_params
capabilities         -- [BATCH, STREAMING, FEDERATION, CDC]
schema_version       -- 参数 schema 的版本，支持向后兼容
status               -- GA / BETA / DEPRECATED
```

**关键设计：** Connector Registry 只管"类型"，不管"实例"。"MySQL 类型需要 host/port/database/username" 是 Connector Registry 的职责。"租户 A 的 MySQL 数据源 host 是 192.168.1.1" 是 DataSource Config Service 的职责。两者严格分离。

---

### DataSource Config Service

**职责：** 维护租户级的数据源配置实例。这是 Application Plane 中 DataSource Lifecycle BC 的**配置来源**。

**存储内容（存于各租户的 PostgreSQL Schema 内）：**

```
datasource_id        -- UUID
tenant_id
connector_type_id    -- 外键指向 Connector Registry
display_name
ownership_type       -- BYODS（租户自有）/ PMDS（平台提供）
connection_params    -- { host, port, database, ... } 非敏感部分
credential_ref       -- Vault 中凭证的路径，不存明文密码
status               -- REGISTERED / VALIDATING / ACTIVE / DEGRADED / SUSPENDED / ARCHIVED
created_at
last_validated_at
```

**与 Application Plane 的边界：** DataSource Config Service 负责数据源的**配置生命周期**（注册、修改、归档）。Application Plane 的 DataSource Lifecycle BC 负责数据源的**运行时生命周期**（连接建立、健康探测、状态响应）。前者是 Control Plane 职责，后者是 Application Plane 职责。

---

### Credential Lifecycle Service

**职责：** HashiCorp Vault 的封装层。它不只是"存凭证"，更重要的是**凭证轮换调度**。

**核心功能：**

**① 凭证存储：** 将租户数据源的密码、API Key、证书等敏感信息写入 Vault KV Secrets Engine。存储路径规范为：`secret/tenant/{tenant_id}/datasource/{datasource_id}/credentials`。DataSource Config Service 只保存这个路径引用（`credential_ref`），不保存明文。

**② 凭证轮换：** BYODS 场景下，租户数据库密码有过期策略。本服务维护一个轮换调度任务：
- 定期检查凭证的 `expires_at`
- 提前 N 天触发轮换流程（通知租户提供新密码，或通过 Vault Dynamic Secrets 自动生成）
- 轮换完成后发布 `credential.rotation.events`，Connection Pool Manager 消费事件重建连接池

**③ 动态密钥（高级场景）：** 对于支持动态密钥的数据源（如 PostgreSQL + Vault Database Engine），Vault 可以为每次连接临时生成短期凭证，无需管理长期密码。这是生产环境的最高安全级别，但需要数据源本身支持动态用户创建。

**④ 审计：** 每次凭证读取操作都通过 Vault Audit Log 记录，满足合规要求。

---

### Schema Provisioning Service

**职责：** 在 PostgreSQL 中为每个租户管理独立 Schema 的完整生命周期。

**三个核心操作：**

**① 创建（Onboarding 阶段）：**
```sql
CREATE SCHEMA tenant_{tenant_id};
-- 执行初始迁移脚本（使用 Flyway 或 Liquibase，per-schema 模式）
SET search_path = tenant_{tenant_id};
-- 执行 V1__init.sql, V2__add_datasource_table.sql ...
```

**② 迁移（平台版本升级）：** 当平台发布新版本需要修改数据模型时，Schema Provisioning Service 负责按租户逐批执行迁移脚本。批次大小和速率需要控制，避免对 PostgreSQL 造成写入峰值。

**③ 删除（Offboarding 阶段）：**
```sql
DROP SCHEMA tenant_{tenant_id} CASCADE;
```

**幂等性保证：** 所有操作必须是幂等的。如果 Onboarding 中途失败重试，`CREATE SCHEMA IF NOT EXISTS` 不会报错；迁移脚本通过版本表（`schema_version`）记录已执行版本，不重复执行。

---

## Layer ④ — Connection Resource Governance Layer（连接资源治理层）

**本层回答的问题：** 平台如何在成千上万租户中公平、安全地分配有限的数据库连接资源？

---

### Policy Engine

**职责：** 连接资源治理的**决策中心**。它不执行任何操作，只产出决策。

Policy Engine 是"空中交通管制中心"——它接收来自多个来源的信号，综合判断后发出指令，但不亲自操控飞机。

**输入信号：**
- 来自 Health Signal Processor 的数据源健康状态
- 来自 Redis 的当前各租户连接池状态快照
- 租户的 `tier`（从 Tenant Management 读取）
- 全局连接资源水位（当前已用 / 总容量）

**决策输出：** 发布 `policy.decision.events`，包含：

```json
{
  "tenant_id": "xxx",
  "datasource_id": "yyy",
  "target_state": "WARM",
  "reason": "HEALTH_DEGRADED",
  "action": "SCALE_DOWN_POOL"
}
```

**连接池温度模型（四档）：**

| 温度 | 含义 | 触发条件 |
|---|---|---|
| HOT | 满载运行，最大连接数 | 高流量，Tier 高 |
| WARM | 预热状态，保持最小连接数 | 正常运行但低流量 |
| COOL | 极少连接，几乎不占资源 | 长时间无访问 |
| SUSPENDED | 连接池完全关闭 | 健康检测连续失败 / 配额耗尽 |

---

### Connection Pool Manager

**职责：** 消费 Policy Engine 的决策事件，**实际执行**连接池的创建、扩缩容和销毁。

**核心操作：**

- 消费 `policy.decision.events`
- 根据 `target_state` 调整对应租户 + 数据源的 HikariCP 池参数（`maximumPoolSize`、`minimumIdle`）
- 连接池销毁（SUSPENDED）时，等待现有连接完成当前事务后优雅关闭
- 凭证轮换时（消费 `credential.rotation.events`），关闭旧池，用新凭证重建新池
- 将当前池状态写入 Redis 供 Policy Engine 读取

**关键约束：** Connection Pool Manager 不做业务判断，只执行指令。业务判断（"该租户应该 WARM 还是 COOL"）全部由 Policy Engine 完成。这是职责分离的核心。

---

### Quota & Throttle Service

**职责：** 为每个租户维护连接资源配额，防止 noisy tenant 独占资源。

**配额维度：**

```
max_active_connections    -- 该租户所有数据源同时活跃连接数上限
max_datasources           -- 该租户可注册的数据源数量上限
max_queries_per_second    -- 查询频率上限（通过 Envoy Rate Limit 联动）
```

这些配额值来自 `tier`（由 Tenant Management Service 提供）。STARTER Tier 的 `max_active_connections` 可能是 10，ENTERPRISE Tier 可能是 5000。

**当配额耗尽时：** 发布事件通知 Policy Engine，Policy Engine 决策将该租户的新数据源连接请求暂停（SUSPENDED），并向 Application Plane 返回 `429 Too Many Requests`。

---

## Layer ⑤ — Observability & Operations Layer（可观测性与运营层）

**本层回答的问题：** 平台如何感知"哪里出问题了"，以及"各租户用了多少资源"？

---

### Health Signal Processor

**职责：** Application Plane 中 Health Monitoring BC 与 Control Plane 之间的**桥梁**。

**边界设计：**

- Health Monitoring BC（Application Plane）：负责**探测**——向每个数据源发起连通性检测，记录原始探测结果，发布 `health.signal.events`。它只发信号，不做业务判断。
- Health Signal Processor（Control Plane）：负责**判断**——消费探测信号，识别模式（"连续 5 次失败"、"延迟 P99 超过阈值"），触发 DataSource 状态迁移或 Policy Engine 决策。

**为什么要分开？** 探测是 Application Plane 的高频操作（每 30 秒一次），判断是 Control Plane 的低频决策（基于一段时间的模式）。职责混在一起会让 Health Monitoring BC 承担它不应该承担的业务决策责任，违反 DDD 的 Bounded Context 边界。

**触发的下游动作：**

- 连续失败 N 次 → 发布事件触发 DataSource Config Service 将状态改为 `DEGRADED`
- 持续 DEGRADED 超过阈值 → 触发告警（通过 Alertmanager → PagerDuty / Slack）
- 恢复成功 → 触发状态回 `ACTIVE`，通知 Policy Engine 恢复 HOT 温度

---

### Usage Metering Service

**职责：** 采集 Application Plane 的用量事件，按租户聚合，上报给 Billing 系统。

**关键设计原则：** Application Plane 的业务代码完全不知道自己在"被计费"。它只发标准的业务事件（"DataSource X 完成了一次查询，扫描了 10,000 行"），Usage Metering Service 负责将业务事件翻译成计费维度（"租户 A 本月已消耗 X 个 Data Processing Units"）。

**采集的计费维度（示例）：**

```
active_datasource_count   -- 按天快照
data_rows_processed       -- 累计行数
api_calls                 -- API 调用次数
connection_hours          -- 连接池活跃小时数
storage_gb                -- 数据存储量
```

**聚合策略：** 原始事件高频写入（Kafka），Usage Metering Service 以分钟/小时为窗口做流式聚合（可用 Kafka Streams 或 Flink），聚合结果写入 PostgreSQL 的 Metering Schema，Billing 系统从此读取。

---

## Layer ⑥ — Admin & Configuration Layer（管理与配置层）

**本层回答的问题：** 平台运营人员如何操控和监控整个 Control Plane？

---

### Admin Console

**职责：** 平台运营团队（不是租户）的操控界面，是整个 Control Plane 对内的"驾驶舱"。

**核心功能（超越普通 SaaS Admin Console 的部分）：**

- 手动触发 Onboarding / Offboarding 流程
- 查看所有租户的当前状态（ACTIVE / SUSPENDED / OFFBOARDING）
- 全局数据源健康全景图（哪些租户的哪些数据源当前是 DEGRADED）
- 手动调整租户 Tier（触发 Quota 和 Pool 温度重新计算）
- 查看连接池资源水位（全局已用 vs 总容量）
- Credential 手动触发轮换
- 查看 Metering 数据（每个租户的资源消耗）

**技术实现：** Admin Console 调用的是 Control Plane 各服务的内部 API，这些 API 通过 Envoy Gateway 的独立 Route（`/internal/admin/...`）与租户 API 隔离，要求 Admin 角色的 JWT Claim，`defaultAction: Deny` 保证其他路径不可访问。

---

### Feature Flag Service

**职责：** 支持租户粒度的功能灰度发布。

**核心场景：** 新的 Streaming Connector 先只给 10 个 Beta 租户开启，观察一周后再全量放开。在 Salesforce 里，类似能力叫 "Permission Set"。

**实现机制：**

- Feature Flag 的开关状态存储在 PostgreSQL（持久化）和 Redis（缓存）
- Token Enrichment Service 在生成注入 Header 时读取该租户的 Feature Flag，注入为 `X-Feature-Flags: streaming_connector=true,new_ui=false`
- Application Plane 的服务读取 Header，决定是否启用对应功能路径
- Feature Flag 变更后，Token Enrichment Service 的 Redis 缓存需要主动 invalidate

**注意：** Feature Flag Service 只管"某个租户是否有某个功能的访问权"，不管功能本身的具体实现。功能实现在 Application Plane 的服务里，通过读取 Header 来做分支。

---

## 层间依赖与关键数据流

---

### 租户入驻完整流程（Onboarding）

```
Admin Console
  → Onboarding Service（发起，状态 PENDING）
    → Tenant Management Service（创建记录）
    → Schema Provisioning Service（创建 PG Schema）
    → Keycloak（创建 Realm/Client）
    → Token Enrichment Service（预热 Redis 缓存）
    → Billing（注册计费）
  → Kafka: tenant.lifecycle.events (TENANT_ACTIVATED)
  → Policy Engine（初始化连接池策略）
  → 状态迁移至 ACTIVE
```

---

### 数据源凭证轮换完整流程

```
Credential Lifecycle Service（调度触发）
  → 生成新凭证，写入 Vault
  → Kafka: credential.rotation.events
    → Connection Pool Manager（消费事件）
      → 关闭该数据源旧连接池
      → 从 Vault 读取新凭证
      → 创建新连接池
      → 将新状态写入 Redis
    → Health Signal Processor（确认新池健康）
```

---

### 数据源健康恶化完整流程

```
Application Plane Health BC（探测失败）
  → Kafka: health.signal.events (FAILURE)
    → Health Signal Processor（消费，识别"连续 5 次失败"模式）
      → DataSource Config Service（状态 → DEGRADED）
      → Policy Engine（触发决策）
        → Kafka: policy.decision.events (SUSPENDED)
          → Connection Pool Manager（关闭连接池）
      → Alertmanager（触发告警）
```

---

## 与 Application Plane 的边界总结

这是最容易混淆的设计问题，必须明确：

| 职责 | Control Plane | Application Plane |
|---|---|---|
| 租户生命周期 | ✅ Onboarding/Offboarding | ❌ |
| 数据源配置注册 | ✅ DataSource Config Service | ❌ |
| 数据源运行时状态 | ❌ | ✅ DataSource Lifecycle BC |
| 健康探测执行 | ❌ | ✅ Health Monitoring BC |
| 健康信号业务判断 | ✅ Health Signal Processor | ❌ |
| 连接池策略决策 | ✅ Policy Engine | ❌ |
| 连接池执行操作 | ✅ Connection Pool Manager | ❌ |
| 凭证存储与轮换 | ✅ Credential Lifecycle | ❌ |
| 实际数据查询执行 | ❌ | ✅ Query / Ingest 服务 |
| 用量事件发布 | ❌ | ✅（透明发出） |
| 用量聚合计费 | ✅ Usage Metering | ❌ |

**核心判断标准：** 如果一件事影响的是"整个平台对这个租户/数据源的策略与配置"，它属于 Control Plane。如果一件事是"处理租户的实际业务数据流"，它属于 Application Plane。
