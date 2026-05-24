---
id: "58ad541d-7b2b-4c64-9272-bfe5e93098dc"
entity_type: "task"
entity_id: "f320a164-7ee5-4cb9-8e5e-a6e7d624365b"
title: "Implement DRAINING → CLOSED transition handler that closes on drain completion or timeout expiry - Notes"
status: "todo"
priority: "high"
display_id: "LEI-23"
parent_task_id: "6124e75f-7caa-4062-bcda-68d3811e9d47"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:44:12.074773+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Dispatch the `close` command and transition a tenant to CLOSED when either drain completes or the drain deadline is exceeded.

## Implementation Approach

1. Create a transition handler triggered by either a drain-complete signal or a timeout event for a DRAINING tenant.
2. Check idempotency: if `closed_at` is already set or a `close` command was already dispatched, return without action.
3. Determine close reason: drain-complete signal → `DRAIN_COMPLETED`; timeout event → `DRAIN_TIMEOUT`.
4. Dispatch the `close` command to dynamic-datasource via the execution command surface (sibling task a1318d26).
5. Persist `closed_at`, `close_reason`, and whether drain completed before timeout.
6. Request the state machine to transition the tenant to CLOSED (sibling task e8774817 owns the transition).
7. Write an audit log entry with close reason, tenant identifier, and timestamps.

## Acceptance Criteria

- A drain-complete signal triggers `close` dispatch and transition to CLOSED with `close_reason = DRAIN_COMPLETED`.
- A drain timeout event triggers `close` dispatch and transition to CLOSED with `close_reason = DRAIN_TIMEOUT`.
- Concurrent or sequential arrival of both signals results in exactly one `close` command and one CLOSED transition.
- `closed_at` and `close_reason` are persisted to durable storage on every successful close.
- An audit log entry is written capturing tenant identifier, `close_reason`, `closed_at`, and whether drain completed before deadline.
- A second invocation for an already-CLOSED tenant produces no commands, state changes, or audit entries.

## Technical Constraints

- Handler must be idempotent: duplicate invocations must not issue duplicate `close` commands.
- Close reason must be persisted durably and included in the audit record.
- The `close` command must be dispatched before the CLOSED transition is requested.
- Must handle concurrent arrival of both drain-complete and timeout signals safely.## Details

**Scope**: Consuming drain-complete signal and timeout event, dispatching the close command to dynamic-datasource, recording close reason (DRAIN_COMPLETED vs DRAIN_TIMEOUT) and close timestamp, requesting CLOSED transition via state machine, idempotency guard, audit record for close.

**Out of Scope**: Drain progress polling and drain-complete signal production (previous subtask), timeout event production (previous subtask), state machine transition implementation (sibling task e8774817), the close command implementation on dynamic-datasource (sibling task a1318d26), any post-CLOSED cleanup beyond the datasource close command.

**Implementation**: 1. Create a transition handler that can be triggered by either a drain-complete signal or a timeout event for a tenant in DRAINING state. 2. Check idempotency: if `closed_at` is already populated or a `close` command has been dispatched, return without action. 3. Determine close reason: if triggered by drain-complete signal → `DRAIN_COMPLETED`; if triggered by timeout event → `DRAIN_TIMEOUT`. 4. Dispatch the `close` command to dynamic-datasource via the execution command surface (sibling a1318d26). 5. Persist `closed_at`, `close_reason`, and whether drain completed before timeout to the tenant's durable runtime record. 6. Request the state machine to transition the tenant to CLOSED (sibling e8774817 owns the transition, this task triggers it). 7. Write an audit log entry with close reason, tenant identifier, and timestamps.

**Constraints**: Handler must be idempotent: a second invocation for an already-closed tenant must not issue a duplicate close command., Close reason (DRAIN_COMPLETED vs DRAIN_TIMEOUT) must be persisted durably and included in the audit record., The close command must be dispatched before the CLOSED transition is requested, to prevent the state machine from advancing ahead of the execution signal., Must handle concurrent arrival of both drain-complete and timeout signals safely (only one close should be issued).

## Acceptance Criteria

- [ ] When a drain-complete signal is received for a DRAINING tenant, the close command is dispatched to dynamic-datasource and the tenant transitions to CLOSED with close_reason = DRAIN_COMPLETED.
- [ ] When a drain timeout event is received for a DRAINING tenant, the close command is dispatched to dynamic-datasource and the tenant transitions to CLOSED with close_reason = DRAIN_TIMEOUT.
- [ ] If both signals arrive concurrently or in sequence, only one close command is issued and one CLOSED transition is requested.
- [ ] A closed_at timestamp and close_reason are persisted to durable storage on every successful close.
- [ ] An audit log entry is written for each close, capturing tenant identifier, close_reason, closed_at, and whether drain completed before the deadline.
- [ ] Invoking the handler a second time for a tenant already in CLOSED state produces no additional commands, state changes, or audit entries.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

