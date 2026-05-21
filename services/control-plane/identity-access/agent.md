# Identity & Access Layer Agent Guide

## Module Context

This directory describes the Identity & Access Layer (IAL) for a SaaS multi-tenant CDP data-source management platform.

IAL owns authentication, tenant isolation, route-level authorization, resource-level authorization, external IdP brokering, and tenant IAM onboarding.

Source documents:

- `IAL_PRD_身份与访问层需求文档.md`: product requirements and scope.
- `IAL_SAD_身份与访问层架构实施文档.md`: architecture and implementation decisions.

## Project Learning Goal

This project has an explicit learning goal: use the design and implementation of IAL to build a coherent knowledge system for SaaS multi-tenant authentication and authorization.

Do not treat implementation as only feature delivery. Every meaningful IAL change should help connect the concepts behind Keycloak, JWT, Envoy Gateway, tenant isolation, RBAC, ABAC, and onboarding automation.

When implementing or explaining work in this module:

- Start from the design reason: why this layer or component exists.
- Make the trust boundary explicit: who issues identity, who verifies it, who consumes it, and what must not be trusted.
- Connect each feature to the SaaS multi-tenant model: tenant identity, tenant context propagation, tenant data isolation, and tenant lifecycle.
- Include at least one minimal verification scenario for important behavior, such as missing headers, invalid tenant access, insufficient role, revoked token, or repeated onboarding event.
- Prefer incremental slices that can be observed and tested end to end.
- Call out common mistakes and prohibited shortcuts, especially mixing JWT parsing into downstream services or skipping tenant ownership checks after RBAC passes.

The desired outcome is not just a working IAL implementation, but the ability to reason independently about SaaS authentication and authorization trade-offs.

## Core Architecture

Use this model as the baseline for all implementation work:

```text
External IdP
  -> Keycloak Shared Realm: cdp
  -> Envoy Gateway
  -> Spring Boot downstream services
```

Key decisions:

- Keycloak is the identity core and the only JWT issuer trusted by the platform.
- The platform uses one shared realm named `cdp`.
- Tenants are modeled as Keycloak Organizations, one Organization per tenant.
- External IdPs such as Okta, Azure AD, and SAML providers are integrated through Keycloak Identity Brokering.
- Envoy Gateway performs JWT validation and coarse RBAC.
- Downstream Spring Boot services do not parse JWTs; they consume trusted headers injected by Envoy.
- Fine-grained authorization is enforced in application code with tenant-aware ABAC checks.

## Security Invariants

Never weaken these constraints:

- Every API request must pass through Envoy Gateway.
- Business Pods must not be reachable directly; use Cilium NetworkPolicy to allow only Envoy ingress.
- Envoy must validate JWT signature, issuer, audience, expiration, and token lifetime.
- `aud` must include `cdp-platform`.
- `sub` must be the Keycloak internal UUID. Do not use username as a stable identity key.
- JWT must include `tenant_id`.
- Roles must be flattened into top-level JWT claim `roles`.
- Envoy must inject `X-User-ID`, `X-Tenant-ID`, and `X-User-Roles`.
- Downstream services must reject requests missing `X-User-ID` or `X-Tenant-ID`.
- Every resource access must verify tenant ownership: `resource.tenantId == currentTenantId`.
- All database queries that return tenant-owned data must be scoped by `tenantId`.
- Secrets and client credentials must be stored in Vault, never config files or logs.

## Token Rules

Access tokens:

- Lifetime: 15 minutes.
- Stored by clients only.
- Validated locally by Envoy using Keycloak JWKS.
- Must include standard claims: `iss`, `sub`, `aud`, `exp`, `iat`.
- Must include platform claims: `tenant_id`, `roles`.

Refresh tokens:

- Lifetime: 7 days.
- Stored in Redis.
- Key format: `rt:{userId}:{jti}`.
- Value contains tenant ID, issued time, and device information.

Revocation:

- Use Redis JTI blocklist only for active revocation scenarios, such as administrator account ban.
- Key format: `blocklist:{jti}`.
- TTL must equal the remaining token lifetime.
- Normal token expiry should rely on the short access-token lifetime.

## Keycloak Rules

Realm and tenant model:

- Realm: `cdp`.
- Tenant isolation: Keycloak Organization per tenant.
- Organization attributes must include `tenant_id` and `tier`.
- `tenant_id` is written into JWT through Protocol Mapper.
- `tier` is not written into JWT; downstream services query it dynamically.

Protocol mappers:

- `tenant-id-mapper`: maps Organization attribute `tenant_id` to token claim `tenant_id`.
- `roles-mapper`: flattens realm roles into top-level token claim `roles`.

Client:

- Client ID: `cdp-platform`.
- Audience: `cdp-platform`.

External IdP:

- Keycloak is the broker between enterprise IdPs and the platform.
- Envoy must only trust Keycloak-issued JWTs, never external IdP tokens directly.
- First login through external IdP may use JIT provisioning to create the Keycloak user and attach it to the tenant Organization.

## Envoy Gateway Rules

