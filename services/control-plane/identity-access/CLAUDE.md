# Identity & Access Layer (IAL) — CLAUDE.md

## 模块概述

IAL 是 SaaS 多租户 CDP 平台的统一身份与访问控制层，负责：
- **身份认证**：Keycloak 颁发 JWT，Envoy Gateway 本地验证
- **多租户隔离**：每个 JWT 携带 `tenant_id`，下游通过 Header 传递
- **双层授权**：Envoy 负责路由级 RBAC，Spring Boot 负责资源级 ABAC

## 技术栈

| 组件 | 技术 |
|------|------|
| 身份提供商 | Keycloak 26+（Shared Realm + Organization-per-Tenant） |
| 网关层 | Envoy Gateway（JWT 验证、RBAC、Claims→Headers 注入） |
| 缓存 | Redis（Refresh Token、JTI 黑名单、tier 缓存） + Caffeine（本地一级缓存） |
| 业务服务 | Spring Boot（`spring-boot-starter-security`，**不引入** oauth2-resource-server） |
| 消息队列 | Kafka（租户 Onboarding 事件驱动） |
| 网络策略 | Cilium NetworkPolicy |
| 凭证管理 | HashiCorp Vault |

## 关键架构约束（必须遵守）

### 下游服务禁止解析 JWT
下游 Spring Boot 服务**不引入** `spring-boot-starter-oauth2-resource-server`，**不解析 JWT**。
身份信息通过 Envoy 注入的 HTTP Header 传递：

```
X-User-ID      → JWT.sub（Keycloak UUID）
X-Tenant-ID    → JWT.tenant_id
X-User-Roles   → JWT.roles（逗号分隔）
```

使用 `TenantAuthenticationFilter` 读取这些 Header 构建 `SecurityContext`。

### tier 不写入 JWT
`tier` 属性存在 Keycloak Organization 上，**不注入 JWT**。下游通过三级缓存实时查询：
```
Caffeine（1min TTL）→ Redis（5min TTL）→ Keycloak Admin API
```
tier 变更时通过 Redis PubSub 广播，各 Pod 主动失效本地缓存。

### Keycloak Admin API 调用不使用 @Transactional
Keycloak 无分布式事务支持。所有 Admin API 调用必须**幂等**（catch 409 → 查已有资源），依赖幂等性而非事务回滚。本地 DB 操作单独使用 `@Transactional`。

### sub 使用 UUID，禁止使用 username 关联数据
`sub` 是 Keycloak 内部 UUID，下游服务**禁止**使用 username 或 email 关联业务数据，必须使用 `sub`（UUID）。

## 服务模块

### iam-provisioning-service
- **职责**：Tenant IAM Onboarding 服务，消费 `TenantCreated`，幂等创建 Keycloak Organization、初始管理员、realm role membership，并发布 `TenantIamProvisionedEvent`
- **依赖**：Spring Boot Web、Actuator、Validation、Spring Data JDBC、Flyway、Kafka、Keycloak Admin Client、Resilience4j、SpringDoc
- **位置**：`iam-provisioning-service/`

### token-enrichment-service
- **职责**：Tenant 上下文富化服务，用于 JWT 和 ExtAuth 流
- **依赖**：Spring Boot Web、Actuator、Validation、Redis、Kafka、SpringDoc
- **位置**：`token-enrichment-service/`

## 核心实现模式

### TenantAuthenticationFilter
```java
// 读取 Envoy 注入的 Header → 构建 TenantAuthentication
// 无 Header → 401（请求未经 Envoy，直接拒绝）
String tenantId = request.getHeader("X-Tenant-ID");
String userId   = request.getHeader("X-User-ID");
String roles    = request.getHeader("X-User-Roles");
```

### ABAC 资源级别授权
```java
// 必须校验资源归属：resource.tenantId == currentTenantId
@PreAuthorize("@dataSourceAuthz.isOwner(authentication, #dataSourceId)")
```

### Redis 数据结构

| 用途 | Key 格式 | TTL |
|------|---------|-----|
| Refresh Token | `rt:{userId}:{jti}` | 7 天 |
| JTI 黑名单 | `blocklist:{jti}` | Token 剩余有效期 |
| tier 缓存 | `tier:{tenantId}` | 5 分钟 |

## 安全防线（4 层）

1. **网络层**：Cilium NetworkPolicy，仅 Envoy 可访问业务 Pod
2. **网关层**：Envoy JWT 验证 + JTI 黑名单 + RBAC
3. **应用层**：`TenantAuthenticationFilter` + `@PreAuthorize` ABAC
4. **数据层**：所有查询强制带 `tenantId` 条件（Hibernate Filter）

## JWT 规范

- Access Token 生命周期：**15 分钟**
- Refresh Token 生命周期：**7 天**，存 Redis
- 必须包含：`iss`、`sub`（UUID）、`aud`（必须含 `cdp-platform`）、`exp`、`iat`、`tenant_id`、`roles`
- `exp - iat > MAX_LIFESPAN` 触发告警并拒绝

## Envoy SecurityPolicy

全平台**唯一一个** SecurityPolicy，指向 Shared Realm JWKS：
```
issuer:   https://keycloak.cdp.example.com/realms/cdp
audience: cdp-platform
JWKS URI: .../realms/cdp/protocol/openid-connect/certs
缓存时长: 300s（kid 不匹配时立即刷新）
```

## 租户 Onboarding 流程

由 Kafka 事件驱动（**不由 Argo 直接调用**）：
```
TenantCreated
  → TenantIamProvisioningService.provisionTenantIam()
      1. 创建 Keycloak Organization（幂等，shared realm: cdp-auth-pool）
      2. 创建初始管理员（幂等，不强制 UPDATE_PASSWORD，不启用 email verification）
      3. 绑定用户到 Organization（幂等）
      4. 绑定 realm roles: TENANT_ADMIN / data_engineer / viewer（幂等）
      5. 本地 DB 标记完成（@Transactional）
  → 发布 TenantIamProvisionedEvent
```

## 角色定义

| 角色 | 说明 |
|------|------|
| `PLATFORM_ADMIN` | 平台管理员，可访问 `/admin/**` |
| `TENANT_ADMIN` | 租户管理员，管理本租户资源 |
| `data_engineer` | 数据工程师，执行数据源与数据管道相关操作 |
| `viewer` | 只读访问本租户数据 |

## 当前范围外（本版本不实现）

- MFA（多因素认证）
- 用户自服务密码找回
- 细粒度审计日志（单独设计）
- Dedicated Realm-per-Tenant（P2，超大企业客户）

## Task Master AI Instructions
**Import Task Master's development workflow commands and guidelines, treat as if import is in the main CLAUDE.md file.**
@./.taskmaster/CLAUDE.md
