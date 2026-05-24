---
id: "8ccbfb93-9c41-463a-9a29-944e1b710877"
entity_type: "task"
entity_id: "d590587d-ab40-45c7-9c5e-45bfa5124923"
title: "Implement drain initiation handler that transitions a tenant into DRAINING and records deadline - Notes"
status: "todo"
priority: "high"
display_id: "LEI-9"
parent_task_id: "6124e75f-7caa-4062-bcda-68d3811e9d47"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:43:01.910549+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Persist the drain deadline and dispatch the `start-drain` command when a tenant enters the DRAINING state.

## Implementation Approach

1. Define a configurable property `tenant.offboarding.drain-timeout-seconds` with a documented default (e.g. 300 seconds).
2. Create a DRAINING-entry event handler / state-entry action in the control plane.
3. On first invocation: compute `drain_deadline = now() + timeout` and persist both `drain_started_at` and `drain_deadline` to the tenant's durable runtime record.
4. Dispatch the `start-drain` command via the execution command surface (sibling task a1318d26).
5. Guard idempotency: if `drain_started_at` is already populated, skip all writes and command dispatch without error.

## Acceptance Criteria

- When the DRAINING entry event is processed, a `drain_deadline` timestamp is persisted to durable storage computed as `event_time + configured drain_timeout_seconds`.
- A `drain_started_at` timestamp is also persisted alongside the deadline on first processing.
- Re-processing the same DRAINING entry event does not overwrite the existing deadline or issue a second `start-drain` command.
- The drain timeout value is read from an externally configurable property with a documented default.
- The `start-drain` command is dispatched exactly once per drain initiation.

## Technical Constraints

- Drain deadline must be persisted to durable storage — not held in memory — so it survives process restart.
- Timeout configuration must be externally overridable without code changes.
- Handler must be idempotent: safe to invoke multiple times for the same tenant DRAINING entry.## Details

**Scope**: Handling the DRAINING entry event: persisting drain deadline and drain start time, issuing start-drain command to dynamic-datasource, idempotency guard on duplicate entry events, configurable timeout property.

**Out of Scope**: State machine transition logic (sibling task e8774817), the start-drain execution implementation on dynamic-datasource (sibling task a1318d26), drain progress polling (next subtask), timeout enforcement scheduling (separate subtask), final CLOSED transition logic (separate subtask).

**Implementation**: 1. Define a configurable property `tenant.offboarding.drain-timeout-seconds` with a sensible default (e.g. 300s). 2. Create a DRAINING-entry event handler / state-entry action within the control plane. 3. On first entry: compute deadline = now() + timeout, persist both `drain_started_at` and `drain_deadline` to the tenant's durable runtime record. 4. Dispatch the `start-drain` command via the execution command surface (sibling a1318d26). 5. Guard idempotency: if `drain_started_at` is already populated for this tenant, skip all writes and command dispatch.

**Constraints**: Drain deadline must be persisted to durable storage — not held in memory — so it is available after process restart., Timeout configuration must be externally overridable without code changes., Handler must be idempotent: safe to invoke multiple times for the same tenant DRAINING entry.

## Acceptance Criteria

- [ ] When the DRAINING entry event is processed, a drain_deadline timestamp is persisted to durable storage computed as event_time + configured drain_timeout_seconds.
- [ ] A drain_started_at timestamp is also persisted alongside the deadline on first processing of the DRAINING entry event.
- [ ] Re-processing the same DRAINING entry event for a tenant already in DRAINING does not overwrite the existing deadline or issue a second start-drain command.
- [ ] The drain timeout value is read from an externally configurable property (e.g. tenant.offboarding.drain-timeout-seconds) with a documented default.
- [ ] The start-drain command is dispatched to dynamic-datasource exactly once per drain initiation, consistent with the command interface defined by sibling task a1318d26.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

