---
id: "f80e1397-01db-4e05-82ba-317aad84e59d"
entity_type: "task"
entity_id: "56ec48a8-9af6-4bc3-948a-7559ac93cbf8"
title: "Implement drain timeout enforcement that fires when drain_deadline is exceeded during DRAINING - Notes"
status: "todo"
priority: "high"
display_id: "LEI-17"
parent_task_id: "6124e75f-7caa-4062-bcda-68d3811e9d47"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:43:47.222306+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Evaluate the persisted drain deadline for DRAINING tenants and fire a durable timeout event when the deadline is exceeded.

## Implementation Approach

1. Implement a recurring scheduler that evaluates all tenants currently in DRAINING state.
2. For each tenant, compare `now()` against the persisted `drain_deadline`.
3. If `now() > drain_deadline` and no `drain_timed_out_at` is recorded: persist `drain_timed_out_at = now()`, write an audit log entry, and emit/persist a timeout event for the transition handler.
4. If `drain_timed_out_at` is already set, skip — idempotency guard.
5. Tenants not in DRAINING are skipped entirely.
6. On process restart, the scheduler reads all DRAINING tenants from durable storage and catches any deadlines that expired during downtime.

## Acceptance Criteria

- When `now()` exceeds a tenant's `drain_deadline` while in DRAINING, a `drain_timed_out_at` timestamp is persisted.
- An audit log entry is recorded when a drain timeout fires, including tenant identifier and the deadline exceeded.
- Re-evaluating a tenant with `drain_timed_out_at` already set does not produce a duplicate event or audit entry.
- After process restart, tenants whose deadline expired during downtime are detected and recorded on the next scheduler run.
- Tenants no longer in DRAINING are skipped silently.

## Technical Constraints

- Deadline evaluation relies solely on the durable `drain_deadline` field — no in-memory timer reconstruction.
- `drain_timed_out_at` must be persisted before the transition handler is notified.
- Mechanism must be idempotent.
- Must only evaluate tenants in DRAINING state.## Details

**Scope**: Durable deadline evaluation against persisted drain_deadline, firing a timeout event (persisting drain_timed_out_at and/or audit entry), idempotency guard against duplicate timeout events, recovery after process restart, scoping enforcement to DRAINING tenants only.

**Out of Scope**: Computing or storing the drain_deadline itself (drain initiation subtask), drain progress polling (previous subtask), the DRAINING → CLOSED transition decision and close command dispatch (next subtask), state machine transition implementation (sibling task e8774817).

**Implementation**: 1. Implement a recurring scheduler (separate from the poll scheduler, or a shared one) that evaluates all tenants in DRAINING state. 2. For each such tenant, compare now() against the persisted `drain_deadline`. 3. If now() > drain_deadline AND no `drain_timed_out_at` is already recorded, persist `drain_timed_out_at = now()`, add an audit log entry indicating timeout, and emit/persist a timeout event for the transition handler to consume. 4. If `drain_timed_out_at` is already set, skip — idempotency guard. 5. Tenants not in DRAINING state are skipped entirely. 6. On process restart, the scheduler re-reads all DRAINING tenants and their deadlines from durable storage, catching any tenants whose deadline expired during downtime.

**Constraints**: Deadline evaluation must rely solely on the durable drain_deadline field — no in-memory timer reconstruction needed., Timeout event / drain_timed_out_at must be persisted before the transition handler is notified., Mechanism must be idempotent: a second evaluation of an already-timed-out tenant must not produce duplicate events., Must only evaluate tenants currently in DRAINING state.

## Acceptance Criteria

- [ ] When now() exceeds a tenant's persisted drain_deadline while that tenant is in DRAINING, a drain_timed_out_at timestamp is persisted to durable storage.
- [ ] An audit log entry is recorded when a drain timeout fires, including the tenant identifier and the deadline that was exceeded.
- [ ] Re-evaluating a tenant whose drain_timed_out_at is already set does not produce a second timeout event or audit entry.
- [ ] After a process restart, tenants whose drain_deadline was exceeded during downtime have their timeout detected and recorded on the next scheduler execution without requiring any manual intervention.
- [ ] Tenants that are no longer in DRAINING state are skipped silently by the timeout evaluator.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

