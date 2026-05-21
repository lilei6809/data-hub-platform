# Local Infrastructure Docker Compose Design

## Goal

Provide a minimal local infrastructure stack for development of the first control-plane services.

The stack should be easy to start, align with the repository's architecture constraints, and avoid premature production complexity.

## Scope

This design covers a local-only `docker-compose` setup for:

- `PostgreSQL`
- `Redis`
- `Kafka`
- `Keycloak`
- `Vault`

It explicitly does not include:

- `Envoy Gateway`
- `PgBouncer`
- observability stack (`Jaeger`, `Loki`, `Prometheus`, `Grafana`)
- Kubernetes-only operators such as `Strimzi`, `Flux`, `KEDA`, `Argo Workflows`

## Design Decisions

### 1. Minimal viable local stack

The compose file targets developer bootstrap, not production parity.

That means:

- one container per infrastructure dependency
- single-node Kafka in KRaft mode
- `Vault` in dev mode
- `Keycloak` in `start-dev` mode with local realm import

This keeps the first local environment small enough to boot quickly while still supporting the current first-wave services.

### 2. Compatibility with current service defaults

`tenant-management-service` already defaults to:

- host: `localhost`
- port: `5432`
- database: `tenant_management`
- username: `user`
- password: `password`

The PostgreSQL bootstrap will therefore create a matching local database and application user to avoid immediate application reconfiguration.

### 3. Explicit local-only credentials

All usernames, passwords, ports, and image tags are parameterized through `.env`.

This serves two purposes:

- make local overrides easy
- make it obvious that these credentials are development placeholders only

### 4. Bootstrap assets live under `infra/local`

Local bootstrap artifacts should remain versioned and explicit:

- PostgreSQL init SQL under `infra/local/postgres/init`
- Keycloak realm import under `infra/local/keycloak`
- Vault local notes under `infra/local/vault`

This avoids hidden mutable state and keeps local environment assumptions inspectable in the repo.

## Service Layout

### PostgreSQL

- single container
- local superuser for admin bootstrap
- creates:
  - `tenant_management` database owned by app user
  - `keycloak` database owned by Keycloak user

### Redis

- single instance
- append-only enabled for better local state continuity

### Kafka

- single-node KRaft mode
- internal listener for container-to-container traffic
- external listener for host access from local services and tools

### Keycloak

- uses PostgreSQL backend
- imports a local `data-hub-local` realm at startup
- exposes admin login and a small set of development clients/users

### Vault

- dev server only
- fixed local root token
- no attempt to model production HA, storage backend, or auth methods yet

## Ports

- PostgreSQL: `5432`
- Redis: `6379`
- Kafka external: `29092`
- Keycloak: `18080`
- Vault: `8200`

These defaults avoid conflict with the current service port `8081` and leave room for future local app processes.

## Known Limits

- `Vault` dev mode is insecure by design
- Kafka is single broker and unsuitable for durability testing
- Keycloak bootstrap is minimal and intended only for local login/token experiments
- no gateway-level JWT verification is modeled yet
- no transaction pooler is modeled yet, so `PgBouncer` constraints are documented but not exercised locally

## Next Expansion Path

If local integration scope expands, the next additions should be:

1. `PgBouncer`
2. `Envoy Gateway` local stub
3. optional observability profile
