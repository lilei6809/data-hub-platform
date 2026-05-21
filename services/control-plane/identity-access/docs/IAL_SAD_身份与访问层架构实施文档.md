# Identity & Access Layer — 架构及实施文档

> **版本:** v1.0
> **模块:** Identity & Access Layer (IAL)
> **所属平台:** SaaS Multi-Tenant CDP — 数据源管理平台
> **决策依据:** IAL-001 ~ IAL-010

---

## 1. 架构总览

### 1.1 核心设计原则

```
身份验证集中化：所有验证逻辑在 Envoy Gateway，下游服务不重复验证
租户上下文头传递：身份信息通过 HTTP Header 传递，下游零 JWT 依赖
授权双层分离：Envoy 负责粗粒度 RBAC，Spring Boot 负责细粒度 ABAC
外部 IdP 收敛：所有 IdP 通过 Keycloak Identity Brokering 统一，Envoy 只认一个 issuer
```

### 1.2 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        外部 IdP 层                               │
│          Okta / Azure AD / SAML（企业租户自有 IdP）               │
└──────────────────────────┬──────────────────────────────────────┘
                           │ OIDC / SAML Federation
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Keycloak（身份核心）                          │
│  Shared Realm: cdp                                               │
│  ├── Organization: acme-corp  { tenant_id, tier: PREMIUM }       │
│  ├── Organization: techcorp   { tenant_id, tier: STANDARD }      │
│  ├── Identity Provider: okta-acme-corp                           │
│  └── Protocol Mapper: roles → 顶层 claim, tenant_id → claim      │
└──────────────────────────┬──────────────────────────────────────┘
                           │ JWT（Keycloak 签名）
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Envoy Gateway（验证 & 路由）                    │
│  ├── JWT 签名验证（JWKS 本地缓存）                                 │
│  ├── Redis 黑名单查询（blocklist:{jti}）                           │
│  ├── RBAC：路由级别粗粒度控制                                      │
│  └── Claims → Headers 注入                                        │
│        X-User-ID / X-Tenant-ID / X-User-Roles                   │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP + Headers（无 JWT）
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│               下游微服务（Spring Boot）                           │
│  ├── TenantAuthenticationFilter → SecurityContext                │
│  ├── @PreAuthorize ABAC：资源归属校验                             │
│  └── tier 实时查询：Caffeine → Redis → Keycloak                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Keycloak 配置设计

### 2.1 Realm 与 Organization 模型

```
Realm: cdp（全平台唯一 Shared Realm）
  │
  ├── Organization: acme-corp
  │     attributes:
  │       tenant_id: "acme-corp"
  │       tier:      "PREMIUM"
  │     Members: bob@acme.com, alice@acme.com
  │     Identity Provider: okta-acme-corp（BYO IdP）
  │
  ├── Organization: techcorp
  │     attributes:
  │       tenant_id: "techcorp"
  │       tier:      "STANDARD"
  │     Members: dave@techcorp.com
  │     Identity Provider: （无，使用本地账号）
  │
  └── Organization: startup-xyz
        attributes:
          tenant_id: "startup-xyz"
          tier:      "BASIC"
```

### 2.2 Protocol Mapper 配置

| Mapper 名称 | 类型 | Token Claim Name | 说明 |
|-------------|------|-----------------|------|
| tenant-id-mapper | Organization Attribute | `tenant_id` | 将 Organization.attributes.tenant_id 注入 JWT |
| roles-mapper | User Realm Role | `roles` | 将 realm_access.roles 打平为顶层 claim |

**打平后的 JWT Payload：**

```json
{
  "sub": "uuid-of-bob",
  "iss": "https://keycloak.cdp.example.com/realms/cdp",
  "aud": ["cdp-platform"],
  "exp": 1716300000,
  "iat": 1716299100,
  "tenant_id": "acme-corp",
  "roles": ["TENANT_ADMIN", "datasource:write"]
}
```

### 2.3 Client 配置

```
Client ID: cdp-platform
  Access Type: public（前端）/ confidential（服务间）
  Valid Redirect URIs: https://app.cdp.example.com/*
  Audiences: cdp-platform
```

