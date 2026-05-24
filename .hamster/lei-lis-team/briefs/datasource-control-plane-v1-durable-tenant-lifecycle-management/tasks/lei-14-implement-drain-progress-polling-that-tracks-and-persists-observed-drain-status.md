---
id: "3e664f81-6081-4baa-83f4-b762c38cd989"
entity_type: "task"
entity_id: "20ed6a29-eb18-44b3-b14c-83e022b6fb9b"
title: "Implement drain progress polling that tracks and persists observed drain status during DRAINING - Notes"
status: "todo"
priority: "high"
display_id: "LEI-14"
parent_task_id: "6124e75f-7caa-4062-bcda-68d3811e9d47"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:43:25.012785+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Periodically poll dynamic-datasource for drain status while a tenant is in DRAINING, persisting observed progress to durable state.

## Implementation Approach

1. Implement a recurring scheduler that queries all tenants currently in DRAINING state.
2. For each tenant, invoke the `status` command on dynamic-datasource to retrieve current drain status.
3. Persist `observed_drain_status` (e.g. `DRAINING_IN_PROGRESS` / `DRAIN_COMPLETE`) and `last_drain_checked_at` to the tenant's durable runtime record.
4. If `DRAIN_COMPLETE` is observed, emit or persist a drain-complete signal that the transition handler (separate subtask) can consume.
5. On transient error from dynamic-datasource: log, retain last known status, and do **not** mutate lifecycle state.
6. Polling ceases automatically when the tenant is no longer in DRAINING state.
7. Expose `observed_drain_status` and `last_drain_checked_at` as queryable fields on the tenant record.

## Acceptance Criteria

- For every tenant in DRAINING state, a status poll is issued at approximately the configured interval.
- The most recently observed drain status and a `last_drain_checked_at` timestamp are persisted after each poll.
- When `DRAIN_COMPLETE` is observed, a drain-complete signal is emitted/persisted for the transition handler.
- A transient error from dynamic-datasource leaves the tenant's lifecycle state unchanged.
- Polling automatically stops once the tenant leaves DRAINING.
- Poll interval is read from a configurable external property with a documented default.

## Technical Constraints

- Poll state must be persisted durably — not held in memory.
- A failed status query must not trigger any lifecycle state change.
- Polling must not run for tenants outside the DRAINING state.
- Poll interval must be configurable externally.## Details

**Scope**: Recurring drain status polling for tenants in DRAINING state, persisting observed drain status and last-checked timestamp, signalling drain-complete condition, configurable poll interval, resilience to transient status query failures, automatic cessation when tenant leaves DRAINING.

**Out of Scope**: Drain initiation (previous subtask), timeout enforcement and deadline evaluation (separate subtask), the actual DRAINING → CLOSED state transition decision (separate subtask), the status command implementation on dynamic-datasource (sibling task a1318d26), the state machine transition logic itself (sibling task e8774817).

**Implementation**: 1. Implement a recurring scheduler (e.g. a scheduled task or persistent job) that queries all tenants currently in DRAINING state. 2. For each such tenant, invoke the `status` command on dynamic-datasource to retrieve current drain status. 3. Persist the result as `observed_drain_status` (e.g. DRAINING_IN_PROGRESS / DRAIN_COMPLETE) and `last_drain_checked_at` on the tenant's durable runtime record. 4. If `DRAIN_COMPLETE` is observed, emit or persist a drain-complete signal/event that the transition handler (next subtask) can consume. 5. On transient error from dynamic-datasource, log the error and retain the last known status — do not mutate lifecycle state. 6. Polling ceases automatically when the tenant is no longer in DRAINING state. 7. Expose `observed_drain_status` and `last_drain_checked_at` as queryable fields on the tenant record.

**Constraints**: Poll state (observed_drain_status, last_drain_checked_at) must be persisted durably — not in memory., A failed status query must not trigger any lifecycle state change., Polling must not run for tenants outside the DRAINING state., Poll interval must be configurable externally (e.g. tenant.offboarding.drain-poll-interval-seconds).

## Acceptance Criteria

- [ ] For every tenant in DRAINING state, a status poll is issued to dynamic-datasource at approximately the configured interval.
- [ ] The most recently observed drain status and a last_drain_checked_at timestamp are persisted to durable storage after each poll.
- [ ] When a DRAIN_COMPLETE status is observed, a drain-complete signal is emitted or persisted so the transition handler can act on it.
- [ ] A transient error from dynamic-datasource during a poll leaves the tenant's lifecycle state unchanged and retains the last successfully observed status.
- [ ] Polling automatically stops (or is skipped) once the tenant has left the DRAINING state.
- [ ] The poll interval is read from an externally configurable property with a documented default.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

