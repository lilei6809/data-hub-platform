---
id: "d55eec1d-235b-4cb6-a19f-1b297d302c94"
entity_type: "task"
entity_id: "d1cb3a1f-32ae-499c-a745-5b8d0051a104"
title: "Implement execution callback ingestion to update command status from asynchronous execution results - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-28"
parent_task_id: "143db2d9-61cc-4068-b884-050827fe5b4b"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:44:37.480252+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement `ExecutionCallbackHandler` to ingest asynchronous execution results and apply idempotent status updates to the corresponding `LifecycleCommand` record.

## Implementation Approach

1. Define an `ExecutionResult` value object with: `commandId` (UUID), `outcome` (SUCCEEDED | FAILED), `errorDetail` (nullable String), `progressMetadata` (nullable Map).
2. Create `ExecutionCallbackHandler` with method: `handle(ExecutionResult) → void` (or returns updated command).
3. Load the `LifecycleCommand` by `commandId` from the repository. If not found, throw `CommandNotFoundException`.
4. Check current status:

- If SUCCEEDED, FAILED, or CANCELLED: log a warning and return (no-op — idempotent).
- If IN_PROGRESS: continue.
- If PENDING: log a warning (unexpected — execution callback before dispatch acknowledged); still apply the update.

1. Map `outcome` to `LifecycleCommandStatus` (SUCCEEDED → SUCCEEDED, FAILED → FAILED).
2. Call `repository.updateStatus(commandId, mappedStatus, errorDetail, now())`.
3. Optionally publish an internal event (e.g., `CommandCompletedEvent`) for downstream consumers (state machine reconciliation, audit log).

## Acceptance Criteria

- `handle(ExecutionResult)` updates command to SUCCEEDED or FAILED and persists with completedAt.
- Terminal-status commands are not modified on duplicate callback — idempotent no-op.
- Unknown commandId throws a typed `CommandNotFoundException`.

## Technical Constraints

- The handler must not trigger re-execution or retry logic — it only records the result.
- All persistence updates must be atomic.
- The handler should be usable from both HTTP and messaging transports without modification.## Details

**Scope**: ExecutionCallbackHandler service/port: accepts an ExecutionResult (commandId, outcome, errorDetail, progressMetadata). Loads the target LifecycleCommand from the repository. Applies status update (SUCCEEDED or FAILED) if command is in IN_PROGRESS status. Idempotency: no-op if command is already terminal. Unknown command ID rejection. Persistence of updated command.

**Out of Scope**: HTTP or messaging transport for delivering callbacks (that is the API surface subtask or infrastructure concern). Retry re-queuing logic — covered by the executor subtask. DRAINING workflow state tracking — owned by the sibling offboarding task. State machine transitions — sibling task.

## Acceptance Criteria

- [ ] ExecutionCallbackHandler.handle(ExecutionResult) updates the command status to SUCCEEDED or FAILED with error detail and completedAt when the command is in IN_PROGRESS status, and persists the change durably.
- [ ] If the command referenced by the callback is already in a terminal status (SUCCEEDED, FAILED, CANCELLED), the handler treats the callback as a no-op and does not modify the record.
- [ ] If the commandId in the callback does not correspond to any known command, the handler throws or returns a typed NotFoundException rather than silently discarding the callback.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