### 2.4 Identity Brokering（BYO IdP）

```
流程：
  租户用户 → 外部 IdP（Okta/Azure AD） → Keycloak → 颁发统一 JWT → Envoy

JIT Provisioning：
  用户首次通过外部 IdP 登录 → Keycloak 自动创建 User → 加入对应 Organization

关键配置（Keycloak Identity Provider）：
  POST /admin/realms/cdp/identity-provider/instances
  {
    alias: "okta-{tenantId}",
    providerId: "oidc",
    config: {
      authorizationUrl: "...",
      tokenUrl: "...",
      clientId: "...",
      clientSecret: "<from Vault>"
    }
  }
```

---

## 3. JWT Token 设计

### 3.1 生命周期

| Token 类型 | 生命周期 | 存储位置 | 验证方式 |
|-----------|---------|---------|---------|
| Access Token | 15 分钟 | 客户端内存 | Envoy 本地验证 |
| Refresh Token | 7 天 | Redis | 有状态验证 |

### 3.2 Refresh Token Redis 结构

```
Key:   rt:{userId}:{jti}
Value: { tenantId, issuedAt, deviceInfo }
TTL:   7 天（自动过期，无需手动清理）

# 踢出该用户所有设备：
SCAN rt:{userId}:* → 批量 DEL
```

### 3.3 Standard Claims 验证规则

| Claim | 验证规则 | 异常处理 |
|-------|---------|---------|
| `iss` | 必须等于 `https://keycloak.cdp.example.com/realms/cdp` | 401 |
| `sub` | 必须存在，格式为 UUID | 401 |
| `aud` | 必须包含 `cdp-platform` | 401 |
| `exp` | 必须大于当前时间 | 401 |
| `iat` | `exp - iat > MAX_LIFESPAN` 触发告警 | 告警 + 拒绝 |

### 3.4 Token 吊销机制

```
Redis JTI 黑名单：

写入时机：管理员主动封禁账户
Key:   blocklist:{jti}
Value: "1"
TTL:   Token 剩余有效期（expiry - now）

验证流程（Envoy 每次请求）：
  EXISTS blocklist:{jti}
    ├── true  → 401 Unauthorized
    └── false → 放行
```

---

## 4. Envoy Gateway 设计

### 4.1 SecurityPolicy（全平台唯一）

```yaml
apiVersion: gateway.envoyproxy.io/v1alpha1
kind: SecurityPolicy
metadata:
  name: cdp-platform-jwt-policy
  namespace: envoy-gateway-system
spec:
  jwt:
    providers:
      - name: keycloak-cdp-platform
        issuer: "https://keycloak.cdp.example.com/realms/cdp"
        audiences:
          - "cdp-platform"
        remoteJWKS:
          uri: "https://keycloak.cdp.example.com/realms/cdp/protocol/openid-connect/certs"
          cacheDuration: "300s"
        claimToHeaders:
          - claim: "sub"
            header: "x-user-id"
          - claim: "tenant_id"
            header: "x-tenant-id"
          - claim: "roles"
            header: "x-user-roles"
```

### 4.2 JWKS 刷新机制

```
定时刷新：每 300 秒拉取最新 JWKS
即时刷新：JWT Header 中的 kid 在缓存中找不到 → 立即触发刷新

Keycloak 密钥轮换配置：
  Active Key:   key-v2（新签名）
  Standby Key:  key-v1（保留至少 15 分钟，= Access Token 生命周期）
  → 密钥轮换对用户完全无感知
```

### 4.3 RBAC 路由控制

```yaml
http_filters:
  - name: envoy.filters.http.rbac
    typed_config:
      rules:
        policies:
          admin-only:
            permissions:
              - url_path:
                  path: { prefix: "/admin/" }
            principals:
              - header:
                  name: "x-user-roles"
                  string_match: { contains: "PLATFORM_ADMIN" }

          tenant-write:
            permissions:
              - url_path:
                  path: { prefix: "/api/datasources" }
            principals:
              - header:
                  name: "x-user-roles"
                  string_match: { contains: "TENANT_ADMIN" }
```

