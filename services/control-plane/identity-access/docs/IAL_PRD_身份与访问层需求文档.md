# Identity & Access Layer — 产品需求文档（PRD）

> **版本:** v1.0
> **模块:** Identity & Access Layer (IAL)
> **所属平台:** SaaS Multi-Tenant CDP — 数据源管理平台
> **状态:** 评审中

---

## 1. 背景与目标

### 1.1 背景

CDP 平台是一个 SaaS 多租户系统，承载来自不同企业的客户数据。平台需要在以下维度保障安全：

- **身份认证**：确认"你是谁"
- **租户隔离**：确认"你属于哪个租户"
- **权限控制**：确认"你能做什么"

平台使用 Keycloak 作为身份提供商，Envoy Gateway 作为统一验证入口，Spring Boot 微服务作为下游业务执行层。

### 1.2 目标

| 目标 | 说明 |
|------|------|
| 统一身份验证 | 所有 API 请求必须携带合法 JWT，在网关层统一验证 |
| 多租户身份隔离 | 每个 JWT 必须携带 `tenant_id`，下游服务通过租户上下文隔离数据 |
| 细粒度授权 | 支持路由级别粗粒度 RBAC 与资源级别细粒度 ABAC |
| 外部 IdP 接入 | 企业租户可接入自有 IdP（Okta / Azure AD / SAML），平台统一颁发 JWT |
| 自动化租户 IAM 初始化 | 新租户 Onboarding 时自动完成 Keycloak 配置，无需人工介入 |

---

## 2. 用户角色

| 角色 | 说明 |
|------|------|
| **平台管理员（PLATFORM_ADMIN）** | 管理整个 CDP 平台，可访问 `/admin/**` 路径 |
| **租户管理员（TENANT_ADMIN）** | 管理本租户内的数据源、用户、配置 |
| **租户普通用户（TENANT_VIEWER）** | 只读访问本租户数据 |
| **数据源操作员（datasource:write）** | 有权创建、修改、删除数据源 |

---

## 3. 功能需求

### 3.1 JWT Token 管理

| 需求编号 | 需求描述 | 优先级 |
|---------|---------|--------|
| IAL-F001 | Access Token 生命周期为 15 分钟 | P0 |
| IAL-F002 | Refresh Token 生命周期为 7 天，存储于 Redis | P0 |
| IAL-F003 | 所有 JWT 必须携带标准 Claims：`iss`、`sub`、`aud`、`exp`、`iat` | P0 |
| IAL-F004 | `sub` 使用 Keycloak 内部 UUID，下游服务禁止使用 username 关联数据 | P0 |
| IAL-F005 | `aud` 必须包含 `cdp-platform`，网关层强制校验 | P0 |
| IAL-F006 | 支持主动吊销 Token：Redis JTI 黑名单，Key = `blocklist:{jti}`，TTL = 剩余有效期 | P0 |
| IAL-F007 | 黑名单仅覆盖主动吊销场景（管理员封禁账户），其余场景依赖短生命周期自然过期 | P0 |

### 3.2 多租户身份隔离

| 需求编号 | 需求描述 | 优先级 |
|---------|---------|--------|
| IAL-F010 | 使用 Keycloak Shared Realm + Organization-per-Tenant 模型 | P0 |
| IAL-F011 | 每个 Organization 携带 `tenant_id`、`tier` 属性 | P0 |
| IAL-F012 | `tenant_id` 通过 Keycloak Protocol Mapper 装配进 JWT | P0 |
| IAL-F013 | `tier` 不写入 JWT，由下游服务实时查询（Caffeine → Redis → Keycloak） | P0 |
| IAL-F014 | `tier` 变更时通过 Redis PubSub 广播，各 Pod 驱逐本地缓存 | P0 |
| IAL-F015 | 支持 Dedicated Realm-per-Tenant 模型（超大企业级租户） | P2 |

### 3.3 Envoy Gateway 验证层

| 需求编号 | 需求描述 | 优先级 |
|---------|---------|--------|
| IAL-F020 | Envoy 通过 JWKS URI 获取 Keycloak 公钥，本地验证 JWT 签名 | P0 |
| IAL-F021 | JWKS 缓存 300 秒，`kid` 不匹配时即时刷新 | P0 |
| IAL-F022 | Envoy 提取 JWT Claims 并注入请求头：`X-User-ID`、`X-Tenant-ID`、`X-User-Roles` | P0 |
| IAL-F023 | `roles` 通过 Keycloak Protocol Mapper 打平为顶层 claim，Envoy 注入 `X-User-Roles` | P0 |
| IAL-F024 | Envoy 执行路由级别 RBAC：`/admin/**` 仅允许 `PLATFORM_ADMIN` | P0 |
| IAL-F025 | 全平台只有一个 SecurityPolicy，指向 Shared Realm JWKS | P0 |

### 3.4 下游服务授权

