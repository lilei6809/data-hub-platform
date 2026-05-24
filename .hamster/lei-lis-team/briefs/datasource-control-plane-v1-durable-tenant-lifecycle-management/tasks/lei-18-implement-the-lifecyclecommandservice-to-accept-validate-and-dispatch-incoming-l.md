---
id: "232c1ae6-1701-44d3-b687-86d41924d0bf"
entity_type: "task"
entity_id: "2fd58091-55ec-4da5-b865-1b1ddae4931a"
title: "Implement the LifecycleCommandService to accept, validate, and dispatch incoming lifecycle commands - Notes"
status: "todo"
priority: "high"
display_id: "LEI-18"
parent_task_id: "143db2d9-61cc-4068-b884-050827fe5b4b"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:43:47.413524+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement `LifecycleCommandService` as the control-plane entry point that validates, persists, idempotency-checks, and dispatches lifecycle commands asynchronously.

## Implementation Approach

1. Create `LifecycleCommandService` with a primary method: `submit(tenantId, commandType, idempotencyKey, payload) → LifecycleCommand`.
2. Validation step: assert tenantId is non-null/non-blank, commandType is a valid `LifecycleCommandType`, idempotencyKey is non-null/non-blank. Throw a descriptive `InvalidCommandException` on failure.
3. Idempotency check: call `repository.findByIdempotencyKey(tenantId, idempotencyKey)`. If found, return the existing command immediately without dispatching.
4. Create a new `LifecycleCommand` with a generated UUID, status = PENDING, retryCount = 0, and the provided fields. Persist it.
5. Dispatch the persisted command to the async execution component (inject a `LifecycleCommandDispatcher` port/interface to decouple from the concrete queue technology).
6. Return the persisted command.
7. Ensure the persist + dispatch sequence is safe: if dispatch fails after persist, the command remains PENDING and can be picked up by the reconciliation path.

## Acceptance Criteria

- `submit(...)` persists a PENDING command and dispatches it; duplicate idempotency keys return the existing command without re-dispatch.
- Validation rejects null/blank tenantId, commandType, or idempotencyKey with a typed exception.
- The persisted `LifecycleCommand` (with commandId and status) is returned to the caller.

## Technical Constraints

- The service must not directly call dynamic-datasource — it only dispatches to an async executor abstraction.
- Dispatch failures must not rollback the persisted PENDING command — durability of the record takes precedence.
- The dispatcher port must be an interface so different transport implementations can be injected.## Details

**Scope**: LifecycleCommandService application service. Input validation (non-null fields, valid command type, tenant ID format). Idempotency check via repository (return existing command if key already seen). PENDING command creation and persistence. Dispatch to asynchronous execution queue/bus. Return of persisted LifecycleCommand to caller.

**Out of Scope**: Actual execution logic (invoking dynamic-datasource) — that is covered by the sibling task on explicit execution commands. State machine transitions — owned by the sibling task on durable runtime state. Callback/result ingestion — separate subtask. HTTP API surface — separate subtask.

## Acceptance Criteria

- [ ] LifecycleCommandService.submit(tenantId, commandType, idempotencyKey, payload) persists a new LifecycleCommand in PENDING status and dispatches it to the async execution path; if a command with the same idempotency key already exists for that tenant, the existing command is returned and no second dispatch occurs.
- [ ] Validation rejects requests with null or blank tenantId, null commandType, or null/blank idempotencyKey by throwing a well-typed validation exception before any persistence occurs.
- [ ] The service returns the persisted LifecycleCommand record (including its commandId and current status) so callers can poll for progress using the command ID.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