---

## 5. 下游服务实现

### 5.1 依赖配置

```xml
<!-- 引入 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- 不引入 -->
<!-- spring-boot-starter-oauth2-resource-server（下游不解析 JWT） -->
```

### 5.2 TenantAuthenticationFilter

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String tenantId = request.getHeader("X-Tenant-ID");
        String userId   = request.getHeader("X-User-ID");
        String roles    = request.getHeader("X-User-Roles");

        // 没有注入头 → 请求未经 Envoy → 拒绝
        if (tenantId == null || userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        List<GrantedAuthority> authorities = (roles != null)
            ? Arrays.stream(roles.split(","))
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r.trim()))
                    .toList()
            : List.of();

        TenantAuthentication auth = new TenantAuthentication(userId, tenantId, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }
}
```

```java
public class TenantAuthentication extends AbstractAuthenticationToken {

    private final String userId;
    private final String tenantId;

    public TenantAuthentication(String userId, String tenantId,
                                 List<GrantedAuthority> authorities) {
        super(authorities);
        this.userId   = userId;
        this.tenantId = tenantId;
        setAuthenticated(true);
    }

    public String getTenantId() { return tenantId; }
    public String getUserId()   { return userId; }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal()   { return userId; }
}
```

### 5.3 ABAC 资源级别授权

```java
@Component("dataSourceAuthz")
public class DataSourceAuthorizationService {

    private final DataSourceRepository repository;

    // 资源归属校验：核心防线
    public boolean isOwner(Authentication auth, String dataSourceId) {
        TenantAuthentication tenantAuth = (TenantAuthentication) auth;

        return repository.findById(dataSourceId)
            .map(ds -> ds.getTenantId().equals(tenantAuth.getTenantId()))
            .orElse(false);
    }

    public boolean canRead(Authentication auth, String dataSourceId) {
        return isOwner(auth, dataSourceId)
            && auth.getAuthorities().stream()
                   .anyMatch(a -> a.getAuthority().equals("ROLE_datasource:read")
                               || a.getAuthority().equals("ROLE_TENANT_ADMIN"));
    }
}

@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {

    @DeleteMapping("/{dataSourceId}")
    @PreAuthorize("@dataSourceAuthz.isOwner(authentication, #dataSourceId)")
    public void delete(@PathVariable String dataSourceId) {
        dataSourceService.delete(dataSourceId);
    }

    @GetMapping("/{dataSourceId}")
    @PreAuthorize("@dataSourceAuthz.canRead(authentication, #dataSourceId)")
    public DataSource get(@PathVariable String dataSourceId) {
        return dataSourceService.findById(dataSourceId);
    }
}
```

### 5.4 tier 实时查询

```java
@Service
public class TenantTierService {

    private final Cache<String, String> localCache = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build();

    private final RedisTemplate<String, String> redis;
    private final KeycloakAdminClient keycloakClient;

    public String getTier(String tenantId) {
        // L1: 本地 Caffeine 缓存（1min TTL）
        return localCache.get(tenantId, id -> {
            // L2: Redis（5min TTL）
            String tier = redis.opsForValue().get("tier:" + id);
            if (tier != null) return tier;

            // L3: Keycloak Admin API
            tier = keycloakClient.getOrganizationAttribute(id, "tier");
            redis.opsForValue().set("tier:" + id, tier, 5, TimeUnit.MINUTES);
            return tier;
        });
    }

    // tier 变更时，监听 Redis PubSub 事件
    @EventListener
    public void onTierChanged(TenantTierChangedEvent event) {
        localCache.invalidate(event.getTenantId());
    }
}
```

---

## 6. Keycloak Admin API — Onboarding 集成

### 6.1 Service Account 配置

```
Client: cdp-onboarding-service
  Grant Type: Client Credentials
  Roles:
    - manage-users
    - manage-organizations
    - manage-identity-providers
  Secret: 存储于 Vault（路径：keycloak/onboarding-service）
