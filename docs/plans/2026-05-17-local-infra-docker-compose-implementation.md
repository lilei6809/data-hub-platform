# Local Infrastructure Docker Compose Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a minimal local infrastructure stack for PostgreSQL, Redis, Kafka, Keycloak, and Vault that supports current control-plane development.

**Architecture:** Use one repo-owned `docker-compose.yml` with local bootstrap assets under `infra/local`. Keep the stack intentionally small, preserve current application defaults, and separate local-only initialization from future production deployment assets.

**Tech Stack:** Docker Compose, PostgreSQL 16, Redis 7, Kafka in KRaft mode, Keycloak dev mode, Vault dev mode

---

### Task 1: Define the compose topology

**Files:**
- Create: `infra/docker-compose/docker-compose.yml`
- Create: `infra/docker-compose/.env.example`

**Step 1: Write the service list and local conventions**

Document these services in the compose file:

- `postgres`
- `redis`
- `kafka`
- `keycloak`
- `vault`

Expose ports through environment variables and use one shared network plus named volumes.

**Step 2: Add health-aware startup where it materially helps**

Add health checks for:

- `postgres`
- `redis`
- `kafka`
- `vault`

Gate `keycloak` on PostgreSQL readiness.

**Step 3: Keep local-only assumptions explicit**

Parameterize:

- image tags
- host ports
- bootstrap credentials

Mark the compose as development-only in comments.

### Task 2: Add bootstrap assets

**Files:**
- Create: `infra/local/postgres/init/001-create-databases.sh`
- Create: `infra/local/keycloak/realm-export.json`
- Create: `infra/local/vault/README.md`

**Step 1: Bootstrap PostgreSQL**

Create roles and databases for:

- service app user and `tenant_management`
- Keycloak user and `keycloak`

**Step 2: Bootstrap Keycloak**

Import one local realm with:

- development roles
- one public client
- one confidential client
- test users

**Step 3: Document Vault local assumptions**

Record the dev-only token and intended usage boundary.

### Task 3: Validate the compose definition

**Files:**
- Validate: `infra/docker-compose/docker-compose.yml`

**Step 1: Run compose config validation**

Run:

```bash
docker compose -f infra/docker-compose/docker-compose.yml --env-file infra/docker-compose/.env config
```

Expected:

- compose renders successfully
- all interpolated variables resolve

**Step 2: Record verification limits if the environment blocks execution**

If `docker` is unavailable in the current environment, record that explicitly in the delivery note.
