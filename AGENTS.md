# AGENTS.md

This repository builds a `CDP DataSource Management Platform`.

Read this file first in every new session. It captures the project vision, system boundaries, service map, technology decisions, current progress, and the next work items.

## Vision

The platform is a multi-tenant control system for datasource management in a CDP-style product.

Its purpose is to:

- onboard and offboard tenants safely
- manage tenant metadata, datasource configuration, credentials, quotas, and policies
- expose a clean runtime boundary so the `Application Plane` can execute business data operations without owning governance concerns
- guarantee tenant isolation, operational control, auditability, and future scale

The platform does **not** treat Control Plane services as the place where tenant raw business data is processed. Control Plane manages metadata, configuration, identity, policies, and operations.

## Architecture Summary

The system is split into:

- `Infrastructure Layer`
  - `Envoy Gateway`
  - `Keycloak`
  - `Kafka`
  - `Redis`
  - `Vault`
  - `PostgreSQL`
- `Control Plane`
  - governance, orchestration, identity context, policy, configuration, operations
- `Application Plane`
  - runtime datasource lifecycle, connection serving, health monitoring, future business execution services

Important boundary:

- `plane` is an architecture grouping
- `layer` is a domain grouping
- only each `*-service` is a real implementation unit and deployable microservice

## Repository Shape

The repository uses `single repo + multiple independent service directories`.

Current structure:

```text
pom.xml
AGENTS.md
services/
  control-plane/
    tenant-lifecycle/
    identity-access/
    datasource-governance/
    resource-governance/
  application-plane/
libraries/
contracts/
infra/
docs/
```

Build boundary rules:

- only the root has the aggregator parent `pom.xml`
- `control-plane/` and each layer directory do not have `pom.xml`
- only real service directories have their own module `pom.xml`

## Technology Stack

These are current confirmed decisions and should be treated as constraints unless explicitly changed.

- language/runtime: `Java 21`
- framework: `Spring Boot 3.x`
- build: `Maven`
- concurrency model: `Virtual Threads`
- persistence access: `Spring Data JDBC`
- database: `PostgreSQL`
- tenant isolation: `Schema-per-Tenant`
- migrations: `Flyway`
- gateway: `Envoy Gateway`
- identity provider: `Keycloak`
- messaging: `Apache Kafka + Spring Cloud Stream`
- kafka operations: `Strimzi`
- cache: `Redis`
- secrets: `HashiCorp Vault`
- JVM-side connection pool: `HikariCP`
- infra-side connection pool: `PgBouncer`
- resilience: `Resilience4j`
- deployment: `Kubernetes on EKS`
- packaging/deploy config: `Helm`
- gitops: `Flux`
- workflow orchestration: `Argo Workflows`
- autoscaling: `KEDA`
- observability: `OpenTelemetry`, `Jaeger`, `Loki`, `Prometheus`, `Grafana`

## Explicit Non-Choices

Do not drift into these without a conscious architecture change:

- no `Gradle`
- no `JPA/Hibernate`
- no `MyBatis`
- no `Spring Cloud Gateway`
- no `WebFlux/Reactor` as the default programming model
- no `Temporal` at the current stage
- no per-tenant Kafka topics

## Critical Engineering Constraints

- `Schema-per-Tenant` is mandatory for tenant data isolation.
- schema switching must use `SET LOCAL search_path = ...` inside an explicit transaction
- never use session-level `SET search_path`
- `PgBouncer` pooling mode is `transaction`
- cross `DB + Kafka` consistency must use an `Outbox Pattern`
- JWT verification happens at `Envoy Gateway`
- `claimToHeaders` should be used so downstream services consume trusted headers instead of decoding JWT directly
- Redis can cache enrichment, rate limit counters, pool state, and API key lookups
- secrets must not be stored in Redis or in datasource config tables

## Service Map

### Control Plane Layers

#### 1. Tenant Lifecycle Layer

- `onboarding-service`
  - tenant onboarding orchestration
  - state machine
  - checkpoint and retry
- `offboarding-service`
  - tenant offboarding orchestration
  - GDPR deletion workflow
  - checkpoint and retry
- `tenant-management-service`
  - tenant metadata source of truth
  - tier, status, region, plan config
  - tenant lifecycle state changes

#### 2. Identity & Access Layer

