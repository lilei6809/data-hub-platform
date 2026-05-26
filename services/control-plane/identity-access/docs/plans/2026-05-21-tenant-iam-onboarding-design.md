# Tenant IAM Onboarding Design

## Goal

Implement tenant IAM onboarding as an extensible reconciliation workflow, not as a one-time creation script.

The first implementation slice should provision the minimum IAM facts required for a tenant to authenticate and be recognized by downstream services:

- Keycloak Organization exists.
- Organization has `tenant_id` and `tier` attributes.
- Initial tenant administrator user exists.
- Administrator is a member of the Organization.
- Administrator has `TENANT_ADMIN`.
- Local provisioning state records IAM completion.

This design also supports the project learning goal: use the implementation to build a clear mental model for SaaS multi-tenant authentication and authorization.

## Core Idea

Tenant onboarding should describe the desired IAM state for a tenant, then reconcile Keycloak and local state until reality matches that desired state.

Instead of thinking:

```text
create organization
create user
assign role
finish
```

Think:

```text
tenant acme-corp should have:
  organization acme-corp
  attributes tenant_id=acme-corp, tier=STANDARD
  admin user admin@acme.com
  admin membership in organization
  TENANT_ADMIN role

each step checks current state and creates, updates, or skips as needed
```

This makes retries safe. If the workflow fails halfway, the next run can continue from the already-created state.

## Input Model

First version input:

```json
{
  "tenantId": "acme-corp",
  "tier": "STANDARD",
  "adminEmail": "admin@acme.com"
}
```

The domain model should leave room for future fields:

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

Initial defaults:

- `identityMode`: `LOCAL_ONLY`
- `realmStrategy`: `SHARED_REALM`
- `identityProviders`: empty
- `policies`: empty

Future supported modes:

- `BROKERED_IDP`: tenant uses external IdP through Keycloak Identity Brokering.
- `DEDICATED_REALM`: tenant gets a separate Keycloak realm.
- `MFA_REQUIRED`: tenant or organization requires MFA policy.

## Architecture

Use a clean ports-and-adapters shape:

```text
TenantIamProvisioningService
  -> TenantIamProvisioningStep[]
  -> KeycloakAdminPort
  -> TenantIamStateRepository
  -> SecretStorePort
  -> EventPublisher
```

The use case owns orchestration. Adapters own external details.

The application core must not directly depend on Keycloak SDK, Kafka client, Vault client, or database-specific APIs.

## Step Pipeline

First version steps:

```text
EnsureOrganizationStep
EnsureOrganizationAttributesStep
EnsureAdminUserStep
EnsureOrganizationMembershipStep
EnsureTenantAdminRoleStep
MarkIamProvisionedStep
PublishTenantIamProvisionedEventStep
```

Future steps can be added without rewriting the core flow:

```text
ConfigureIdentityProviderStep
ConfigureMfaPolicyStep
ConfigureDedicatedRealmStep
ConfigureProtocolMapperStep
ConfigureClientAudienceStep
ConfigureTenantRoleMappingsStep
```

Each step must be idempotent:

- If the target object already exists, return success.
- If the target relationship already exists, return success.
- If attributes exist but differ, update them deliberately.
- If Keycloak returns `409 Conflict` for an already-created object, resolve the existing object and continue.

## State Machine

Avoid a single boolean such as `iamProvisioned`.

Use explicit provisioning states:

```text
PENDING_IAM
IAM_PROVISIONING
IAM_PROVISIONED
IAM_FAILED
```

Recommended state fields:

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

State transitions:

```text
PENDING_IAM -> IAM_PROVISIONING
IAM_PROVISIONING -> IAM_PROVISIONED
IAM_PROVISIONING -> IAM_FAILED
IAM_FAILED -> IAM_PROVISIONING
```

Do not mark the tenant active just because IAM provisioning succeeded. IAM completion should advance the tenant toward `PENDING_ACTIVATION`; other bounded contexts may still need initialization.

