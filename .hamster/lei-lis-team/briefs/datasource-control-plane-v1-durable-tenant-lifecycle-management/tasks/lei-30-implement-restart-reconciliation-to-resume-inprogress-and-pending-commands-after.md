---
id: "88495aaa-b929-482c-8308-9f5f42b2c80c"
entity_type: "task"
entity_id: "744013d5-2387-44d8-bada-21698cb70c70"
title: "Implement restart reconciliation to resume IN_PROGRESS and PENDING commands after process restart - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-30"
parent_task_id: "143db2d9-61cc-4068-b884-050827fe5b4b"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:45:21.043371+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement a startup reconciliation routine that detects and re-queues `PENDING` and `IN_PROGRESS` commands left over from before a process restart, ensuring no lifecycle work is silently lost.

## Implementation Approach

1. Create `CommandReconciliationService` with a method `reconcileOnStartup()`.
2. Wire it to run at application startup (e.g., via `ApplicationStartedEvent`, `@PostConstruct`, or an equivalent startup hook).
3. Query `repository.findByStatus(PENDING)` and `repository.findByStatus(IN_PROGRESS)` to retrieve all non-terminal commands.
4. For each command:

- If `retryCount >= maxRetries`: call `repository.updateStatus(commandId, FAILED, "reconciliation: retry limit exhausted", now())`. Log `FAILED`.
- Otherwise: dispatch via `LifecycleCommandDispatcher.dispatch(command)`. Log `RE_QUEUED` with commandId, tenantId, commandType, retryCount.

1. Emit a summary log at the end: total commands found, total re-queued, total failed.
2. Ensure the routine completes before the application begins accepting inbound requests, or document the ordering guarantee.

## Acceptance Criteria

- Startup reconciliation re-queues all PENDING/IN_PROGRESS commands within retry budget via the dispatcher.
- Commands exceeding maxRetries are marked FAILED with a clear error detail.
- A structured log entry per reconciled command (and a summary) is emitted.

## Technical Constraints

- Reconciliation must not re-dispatch commands that are already in terminal states (SUCCEEDED, FAILED, CANCELLED).
- The maxRetries threshold used here must be the same configurable value as in the executor — not a separate constant.
- The reconciliation routine must be idempotent: if invoked more than once, commands that were already re-queued in a previous run must not be dispatched a second time.## Details

**Scope**: CommandReconciliationService or StartupReconciler: queries for PENDING and IN_PROGRESS commands on startup. Dispatches recoverable commands via LifecycleCommandDispatcher. Transitions exhausted commands (retryCount >= maxRetries) to FAILED. Structured logging of reconciled commands.

**Out of Scope**: Executor retry logic itself — covered by the executor subtask. The persistence query methods — covered by the persistence subtask. Any periodic/scheduled reconciliation beyond startup — out of scope unless explicitly described in the brief.

## Acceptance Criteria

- [ ] On application startup, the reconciliation routine queries for all LifecycleCommand records in PENDING or IN_PROGRESS status and dispatches each recoverable command through the LifecycleCommandDispatcher so they resume execution without manual intervention.
- [ ] Commands found during reconciliation where retryCount is greater than or equal to the configured maxRetries are transitioned to FAILED status with an error detail indicating 'reconciliation: retry limit already exhausted' rather than being re-queued.
- [ ] A structured log entry is emitted for each reconciled command, including commandId, tenantId, commandType, and the action taken (re-queued or failed), so operators can verify recovery after a restart.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