| 需求编号 | 需求描述 | 优先级 |
|---------|---------|--------|
| IAL-F030 | 下游服务不引入 `spring-boot-starter-oauth2-resource-server` | P0 |
| IAL-F031 | 自定义 `TenantAuthenticationFilter` 读取请求头，构建 `SecurityContext` | P0 |
| IAL-F032 | 通过 `@PreAuthorize` 实现资源级别 ABAC | P0 |
| IAL-F033 | ABAC 必须校验资源归属：`resource.tenantId == currentTenantId` | P0 |
| IAL-F034 | Cilium NetworkPolicy 封锁直接 Pod 访问，仅 Envoy 可到达业务 Pod | P0 |

### 3.5 外部 IdP 接入（Identity Brokering）

| 需求编号 | 需求描述 | 优先级 |
|---------|---------|--------|
| IAL-F040 | 企业租户可配置外部 IdP（Okta / Azure AD / SAML） | P0 |
| IAL-F041 | Keycloak 作为 Identity Broker，向外部 IdP 表现为 SP，向平台表现为 IdP | P0 |
| IAL-F042 | 用户首次通过外部 IdP 登录时，自动 JIT Provisioning 创建 Keycloak User | P0 |
| IAL-F043 | Identity Brokering 后，Envoy 仍只认 Keycloak 颁发的 JWT，不暴露外部 IdP | P0 |

### 3.6 租户 IAM 自动化 Onboarding

| 需求编号 | 需求描述 | 优先级 |
|---------|---------|--------|
| IAL-F050 | 新租户 Onboarding 时自动创建 Keycloak Organization | P0 |
| IAL-F051 | 自动创建租户初始管理员账号，设置临时密码，首次登录强制修改 | P0 |
| IAL-F052 | 所有 Keycloak Admin API 调用必须幂等，支持 Temporal 重试 | P0 |
| IAL-F053 | Keycloak Admin API 使用专用 Service Account + Client Credentials，凭证存 Vault | P0 |
| IAL-F054 | Keycloak Admin API 调用不使用 `@Transactional`，依赖幂等性而非事务 | P0 |
| IAL-F055 | IAM 初始化由 Kafka 事件驱动（`TenantInfrastructureProvisionedEvent`），不由 Argo 直接调用 | P0 |

---

## 4. 非功能需求

### 4.1 安全性

| 需求 | 说明 |
|------|------|
| Token 最短生命周期 | Access Token ≤ 15 分钟 |
| 跨租户隔离 | 任何 API 不得返回其他租户数据，应用层强制校验 `tenantId` |
| 凭证安全 | 所有密钥、Client Secret 存 Vault，不写配置文件、不打印日志 |
| 最小权限 | Admin Service Account 仅授予必要 Realm 管理角色 |
| 异常 Token 检测 | `exp - iat > 阈值` 触发告警（防止配置错误导致超长有效期） |

### 4.2 性能

| 需求 | 指标 |
|------|------|
| JWT 验证延迟 | Envoy 本地验证，<1ms |
| Redis 黑名单查询 | <1ms，不阻塞正常请求 |
| tier 查询 | Caffeine 本地缓存命中率 >95%，TTL 1 分钟 |
| Keycloak 登录响应 | P99 < 500ms（含 Infinispan 缓存） |

### 4.3 可用性

| 需求 | 说明 |
|------|------|
| Keycloak 高可用 | 多副本部署，Infinispan 分布式缓存 |
| Redis 高可用 | Redis Cluster 或 Sentinel |
| Envoy JWKS 刷新 | 缓存过期前自动刷新，密钥轮换对用户无感知 |

---

## 5. 边界与约束

### 5.1 在范围内

- JWT 颁发、验证、吊销
- 多租户身份隔离
- RBAC（路由级）+ ABAC（资源级）
- BYO IdP 接入（Identity Brokering）
- 租户 IAM 自动化 Onboarding

### 5.2 不在范围内（当前版本）

- 多因素认证（MFA）
- 用户自服务密码找回
- 细粒度审计日志（Audit Log）单独设计
- Dedicated Realm-per-Tenant（P2，低优先级）

---

## 6. 术语表

| 术语 | 定义 |
|------|------|
| JWT | JSON Web Token，无状态身份令牌 |
| JTI | JWT ID，Token 的全局唯一标识符 |
| JWKS | JSON Web Key Set，公钥集合 |
| Realm | Keycloak 的租户隔离单元（本平台使用 Shared Realm） |
| Organization | Keycloak 26+ 功能，Shared Realm 内的租户隔离单元 |
| Identity Broker | Keycloak 作为外部 IdP 与平台之间的身份代理 |
| JIT Provisioning | Just-In-Time，用户首次登录时自动创建账号 |
| RBAC | Role-Based Access Control，基于角色的访问控制 |
| ABAC | Attribute-Based Access Control，基于属性的访问控制 |
| SecurityPolicy | Envoy Gateway CRD，配置 JWT 验证规则 |