## Ports

`KeycloakAdminPort` should expose intent-based operations:

```text
ensureOrganization(tenantId, attributes)
ensureUser(email, temporaryCredentialPolicy)
ensureOrganizationMembership(organizationId, userId)
ensureRealmRole(realmRoleName)
ensureUserRealmRole(userId, realmRoleName)
```

Future operations:

```text
ensureIdentityProvider(tenantId, idpConfig)
ensureProtocolMapper(clientId, mapperConfig)
ensureClientAudience(clientId, audience)
ensureMfaPolicy(tenantId, policy)
```

`TenantIamStateRepository` owns local provisioning state:

```text
markProvisioningStarted(tenantId)
markProvisioned(tenantId)
markFailed(tenantId, failure)
findStatus(tenantId)
```

`EventPublisher` owns outbound events:

```text
publishTenantIamProvisioned(event)
publishTenantIamProvisioningFailed(event)
```

`SecretStorePort` is reserved for future BYO IdP and service account secrets:

```text
readSecret(path)
writeSecret(path, value)
```

## Transaction Boundaries

Do not wrap Keycloak Admin API calls in a database transaction.

Remote calls must be safe through idempotency, not rollback.

Use local database transactions only for local state changes such as:

- Marking provisioning started.
- Marking provisioning completed.
- Recording failure information.

## Events

Input event:

```text
TenantInfrastructureProvisionedEvent
  tenantId
  tier
  adminEmail
  correlationId
```

Output event:

```text
TenantIamProvisionedEvent
  tenantId
  organizationId
  adminUserId
  correlationId
```

Failure event:

```text
TenantIamProvisioningFailedEvent
  tenantId
  failureCode
  retryable
  correlationId
```

The first implementation may call the use case directly from tests or a local runner, but the design target remains Kafka-driven onboarding.

## Trust Boundary

Onboarding creates identity facts. It does not authenticate requests.

Authentication path remains:

```text
Keycloak issues JWT
  -> Envoy validates JWT
  -> Envoy injects X-User-ID, X-Tenant-ID, X-User-Roles
  -> downstream services build SecurityContext from headers
```

The Organization `tenant_id` attribute is important because it becomes the JWT `tenant_id` claim through Protocol Mapper.

## Minimal Verification Scenarios

First version should prove:

- Running onboarding once creates Organization, admin user, membership, and role assignment.
- Running onboarding twice succeeds without duplicates or fatal conflicts.
- Failing after Organization creation and retrying continues from existing Organization.
- Organization contains `tenant_id` and `tier`.
- Admin user can be resolved by email.
- Admin user is a member of the tenant Organization.
- Admin user has `TENANT_ADMIN`.
- Local provisioning state reaches `IAM_PROVISIONED`.

Later end-to-end verification should prove:

- Admin login produces JWT with `tenant_id`.
- JWT includes flattened `roles`.
- Envoy can inject `X-Tenant-ID` and `X-User-Roles`.
- Downstream ABAC rejects cross-tenant resource access.

## First Implementation Scope

Implement now:

- Desired-state request model with extensible fields and defaults.
- Step pipeline abstraction.
- Keycloak admin port interface.
- Fake/in-memory Keycloak adapter for tests.
- Local provisioning state abstraction.
- Unit tests for idempotent step execution.

Defer:

- Real Keycloak adapter details.
- BYO IdP configuration.
- MFA policies.
- Dedicated realm strategy.
- Kafka integration.
- Vault integration.

## Design Principle

The first version should be small in behavior but strong in shape.

The implementation should teach and preserve these concepts:

- Tenant IAM onboarding defines durable identity facts.
- Organization is the tenant identity boundary in the shared realm model.
- `tenant_id` bridges Keycloak identity to business data isolation.
- Roles authorize actions but do not replace tenant ownership checks.
- Idempotent reconciliation is the correct model for remote IAM provisioning.

