---
id: "13a22c14-b4b5-45f7-8ad8-cd1386ff7125"
entity_type: "task"
entity_id: "71f2cbb3-5b20-4814-abf6-cc0fc8a7ea5b"
title: "Implement the reconciliation loop to detect and re-drive state drift after restart - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-25"
parent_task_id: "e8774817-16ba-4c59-934c-14803d53c035"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:44:25.359924+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement a scheduled reconciliation job that detects desired-vs-actual state drift in persisted TenantRuntimeState records and emits correction signals to re-drive tenants toward their intended state after restarts or failed callbacks.

## Implementation Approach

1. Implement `ReconciliationJob` (scheduler-triggered, configurable interval) querying `findAllByDesiredState` and comparing to `actualState` for drift.
2. For each drifted tenant, determine the correction action based on the (actual → desired) pair.
3. Emit the correction signal via a `ReconciliationCommandPort` interface — keep the reconciler decoupled from command bus implementation details.
4. Wrap each per-tenant operation in a try-catch to isolate failures — a single failing tenant must not abort the cycle.
5. Log each cycle summary: tenants scanned, drift count, signals emitted, errors encountered.
6. Unit-test drift detection, signal emission, failure isolation, and empty-drift no-op behaviour.

## Acceptance Criteria

- `ReconciliationJob` runs on a configurable schedule and queries for tenants with `desiredState ≠ actualState`.
- For each drifted tenant, the job emits the appropriate correction signal via a decoupled port interface.
- A failure processing one tenant does not halt reconciliation of remaining tenants in the same cycle.
- Each cycle produces a structured log entry recording: tenants scanned, drift count, signals emitted, and any errors.
- Reconciliation interval is configurable via application properties.
- Unit tests verify drift detection, correct signal emission per state pair, failure isolation, and empty-drift no-op behaviour.

## Technical Constraints

- Reconciler emits correction signals only — it must not execute lifecycle operations directly.
- Reconciliation interval must be configurable, not hardcoded.
- Single failing tenant must not abort the cycle; skip on `OptimisticLockException` and let the next cycle handle it.## Details

**Scope**: Scheduled reconciliation job that queries for desiredState ≠ actualState drift; per-tenant correction signal emission (publishing a re-drive command or event); optimistic-lock-safe processing to prevent duplicate actions from concurrent reconciler instances; structured logging of each reconciliation cycle (tenants found, actions taken, errors); configurable reconciliation interval.

**Out of Scope**: The actual command execution logic (sibling task — durable command orchestration); DRAINING drain-progress tracking and timeout enforcement (sibling task); upstream event ingestion (sibling task — Tenant Projection Management); any UI or API exposure of reconciler results.

**Implementation**: 1. Implement a ReconciliationJob (scheduler-triggered, configurable interval) that calls findAllByActualState for each non-terminal state and compares to desiredState. 2. For each drifted tenant, determine the correction action (e.g. if desired=ACTIVE and actual=PROVISIONING, emit an activate command). 3. Emit the correction signal via a port/interface (e.g. ReconciliationCommandPort) so the reconciler is decoupled from the command bus implementation. 4. Wrap each per-tenant operation in a try-catch so a single failing tenant does not abort the entire cycle. 5. Log the cycle summary: number of tenants scanned, number with drift, actions emitted, errors encountered. 6. Unit-test the drift-detection and signal-emission logic using a stubbed repository and command port.

**Constraints**: Reconciler must not execute lifecycle operations directly — it emits correction signals only, keeping it decoupled from execution details., Reconciliation interval must be configurable via application properties, not hardcoded., A single failing tenant must not abort the reconciliation of other tenants in the same cycle., Optimistic-lock on the aggregate must be respected: if a save fails with OptimisticLockException during re-drive acknowledgement, the reconciler must skip (not retry inline) and let the next cycle handle it.

## Acceptance Criteria

- [ ] ReconciliationJob runs on a configurable schedule and queries the repository for tenants with desiredState ≠ actualState.
- [ ] For each drifted tenant, the job emits the appropriate correction signal via a decoupled port/interface.
- [ ] A failure to process one tenant does not halt reconciliation of remaining tenants in the same cycle.
- [ ] Each cycle produces a structured log entry recording: tenants scanned, drift count, signals emitted, and any errors.
- [ ] The reconciliation interval is configurable via application properties.
- [ ] Unit tests verify drift detection, correct signal emission per state pair, single-tenant failure isolation, and empty-drift no-op behaviour.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

