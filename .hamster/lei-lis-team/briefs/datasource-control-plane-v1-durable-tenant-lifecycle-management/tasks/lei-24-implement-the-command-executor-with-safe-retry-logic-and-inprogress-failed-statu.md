---
id: "9ed1e111-093c-4968-b677-903e5588cd09"
entity_type: "task"
entity_id: "ec41acef-c441-485f-9c13-96c23ad59eaf"
title: "Implement the command executor with safe retry logic and IN_PROGRESS/FAILED status transitions - Notes"
status: "todo"
priority: "high"
display_id: "LEI-24"
parent_task_id: "143db2d9-61cc-4068-b884-050827fe5b4b"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:44:15.397305+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement the asynchronous `LifecycleCommandExecutor` that processes dispatched commands with IN_PROGRESS/SUCCEEDED/FAILED status transitions and a configurable safe retry policy.

## Implementation Approach

1. Create `LifecycleCommandExecutor` as an async consumer (e.g., a message listener, scheduled poller, or reactive subscriber — inject via `LifecycleCommandDispatcher` port).
2. On receiving a command ID:
  a. Load the command from the repository.
  b. If status is not PENDING (guard against duplicate delivery), log and skip.
  c. Atomically transition to IN_PROGRESS and persist.
3. Invoke `ExecutionCommandPort.execute(command)` — this port is the boundary to the dynamic-datasource layer (implemented in the sibling task).
4. On success: transition command to SUCCEEDED, set completedAt, persist.
5. On failure:
  a. Increment retryCount, persist errorDetail.
  b. If retryCount < maxRetries: re-queue the command with a backoff delay.
  c. If retryCount >= maxRetries: transition to FAILED, set completedAt, persist. Do not re-queue.
6. Make maxRetries and backoff configurable via application configuration.
7. Ensure the status check before execution is atomic (optimistic lock or conditional update) to prevent concurrent double-execution.

## Acceptance Criteria

- PENDING → IN_PROGRESS → SUCCEEDED/FAILED transitions are persisted durably.
- Failed commands are retried up to the configured limit; after exhaustion they move to FAILED with error detail.
- Duplicate command delivery (command already IN_PROGRESS/SUCCEEDED/FAILED) is safely skipped.

## Technical Constraints

- The executor must not hold the command in memory across retries — re-load from the repository on each attempt.
- `ExecutionCommandPort` must be an injected interface; the executor has no direct dependency on dynamic-datasource classes.
- Max retry limit and backoff must be configurable properties, not hardcoded constants.## Details

**Scope**: LifecycleCommandExecutor: async consumer of dispatched commands. Status transition: PENDING → IN_PROGRESS on pickup. Invocation of ExecutionCommandPort (injected interface). Status transition: IN_PROGRESS → SUCCEEDED on success, IN_PROGRESS → FAILED on exhausted retries. Retry policy with configurable max attempts and backoff. Idempotency guard: skip if command is not in PENDING or IN_PROGRESS status.

**Out of Scope**: ExecutionCommandPort implementation (invoking dynamic-datasource) — that is the sibling task on explicit execution commands. State machine transitions of the tenant runtime state — sibling task. Callback ingestion from external execution results — separate subtask. Command submission — covered by the service subtask.

## Acceptance Criteria

- [ ] The executor transitions a PENDING command to IN_PROGRESS before invoking execution, and to SUCCEEDED or FAILED after the result is known; all status transitions are persisted durably so a process restart can observe the last known state.
- [ ] On transient execution failure, the executor increments retryCount, persists the error detail, and re-queues the command; after reaching the configured maximum retry limit the command is transitioned to FAILED and not re-queued further.
- [ ] If a command is delivered to the executor but is already in IN_PROGRESS, SUCCEEDED, or FAILED status (e.g., due to duplicate queue delivery), the executor skips processing and does not invoke the execution port a second time.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