- `iam-provisioning-service`
  - tenant IAM onboarding in the shared Keycloak realm
  - derive desired IAM state from tenant infrastructure lifecycle events
  - provision Keycloak Organization, initial admin user, realm role memberships, and local provisioning state idempotently
  - consume `TenantInfrastructureProvisionedEvent` and publish `TenantIamProvisionedEvent`
  - expose a manual operator trigger at `POST /admin/tenants/{tenantId}/provision-iam`
- `token-enrichment-service`
  - resolve tenant business context
  - inject `tenant_id`, `tier`, `feature_flags`, quota-related attributes
  - support Envoy ExtAuth-style enrichment
- `api-key-management-service`
  - planned
  - API key lifecycle, rotation, revocation, audit

#### 3. DataSource Governance Layer

- `connector-registry-service`
  - planned
  - global connector type metadata
- `datasource-config-service`
  - tenant datasource configuration lifecycle
  - connector reference, ownership type, non-secret connection parameters, status
- `credential-lifecycle-service`
  - planned
  - Vault wrapper and secret rotation orchestration
- `schema-provisioning-service`
  - planned
  - tenant schema create/migrate/drop

#### 4. Connection Resource Governance Layer

- `policy-engine-service`
  - target-state and policy decision engine
  - input: tier, quotas, health signals, resource state
  - output: decisions, not direct pool execution
- `connection-pool-manager-service`
  - planned
  - execute pool lifecycle and resizing decisions
- `quota-throttle-service`
  - planned
  - enforce resource quotas and throttling

#### 5. Observability & Operations Layer

- `health-signal-processor-service`
  - planned
  - interpret health signals, trigger business decisions
- `usage-metering-service`
  - planned
  - aggregate usage and report to billing
- `audit-log-service`
  - planned
  - immutable audit chain
- `alert-notification-service`
  - planned
  - thresholds and operator notifications

#### 6. Admin & Configuration Layer

- `admin-console-service`
  - planned
  - internal operator entry point
- `feature-flag-service`
  - planned
  - tenant-scoped feature rollout
- `tier-policy-config-service`
  - planned
  - tier and SLA policy definitions

### Application Plane

- `datasource-lifecycle-service`
  - runtime datasource lifecycle execution
- `connection-serving-service`
  - connection request routing and pool reuse
- `health-monitoring-service`
  - probing and health signal emission

## First Implementation Batch

The first batch currently in scope is:

- `tenant-management-service`
- `onboarding-service`
- `offboarding-service`
- `iam-provisioning-service`
- `token-enrichment-service`
- `datasource-config-service`
- `policy-engine-service`

Maturity target for this batch:

- `tenant-management-service`, `onboarding-service`, `datasource-config-service`
  - real first-wave services
- `offboarding-service`, `token-enrichment-service`, `policy-engine-service`
  - standard skeleton plus clear contracts and placeholders
- `iam-provisioning-service`
  - current active Identity & Access implementation slice for tenant IAM onboarding

## Current Status

As of `2026-05-29`:

- repository directory structure and root Maven aggregator are in place
- root Maven dependency management includes shared versions for Keycloak Admin Client, Lombok, MapStruct, Resilience4j, Spring Cloud, SpringDoc, WireMock, Testcontainers, and ArchUnit
- root Maven plugin management owns Java 21 compiler configuration, annotation processors, Failsafe `**/*IT.java` integration-test conventions, default `skipITs=true`, and the optional `wsl2-docker-desktop` profile for local Testcontainers runs
- `iam-provisioning-service` exists under `services/control-plane/identity-access/` with Spring Boot, DDD/hexagonal package structure, Flyway migration scaffold, and service-local dependencies
- `LEI-70` desired-state modeling work is implemented:
  - base value objects and `DomainValidationException`
  - `AdminUser` and `TemporaryCredentialPolicy`
  - `IdentityMode`, `RealmStrategy`, and placeholder extension value objects
  - `TenantIamDesiredState` aggregate with minimal-input factory and invariant tests
- `iam-provisioning-service` application flow is implemented for the current slice:
  - `TenantInfrastructureProvisionedEvent` is translated to `TenantIamDesiredState`
  - `TenantIamOnboardingService` delegates to `ProvisionTenantIamUseCase`
  - `TenantIamProvisioningService` coordinates the ordered Step Pipeline
  - pipeline steps currently ensure Keycloak Organization, initial admin user, tenant-admin realm role assignment, and Organization membership
  - step completion is checkpointed in `TenantIamProvisioningState`
