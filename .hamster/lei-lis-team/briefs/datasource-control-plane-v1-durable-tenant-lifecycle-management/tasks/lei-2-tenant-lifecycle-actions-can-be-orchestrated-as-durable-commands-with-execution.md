---
id: "fdd9ce5f-d328-4ff7-9fe7-5bc0f7f4e800"
entity_type: "task"
entity_id: "143db2d9-61cc-4068-b884-050827fe5b4b"
title: "Tenant lifecycle actions can be orchestrated as durable commands with execution feedback - Notes"
status: "todo"
priority: "urgent"
display_id: "LEI-2"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:40:12.691216+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Platform engineers can trigger tenant lifecycle actions as durable, auditable commands so that orchestration is decoupled from execution and safe to retry.

Control-plane logic is currently coupled directly to synchronous execution. There is no audit trail of what was commanded, no safe retry path, and replacing the underlying executor requires rewriting orchestration code.

## Experience

Every lifecycle action — onboard, suspend, resume, offboard — is issued as a named command with a unique correlation ID. Commands are persisted before dispatch, executed asynchronously, and advanced in status via explicit execution feedback. Engineers can query command history at any time to understand what happened for any tenant.

## Interaction

1. Upstream system (or engineer) calls `POST /tenants/{tenantId}/commands` with the desired action and a correlation ID
2. Control plane persists the command (status: PENDING) and dispatches it asynchronously to the executor
3. The executor reports progress — command advances to IN_PROGRESS → SUCCEEDED or FAILED
4. On failure, retry logic re-dispatches up to a configured limit
5. Execution feedback updates the tenant's actual state in the runtime record
6. Engineers can call `GET /tenants/{tenantId}/commands/{commandId}` to audit exactly what happened and when## Details

**User Capability**: Platform engineers and upstream systems can trigger tenant lifecycle actions through a command model where each action is recorded durably, dispatched asynchronously to executors, and advanced in state only upon confirmed execution feedback — eliminating tight synchronous coupling between control-plane orchestration and execution.

**Business Value**: Current control-plane logic is tightly coupled to synchronous execution. This makes it impossible to audit what happened, retry safely on transient failure, or swap out dynamic-datasource for a different pool manager without rewriting orchestration code. A command-and-feedback model creates a stable contract between the control plane and its executors.

**Functional Requirements**:
- Model each lifecycle action (onboard, suspend, resume, offboard) as a named, durable command with a unique correlation ID
- Persist each command before dispatching it so it can be replayed after a restart
- Dispatch commands to an executor (dynamic-datasource or future pool manager) without blocking the calling thread on the final outcome
- Accept execution feedback (success, failure, partial progress) as callbacks or events that advance the tenant's actual state
- Expose an API for enqueueing lifecycle commands with idempotency guarantees (same correlation ID = no duplicate command)
- Support command status querying: PENDING, IN_PROGRESS, SUCCEEDED, FAILED
- Provide a retry mechanism for FAILED commands up to a configurable limit

**Data Model & Structure**:
- Command record: command_id (UUID), tenant_id, command_type (ONBOARD | SUSPEND | RESUME | OFFBOARD), status, correlation_id, issued_at, completed_at, failure_reason, retry_count, max_retries
- Execution feedback record: command_id, execution_status, received_at, payload

**Technical Approach**:
- Introduce a `TenantLifecycleCommand` domain entity in `datasource-control-plane`
- Command dispatch can be via an internal queue, outbox pattern, or direct async call — the key invariant is that the command is persisted before dispatch
- Feedback handler: an inbound event/callback handler that reads the command record and advances actual_state on the tenant runtime record
- Idempotency: commands with the same correlation_id for the same tenant_id are deduplicated at write time

**User Workflows**:
1. Upstream system calls the onboard API with tenant configuration
2. Control plane writes command record (status=PENDING), then dispatches to executor
3. Executor acknowledges receipt (status → IN_PROGRESS) and eventually returns a result
4. Feedback handler receives result, updates command status, and advances tenant actual_state
5. On failure, retry logic re-dispatches up to max_retries before marking FAILED
6. Engineers query command status to understand what happened for a given tenant

**API/Interface Specifications**:
- `POST /tenants/{tenantId}/commands` → body: {command_type, correlation_id, config} → returns command_id, status
- `GET /tenants/{tenantId}/commands/{commandId}` → returns command status and history
- Internal feedback callback: receives execution result keyed by command_id

**Scope - INCLUDED**: Command domain model and persistence, command dispatch (async), feedback/callback ingestion, idempotency enforcement, retry logic, command status API
**Scope - EXCLUDED**: Tenant state machine definition (covered by state machine task), actual executor implementation (covered by dynamic-datasource task), DRAINING-specific workflow (covered by offboarding task)

**Success Criteria**:
- All four lifecycle action types (onboard, suspend, resume, offboard) can be triggered via the command API
- Commands are persisted before dispatch — no in-memory-only commands
- Duplicate commands with the same correlation_id are rejected or deduplicated
- Execution feedback correctly advances tenant actual_state
- Failed commands are retried up to the configured limit and then marked FAILED
- Command status is queryable at any point

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | Tenant lifecycle state machine can be managed as durable runtime state |
| testStrategy | Issue all four command types and verify each is persisted before any executor interaction occurs. Replay the same command with an identical correlation_id and confirm deduplication. Simulate executor failure and verify retry behavior up to the configured limit, followed by FAILED terminal status. Confirm that successful feedback correctly advances the tenant's actual_state. Query command history API and verify all state transitions are recorded with timestamps. |