```

### 6.2 Onboarding Provisioning Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantIamProvisioningService {

    private final Keycloak keycloakAdmin;
    private final TenantRepository tenantRepository;

    /**
     * 由 Kafka 消费 TenantInfrastructureProvisionedEvent 后触发
     * 整个方法幂等，支持 Temporal 重试
     * 注意：不使用 @Transactional，Keycloak 无分布式事务支持
     */
    public TenantIamResult provisionTenantIam(TenantIamRequest request) {
        String tenantId = request.getTenantId();
        log.info("Starting IAM provisioning for tenant: {}", tenantId);

        // Step 1: 创建 Organization（幂等）
        String orgId = ensureOrganizationExists(tenantId, request.getTier());

        // Step 2: 创建初始管理员（幂等，按 email 查重）
        String userId = ensureAdminUserExists(request.getAdminEmail());

        // Step 3: 绑定关系（幂等）
        addUserToOrganization(orgId, userId);
        assignTenantAdminRole(userId);

        // Step 4: 配置 BYO IdP（按需，幂等）
        if (request.hasBYOIdP()) {
            configureIdentityProvider(tenantId, request.getIdpConfig());
        }

        // Step 5: 写本地 DB（独立事务，仅操作本地）
        markIamProvisioned(tenantId);

        log.info("IAM provisioning completed for tenant: {}", tenantId);
        return TenantIamResult.success(tenantId);
    }

    private String ensureOrganizationExists(String tenantId, String tier) {
        OrganizationRepresentation org = new OrganizationRepresentation();
        org.setName(tenantId);
        org.setAttributes(Map.of(
            "tenant_id", List.of(tenantId),
            "tier",      List.of(tier)
        ));

        try (Response response = keycloakAdmin.realm("cdp").organizations().create(org)) {
            return switch (response.getStatus()) {
                case 201 -> extractCreatedId(response);
                case 409 -> getOrganizationIdByName(tenantId); // 已存在，幂等
                default  -> throw new KeycloakProvisioningException(response);
            };
        }
    }

    private String ensureAdminUserExists(String email) {
        UserRepresentation user = new UserRepresentation();
        user.setEmail(email);
        user.setUsername(email);
        user.setEnabled(true);
        user.setCredentials(List.of(buildTempCredential()));

        try (Response response = keycloakAdmin.realm("cdp").users().create(user)) {
            return switch (response.getStatus()) {
                case 201 -> extractCreatedId(response);
                case 409 -> getUserIdByEmail(email); // 已存在，幂等
                default  -> throw new KeycloakProvisioningException(response);
            };
        }
    }

    @Transactional // 只包裹本地 DB 操作
    protected void markIamProvisioned(String tenantId) {
        tenantRepository.markIamProvisioned(tenantId);
    }
}
```

### 6.3 幂等性保障矩阵

| 操作 | Keycloak 重复调用结果 | 处理方式 |
|------|---------------------|---------|
| 创建 Organization | 409 Conflict | catch 409 → 查询返回已有 ID |
| 创建 User | 409 Conflict | catch 409 → 查询返回已有 ID |
| 加入 Organization | 409 Conflict | catch 409 → 忽略，视为成功 |
| 分配 Role | 重复分配不报错 | 天然幂等 |
| 配置 IdP | 409 Conflict | catch 409 → 更新或忽略 |

---

## 7. Onboarding 流程集成

### 7.1 IAM 在四阶段 Onboarding 中的位置

```
阶段 1: PENDING_INFRA
  └── Onboarding Service 写 CDP DB
        status = PENDING_INFRA, active = false

阶段 2: PENDING_APP（Argo Workflow 完成后发布 Kafka 事件）
  └── Tenant Provisioning Service 消费事件
        └── TenantIamProvisioningService.provisionTenantIam()
              ├── 创建 Keycloak Organization
              ├── 创建初始管理员
              ├── 配置 BYO IdP（按需）
              └── 写本地 DB: status = PENDING_ACTIVATION

阶段 3: PENDING_ACTIVATION
  └── 其他 BC 初始化（Connector Registry / DataSource / Connection Governance）

阶段 4: ACTIVE
  └── active = true，发激活邮件（含临时密码重置链接）
```

