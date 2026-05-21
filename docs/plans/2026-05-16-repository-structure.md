# Repository Structure Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Establish the initial repository directory skeleton for the CDP platform so Control Plane and Application Plane services can be scaffolded consistently.

**Architecture:** The repository is organized as `plane -> layer -> service` for service directories, while shared assets live in dedicated top-level areas for libraries, contracts, infrastructure, and documentation. Only `*-service` directories are intended to become deployable Spring Boot applications; higher-level directories are grouping boundaries only.

**Tech Stack:** Git repository structure, Spring Boot microservice layout planning, Gradle-based multi-service repository conventions

---

### Task 1: Create top-level repository groups

**Files:**
- Create: `services/`
- Create: `libraries/`
- Create: `contracts/`
- Create: `infra/`
- Create: `docs/`

**Step 1: Create the directories**

Run: `mkdir -p services libraries contracts infra docs`
Expected: directories exist at repository root

**Step 2: Verify structure**

Run: `find . -maxdepth 1 -type d | sort`
Expected: the repository root includes all five top-level groups

### Task 2: Create service grouping directories

**Files:**
- Create: `services/control-plane/tenant-lifecycle/`
- Create: `services/control-plane/identity-access/`
- Create: `services/control-plane/datasource-governance/`
- Create: `services/control-plane/resource-governance/`
- Create: `services/application-plane/`

**Step 1: Create control-plane and application-plane groupings**

Run: `mkdir -p services/control-plane services/application-plane`
Expected: both plane directories exist

**Step 2: Create layer groupings**

Run: `mkdir -p services/control-plane/tenant-lifecycle services/control-plane/identity-access services/control-plane/datasource-governance services/control-plane/resource-governance`
Expected: layer directories exist under `services/control-plane`

### Task 3: Create first-batch service directories

**Files:**
- Create: `services/control-plane/tenant-lifecycle/onboarding-service/`
- Create: `services/control-plane/tenant-lifecycle/offboarding-service/`
- Create: `services/control-plane/tenant-lifecycle/tenant-management-service/`
- Create: `services/control-plane/identity-access/token-enrichment-service/`
- Create: `services/control-plane/datasource-governance/datasource-config-service/`
- Create: `services/control-plane/resource-governance/policy-engine-service/`

**Step 1: Create the six approved Control Plane services**

Run: `mkdir -p services/control-plane/tenant-lifecycle/onboarding-service services/control-plane/tenant-lifecycle/offboarding-service services/control-plane/tenant-lifecycle/tenant-management-service services/control-plane/identity-access/token-enrichment-service services/control-plane/datasource-governance/datasource-config-service services/control-plane/resource-governance/policy-engine-service`
Expected: all six leaf service directories exist

**Step 2: Add tracked placeholders**

Create empty `.gitkeep` files in each leaf service directory.
Expected: Git can track the skeleton before real code exists

### Task 4: Create reserved Application Plane service placeholders

**Files:**
- Create: `services/application-plane/datasource-lifecycle-service/`
- Create: `services/application-plane/connection-serving-service/`
- Create: `services/application-plane/health-monitoring-service/`

**Step 1: Create placeholder application-plane service directories**

Run: `mkdir -p services/application-plane/datasource-lifecycle-service services/application-plane/connection-serving-service services/application-plane/health-monitoring-service`
Expected: application-plane placeholders exist for future work

**Step 2: Add tracked placeholders**

Create empty `.gitkeep` files in each application-plane leaf directory.
Expected: the future service boundaries are visible in the repository

### Task 5: Create shared support directories

**Files:**
- Create: `libraries/platform-observability/`
- Create: `libraries/platform-web/`
- Create: `libraries/platform-events/`
- Create: `libraries/platform-test-support/`
- Create: `contracts/openapi/`
- Create: `contracts/events/`
- Create: `infra/docker-compose/`
- Create: `infra/helm/`
- Create: `infra/local/`
- Create: `docs/architecture/`
- Create: `docs/plans/`

**Step 1: Create shared support directories**

Run: `mkdir -p libraries/platform-observability libraries/platform-web libraries/platform-events libraries/platform-test-support contracts/openapi contracts/events infra/docker-compose infra/helm infra/local docs/architecture docs/plans`
Expected: shared support directories exist

**Step 2: Add tracked placeholders where empty**

Create empty `.gitkeep` files in each currently empty directory.
Expected: Git tracks the scaffold cleanly

### Task 6: Verify and document the resulting structure

**Files:**
- Modify: `docs/plans/2026-05-16-repository-structure.md`

**Step 1: Verify the tree**

Run: `find services libraries contracts infra docs -maxdepth 4 | sort`
Expected: all approved directories are present in the expected grouping hierarchy

**Step 2: Use this structure as the baseline for future scaffolding**

The next implementation step is to scaffold `tenant-management-service` as the first real Spring Boot service under the established layout.
