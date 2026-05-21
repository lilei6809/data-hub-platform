# Local Infra Docker Compose Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a minimal local infrastructure stack for development with PostgreSQL, Redis, Kafka, Keycloak, and Vault.

**Architecture:** Keep all local runtime orchestration under `infra/docker-compose/`, keep bootstrap assets under `infra/local/`, and prefer explicit, low-complexity defaults that match the current first-wave services. Use PostgreSQL init scripting and Keycloak realm import so local integration has stable bootstrap state.

**Tech Stack:** Docker Compose, PostgreSQL, Redis, Kafka in KRaft mode, Keycloak, Vault

---

### Task 1: Document the local compose design

**Files:**
- Create: `docs/plans/2026-05-17-local-infra-docker-compose-design.md`

**Step 1: Write the design document**

Document:

- the minimal-local scope
- included services
- excluded services
- file layout
- bootstrap assumptions
- success criteria

**Step 2: Review the design for consistency**

Check:

- service list matches `AGENTS.md`
- no production-only claims are made
- local-only caveats are explicit

### Task 2: Create the compose environment template

**Files:**
- Create: `infra/docker-compose/.env.example`

**Step 1: Define stable local variables**

Include exact variables for:

- container ports
- database bootstrap names
- database credentials
- Keycloak admin credentials
- Vault dev token

**Step 2: Keep values aligned with current service defaults**

Ensure:

- PostgreSQL host port remains `5432`
- a `tenant_management` database exists
- a local app user `user/password` is provisioned

### Task 3: Add PostgreSQL bootstrap script

**Files:**
- Create: `infra/local/postgres/init/001-init-databases.sh`

**Step 1: Write idempotent bootstrap logic**

Bootstrap:

- `tenant_management` database
- `keycloak` database
- `user` application account

**Step 2: Use container environment variables**

Reference:

- `POSTGRES_USER`
- `POSTGRES_DB`
- `POSTGRES_TENANT_MANAGEMENT_DB`
- `POSTGRES_KEYCLOAK_DB`
- `APP_DB_USER`
- `APP_DB_PASSWORD`

### Task 4: Add Keycloak local realm import

**Files:**
- Create: `infra/local/keycloak/realm-export.json`

**Step 1: Define a minimal realm**

Create:

- realm `data-hub-local`
- public client `control-plane-api`
- test user `platform-admin`

**Step 2: Keep it minimal**

Do not add:

- production role modeling
- complex protocol mappers
- advanced identity flows

### Task 5: Create the compose stack

**Files:**
- Create: `infra/docker-compose/docker-compose.yml`

**Step 1: Add the service definitions**

Define:

- `postgres`
- `redis`
- `kafka`
- `keycloak`
- `vault`

**Step 2: Add bootstrap wiring**

Wire:

- postgres init directory mount
- keycloak realm import mount
- named volumes for data persistence
- shared bridge network

**Step 3: Add safe local defaults**

Include:

- explicit port mappings
- restart policy
- local-only comments
- health checks where lightweight and reliable

### Task 6: Update repository progress tracking

**Files:**
- Modify: `AGENTS.md`

**Step 1: Add the new current-state item**

Record that:

- local infrastructure compose scaffolding now exists under `infra/docker-compose/`

**Step 2: Keep architecture unchanged**

Do not:

- alter service boundaries
- reframe platform architecture
- change technology decisions

### Task 7: Verify the compose configuration

**Files:**
- Verify: `infra/docker-compose/docker-compose.yml`
- Verify: `infra/docker-compose/.env.example`

**Step 1: Render the compose file**

Run:

```bash
docker compose --env-file infra/docker-compose/.env -f infra/docker-compose/docker-compose.yml config
```

Expected:

- compose renders without syntax errors
- environment variables resolve cleanly

**Step 2: Fix any validation errors**

Adjust:

- YAML structure
- variable references
- health-check command formatting

### Task 8: Summarize the resulting local entry point

**Files:**
- Reference: `infra/docker-compose/docker-compose.yml`
- Reference: `infra/docker-compose/.env.example`

**Step 1: Document the operator path in the final response**

Explain:

- where the compose file lives
- which services are included
- how it was validated

**Step 2: Call out limitations**

Mention:

- no Envoy yet
- no PgBouncer yet
- Vault is dev-mode only