### 7.2 事件流

```
Argo Workflow 最后一步
    └──→ Kafka: TenantInfrastructureProvisionedEvent
                { tenantId, tier, adminEmail, idpConfig? }

Tenant Provisioning Service
    └──→ 监听事件
    └──→ TenantIamProvisioningService.provisionTenantIam()
    └──→ 完成后发布: TenantIamProvisionedEvent

后续 BC（Connector Registry 等）
    └──→ 监听 TenantIamProvisionedEvent，继续执行
```

---

## 8. 防御纵深

### 8.1 多层安全防线

```
Layer 1: 网络层（Cilium NetworkPolicy）
  仅 Envoy Gateway 可访问业务 Pod
  物理封锁直接 Pod 访问

Layer 2: 网关层（Envoy）
  JWT 签名验证
  JTI 黑名单查询
  RBAC 路由控制

Layer 3: 应用层（Spring Boot）
  TenantAuthenticationFilter 验证 Header 存在性
  @PreAuthorize ABAC 资源归属校验

Layer 4: 数据层（DB 查询）
  所有查询强制带 tenantId 条件
  ORM 框架级别注入（Hibernate Filter）
```

### 8.2 Cilium NetworkPolicy

```yaml
apiVersion: cilium.io/v2
kind: CiliumNetworkPolicy
metadata:
  name: allow-only-gateway
spec:
  endpointSelector:
    matchLabels:
      app: datasource-service
  ingress:
    - fromEndpoints:
        - matchLabels:
            app: envoy-gateway
```

---

## 9. 风险与权衡

| 风险 | 说明 | 缓解措施 |
|------|------|---------|
| tier 变更最终一致性 | 变更后 Caffeine 缓存最多 1 分钟延迟 | Redis PubSub 主动失效；对计费敏感操作可强制实时查 |
| Keycloak 单点故障 | Keycloak 不可用时无法登录 | 多副本 + Infinispan；已颁发的 JWT 在有效期内仍可用 |
| Redis 黑名单不可用 | Redis 宕机时黑名单查询失败 | 熔断降级：Redis 不可用时跳过黑名单（接受短暂风险） |
| Admin API 限流 | 大规模 Onboarding 并发 | Temporal Activity 加速率限制，错开请求 |
| JIT Provisioning 首次延迟 | 用户首次登录时 Keycloak 需创建账号 | 通常 <100ms，可接受 |

---

## 10. 未来扩展（P2）

| 功能 | 说明 |
|------|------|
| Dedicated Realm-per-Tenant | 超大企业客户要求完全隔离，Onboarding 时注册独立 SecurityPolicy |
| MFA 支持 | Keycloak 原生支持，按 Organization 配置强制 MFA |
| 细粒度审计日志 | 记录所有 IAM 操作（登录、权限变更、封禁）至专用审计 DB |
| 用户自服务 | 密码找回、个人信息修改，通过 Keycloak Account Console |

---

## 11. 决策索引

| 决策 ID | 内容摘要 |
|---------|---------|
| IAL-001 | JWT 本地验证 + Redis JTI 黑名单 |
| IAL-002 | Standard Claims 全部强制校验，sub 使用 UUID |
| IAL-003 | Shared Realm + Organization-per-Tenant，tier 实时查询 |
| IAL-004 | 下游不引入 oauth2-resource-server，自定义 Filter 读 Header |
| IAL-005 | 不引入 TenantAwareJwtConverter |
| IAL-006 | Protocol Mapper 打平 roles，Envoy 注入 X-User-Roles |
| IAL-007 | 双层授权：Envoy RBAC + Spring Boot ABAC |
| IAL-008 | SecurityPolicy 按 Issuer 维度划分 |
| IAL-009 | BYO IdP 通过 Keycloak Identity Brokering 收敛 |
| IAL-010 | Admin API 幂等性、无 @Transactional、Kafka 事件驱动 |