Envoy owns centralized authentication and route-level RBAC:

- Use one platform SecurityPolicy for the shared Keycloak issuer.
- JWKS URI: Keycloak realm certs endpoint.
- JWKS cache duration: 300 seconds.
- Refresh JWKS immediately when JWT `kid` is missing from cache.
- Validate issuer and audience.
- Inject claims into request headers:
  - `sub` -> `X-User-ID`
  - `tenant_id` -> `X-Tenant-ID`
  - `roles` -> `X-User-Roles`
- Enforce route-level RBAC, for example `/admin/**` requires `PLATFORM_ADMIN`.

Do not move resource ownership checks into Envoy. Envoy handles coarse route access only; application services handle resource-specific authorization.

## Spring Boot Downstream Rules

Downstream services must use header-based authentication:

- Include `spring-boot-starter-security`.
- Do not include `spring-boot-starter-oauth2-resource-server`.
- Do not parse or validate JWTs in downstream services.
- Implement a `TenantAuthenticationFilter` that reads `X-User-ID`, `X-Tenant-ID`, and `X-User-Roles`.
- Build a tenant-aware `SecurityContext`.
- Reject requests without required identity headers.
- Use `@PreAuthorize` for resource-level ABAC.

Required authorization pattern:

```java
@PreAuthorize("@dataSourceAuthz.isOwner(authentication, #dataSourceId)")
```

Authorization services must load the resource, compare its `tenantId` with the authenticated tenant ID, and only then evaluate permissions.

## Tenant Tier Lookup

`tier` is intentionally not embedded in JWT because it can change independently of token lifetime.

Use the lookup chain:

```text
Caffeine local cache, TTL 1 minute
  -> Redis cache, TTL 5 minutes
  -> Keycloak Admin API
```

When tier changes, publish a Redis PubSub event and evict local Caffeine caches in all Pods.

For billing-sensitive or security-sensitive operations, consider bypassing cache or forcing a real-time lookup.

## Tenant IAM Onboarding

IAM provisioning is event-driven and idempotent.

Trigger:

- Kafka event: `TenantInfrastructureProvisionedEvent`.
- Argo Workflow must not call Keycloak directly.

Provisioning steps:

1. Ensure Keycloak Organization exists.
2. Ensure initial tenant admin user exists.
3. Add admin user to Organization.
4. Assign tenant admin role.
5. Configure BYO IdP when provided.
6. Mark IAM provisioning completed in the local database.
7. Publish `TenantIamProvisionedEvent`.

Implementation constraints:

- Keycloak Admin API calls must be idempotent.
- Handle `409 Conflict` as already-created success where appropriate.
- Do not wrap remote Keycloak calls in `@Transactional`.
- Use local database transactions only for local database updates.
- Admin API credentials must use a dedicated service account with client credentials stored in Vault.
- The service account should have only the minimum required realm-management permissions.

## Defense In Depth

Apply these layers together:

- Network layer: Cilium NetworkPolicy allows only Envoy to reach business Pods.
- Gateway layer: Envoy validates JWT, checks JTI blocklist, and enforces route RBAC.
- Application layer: Spring Security filter builds tenant context and `@PreAuthorize` enforces ABAC.
- Data layer: tenant-owned queries always include `tenantId`.

Do not rely on any single layer as the only tenant isolation boundary.

## Current Scope

In scope:

- JWT issuing, validation, and active revocation.
- Shared Realm and Organization-per-tenant identity isolation.
- Route-level RBAC and resource-level ABAC.
- BYO IdP through Keycloak Identity Brokering.
- Automated tenant IAM onboarding.

Out of scope for the current version:

- MFA.
- User self-service password recovery.
- Dedicated Realm-per-tenant, except as future P2 extension.
- Standalone audit-log subsystem.

## Development Checklist

Before implementing or reviewing changes in this module, verify:

- Does the change preserve Envoy as the centralized JWT validation layer?
- Does the downstream service avoid JWT parsing?
- Are `X-User-ID`, `X-Tenant-ID`, and `X-User-Roles` the only identity inputs trusted by application code?
- Is every tenant-owned operation scoped by authenticated tenant ID?
- Are resource ownership checks enforced before write/delete/read of sensitive resources?
- Are token lifetimes and Redis key formats consistent with the PRD?
- Are Keycloak Admin API operations idempotent and retry-safe?
- Are secrets read from Vault and excluded from logs?
- Are network policies still preventing direct Pod access?

## Prohibited Shortcuts

Do not:

- Trust external IdP tokens directly in Envoy or downstream services.
- Add `spring-boot-starter-oauth2-resource-server` to downstream business services.
- Use usernames as stable user identifiers.
- Put `tier` into JWT.
- Skip tenant ownership checks because route RBAC passed.
- Implement Keycloak remote calls inside one database transaction.
- Log tokens, client secrets, temporary passwords, or IdP credentials.
- Add a new SecurityPolicy per normal tenant in the shared-realm model.
- Treat Redis blocklist as a replacement for short access-token lifetimes.
