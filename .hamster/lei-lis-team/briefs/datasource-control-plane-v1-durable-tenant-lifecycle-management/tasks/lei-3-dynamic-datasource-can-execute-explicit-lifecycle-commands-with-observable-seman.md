---
id: "1d7cedbc-dc0e-41b3-a040-2ce838460495"
entity_type: "task"
entity_id: "a1318d26-212f-4e43-978d-abe515479cdc"
title: "dynamic-datasource can execute explicit lifecycle commands with observable semantics - Notes"
status: "todo"
priority: "high"
display_id: "LEI-3"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:40:45.489803+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Backend engineers can invoke explicit lifecycle operations on dynamic-datasource so the control plane can drive tenant execution safely and without ambiguity.

`dynamic-datasource` currently exposes a single ambiguous deregistration path that conflates multiple lifecycle intents. The control plane cannot drive it safely with a command model, and there is no way to distinguish register from activate from suspend from teardown at the execution layer.

## Experience

The `dynamic-datasource` module exposes a clean `DatasourceExecutor` interface with six discrete, named operations. Each operation has an unambiguous contract — inputs, success result, error result, and where relevant, progress information. The control plane treats this as a stable, replaceable execution boundary.

## Interaction

1. **register** — provision a JDBC datasource for a tenant; not yet query-routable
2. **activate** — make the datasource available for query routing
3. **suspend** — halt new routing while keeping the datasource registered
4. **start-drain** — begin graceful drain; returns drain progress handle
5. **close** — fully deregister and release the datasource after drain or forced teardown
6. **status** — return current execution-level state (REGISTERED | ACTIVE | SUSPENDED | DRAINING | CLOSED)

Each operation is idempotent: calling activate on an already-active datasource returns success, not an error.## Details

**User Capability**: Backend engineers can call discrete, named operations on the `dynamic-datasource` module — each with a clear contract (inputs, outputs, error conditions) — instead of a single deregistration path that conflates multiple lifecycle intents.

**Business Value**: The current `dynamic-datasource` module exposes one ambiguous deregistration path that conflates register, activate, suspend, drain-initiation, and teardown. This forces the control plane to make assumptions about execution state and cannot be driven safely by a command-and-feedback model. Explicit execution semantics create a stable, replaceable execution boundary that the control plane can call and interpret results from without ambiguity.

**Functional Requirements**:
- Expose the following discrete operations on `dynamic-datasource`: `register`, `activate`, `suspend`, `start-drain`, `close`, `status`
- Each operation must return an unambiguous result: success, failure (with reason), or in-progress (where applicable for async ops)
- `register`: provisions a JDBC datasource for a tenant without making it active for query routing
- `activate`: makes a registered datasource available for query routing
- `suspend`: halts new query routing to a datasource while leaving the datasource registered
- `start-drain`: initiates the graceful drain process; must return drain progress or a handle for polling
- `close`: fully deregisters and releases the datasource after drain completes or on forced teardown
- `status`: returns the current execution-level state of a tenant's datasource (registered, active, suspended, draining, closed)
- Operations must be idempotent where reasonable (e.g., calling `activate` on an already-active datasource returns success, not error)

**Data Model & Structure**:
- Execution state per tenant: tenant_id, execution_state (REGISTERED | ACTIVE | SUSPENDED | DRAINING | CLOSED), drain_started_at, drain_deadline, registered_at
- `start-drain` response includes: drain_id, estimated_completion, current_open_connections

**Technical Approach**:
- Refactor or extend the `dynamic-datasource` module to expose a structured `DatasourceExecutor` interface (or equivalent)
- Each operation is implemented as a distinct method/endpoint — no shared "deregister" catch-all
- Internal state tracking per tenant at the execution layer to support idempotency and `status` queries
- `start-drain` should track open connections and signal completion when they reach zero or deadline is exceeded

**User Workflows**:
1. Control plane receives a command to onboard tenant X
2. Control plane calls `register(tenantId, jdbcConfig)` → gets success
3. Control plane calls `activate(tenantId)` → datasource is now query-routable
4. On suspension command, control plane calls `suspend(tenantId)` → queries stop routing
5. On offboard command, control plane calls `start-drain(tenantId)` → drain begins
6. Control plane polls or receives callback when drain completes, then calls `close(tenantId)`
7. At any point, control plane can call `status(tenantId)` to get current execution state

**API/Interface Specifications**:
- `DatasourceExecutor` interface with methods: `register(tenantId, config)`, `activate(tenantId)`, `suspend(tenantId)`, `startDrain(tenantId)`, `close(tenantId)`, `status(tenantId)`
- Each method returns a typed result object with status and optional error/progress payload
- `startDrain` returns a drain handle with progress information

**Scope - INCLUDED**: Defining and implementing the six explicit operations on dynamic-datasource, execution-level state tracking, idempotency for each operation, `status` query support, drain progress tracking at the executor level
**Scope - EXCLUDED**: Control-plane command model (covered by command orchestration task), DRAINING workflow orchestration and timeouts at the control-plane level (covered by offboarding task), tenant state machine (covered by state machine task)

**Success Criteria**:
- All six operations (`register`, `activate`, `suspend`, `start-drain`, `close`, `status`) are individually callable with distinct inputs and outputs
- No ambiguous "deregister" path remains as the sole offboarding mechanism
- Calling any operation in an already-matching state returns a success/no-op result, not an error
- `status` returns the correct execution-level state for any registered tenant
- `start-drain` returns drain progress information
- The control plane can drive a complete onboard→active→suspend→drain→close sequence via these operations

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | Tenant lifecycle state machine can be managed as durable runtime state |
| testStrategy | Call each of the six operations independently and verify correct result contracts. Call each operation when the datasource is already in the matching state and confirm idempotent success. Drive a full onboard→activate→suspend→start-drain→close sequence and verify correct state progression. Call `status` at each stage and confirm it reflects the correct execution state. Verify that the old ambiguous deregistration path is no longer the primary offboarding mechanism. |