- `KeycloakAdminPort` now defines the application-core contract for idempotent Keycloak operations
- `FakeKeycloakAdminPort` exists for contract/application tests and includes basic fault injection
- `RealKeycloakAdminPort` exists for Keycloak Admin API integration and handles core ensure semantics, including `409 Conflict` as already-created success where applicable
- local provisioning state is currently backed by `InMemoryTenantIamProvisioningStateRepository` with snapshot isolation and optimistic version checks; persistent JDBC state is not implemented yet
- event publishing is currently represented by `EventPublisher` and an in-memory publisher; Kafka binding and the required Outbox Pattern are not implemented yet
- Vault integration is still a placeholder; `VaultSecretStore` is not production-ready
- messaging adapter is intentionally thin and currently has no Kafka listener annotation; it is ready to be wired once the event transport contract is finalized
- HTTP manual trigger exists for operator-driven IAM provisioning retries or manual execution
- local development infrastructure compose scaffolding exists under `infra/docker-compose/`
- local Keycloak realm import exists for the shared realm `cdp-auth-pool`
- current focused test suite for `iam-provisioning-service` passes with `mvn -pl services/control-plane/identity-access/iam-provisioning-service -am test -DskipITs=true` (`96` tests)
- Keycloak/Testcontainers resources exist under `iam-provisioning-service/src/test/resources`; integration tests should be run through `mvn verify -DskipITs=false`, and local WSL2 Docker Desktop users can add `-Pwsl2-docker-desktop`

Known production gaps and safety issues:

- remove any logging of Keycloak admin client credentials or generated temporary passwords before continuing toward production readiness
- remove local default Keycloak client secret values from committed application configuration; secrets must come from environment/Vault only
- replace in-memory provisioning state and in-memory event publishing with PostgreSQL/Flyway-backed state plus Outbox Pattern
- wire real Kafka consumption/publication after event schemas and topic names are finalized
- finish Vault-backed secret access before relying on service-account credentials outside local development
- review current Keycloak role strategy: code assigns `TENANT_ADMIN`; older docs also mention `data_engineer` and `viewer`, so the MVP role set must be reconciled before broadening provisioning

Relevant files:

- [README.md](/home/leland/projects/data-hub-platform/README.md:1)
- [IAM provisioning service](/home/leland/projects/data-hub-platform/services/control-plane/identity-access/iam-provisioning-service/pom.xml:1)
- [Identity & Access agent guide](/home/leland/projects/data-hub-platform/services/control-plane/identity-access/agent.md:1)
- [Identity & Access DDD document](/home/leland/projects/data-hub-platform/services/control-plane/identity-access/docs/DDD.md:1)

## What To Build Next

Recommended next steps, in order:

1. fix credential hygiene immediately:
   - remove Keycloak admin client secret logging
   - remove generated temporary password logging
   - remove committed default client secret from `application.yml`
2. harden and align the Keycloak Admin adapter:
   - reconcile the MVP role set (`TENANT_ADMIN` only vs `TENANT_ADMIN`, `data_engineer`, `viewer`)
   - ensure every Keycloak SDK/HTTP failure is translated at the infrastructure boundary
   - add or promote real Keycloak Testcontainers coverage to Failsafe `*IT` tests
3. implement persistent local provisioning state:
   - JDBC/Flyway table for `TenantIamProvisioningState`
   - optimistic locking/version checks in the JDBC repository
   - retry query for `IAM_AWAITING_RETRY`
4. implement reliable event boundaries:
   - Kafka consumer for `TenantInfrastructureProvisionedEvent`
   - Outbox Pattern for `TenantIamProvisionedEvent` and `TenantIamProvisioningFailedEvent`
   - avoid direct publish after DB state mutation once persistent state exists
5. finish secret-management integration:
   - implement `SecretStorePort` with Vault-backed adapter
   - inject Keycloak service-account credential through the secret boundary
6. after IAM provisioning reaches persistent/evented MVP, return to the first-batch services:
   - `tenant-management-service`
   - `onboarding-service`
   - `datasource-config-service`
   - skeleton contracts for `offboarding-service`, `token-enrichment-service`, and `policy-engine-service`

## Working Rules For Future Sessions

- preserve the distinction between architecture grouping and deployable service boundaries
- prefer small, explicit dependencies in each service module
- keep services independent; do not introduce direct service-to-service module dependencies
- do not introduce shared code prematurely
- when in doubt, re-check `README.md`, `技术栈.md`, and this file before changing architecture
- update this file when major decisions, scope, or current phase changes
