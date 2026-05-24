---
id: "db8811df-32e8-405f-b5bf-581c7dc9df73"
entity_type: "task"
entity_id: "ee9dec9b-671e-4347-92a0-3b5e55e5c7c5"
title: "Integrate lifecycle state change events as projection update inputs - Notes"
status: "todo"
priority: "high"
display_id: "LEI-19"
parent_task_id: "dfc8152d-2520-4a4d-837d-da05eb52198a"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:43:48.644164+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement the lifecycle event ingestion path that maps FSM state transitions to `TenantProjection` upserts.

## Implementation Approach

1. Define `TenantLifecycleNotification` contract: `tenantId`, `newState`, `enteredAt`, `sequenceNumber`, optional `failureReason`.
2. Implement `LifecycleProjectionUpdater` listener that receives this notification.
3. Map notification fields to `TenantProjection` lifecycle fields; derive `inputVersion` from `sequenceNumber`.
4. Call `TenantProjectionStore.upsert(projection)`.
5. Ensure idempotency — the store's version guard handles replay safely; listener must not throw on duplicate delivery.
6. Document and implement failure-handling strategy (log-and-retry, dead-letter, etc.).

## Acceptance Criteria

- `TenantLifecycleNotification` contract decouples this component from state machine internals.
- All FSM states are correctly mapped to projection fields and trigger upsert.
- Re-delivery of same notification leaves projection unchanged.
- Out-of-order/stale notifications are silently dropped.
- Projection update failure does not propagate back to or halt the state machine transition.

## Technical Constraints

- Depend only on published notification contract, not internal FSM implementation.
- Idempotency required — replay-safe.
- Failure isolation: projection update failures must not block state machine transitions.## Details

**Scope**: Lifecycle event/notification listener or observer: receives state transition notifications from the state machine, maps them to TenantProjection fields, and calls TenantProjectionStore.upsert. Idempotency via inputVersion derived from the event sequence. Definition of the lifecycle notification contract/interface this component depends on.

**Out of Scope**: State machine implementation (sibling task), routing input ingestion (next subtask), the projection store itself (previous subtask), query API (later subtask), command/execution feedback processing (sibling task).

**Implementation**: 1. Define a `TenantLifecycleNotification` contract (interface/record) that the state machine publishes — includes: tenantId, newState, enteredAt, sequenceNumber, optional failureReason. 2. Implement a `LifecycleProjectionUpdater` listener/observer that receives this notification. 3. Map notification fields to `TenantProjection` lifecycle fields; set `inputVersion` from the notification's sequenceNumber. 4. Call `TenantProjectionStore.upsert(projection)`. 5. Handle idempotency: the store's version guard makes replay safe, but the listener should not throw on duplicate delivery. 6. Test: first delivery updates projection; re-delivery of same event leaves projection unchanged; delivery out of order (older sequence) is silently dropped.

**Constraints**: Listener must not couple to the internal state machine implementation — depend only on the published notification contract., Idempotency: replayed notifications for the same transition must not corrupt the projection., Listener must not block or fail the state machine's own transition logic if the projection update fails — failure handling strategy must be defined (e.g., log and retry, dead-letter).

## Acceptance Criteria

- [ ] A TenantLifecycleNotification contract is defined that decouples this component from the state machine internals.
- [ ] LifecycleProjectionUpdater correctly maps all FSM states (PROVISIONING, ACTIVE, SUSPENDED, DRAINING, FAILED, DEGRADED, CLOSED) to projection fields and calls upsert.
- [ ] Re-delivery of the same notification leaves the stored projection unchanged (idempotent via inputVersion guard in the store).
- [ ] Out-of-order or stale notifications (lower sequenceNumber) are silently dropped without corrupting the current projection.
- [ ] A failure in the projection update does not propagate back to or halt the state machine transition (failure handling strategy is documented and implemented).

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

