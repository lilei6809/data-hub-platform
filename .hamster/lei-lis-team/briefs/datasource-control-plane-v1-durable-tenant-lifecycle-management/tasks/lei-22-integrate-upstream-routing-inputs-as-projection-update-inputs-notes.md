---
id: "03fe2410-c641-4ee7-a101-fc32cb511dab"
entity_type: "task"
entity_id: "102d7d9b-23d9-4dbf-928f-d65f79e73d38"
title: "Integrate upstream routing inputs as projection update inputs - Notes"
status: "todo"
priority: "high"
display_id: "LEI-22"
parent_task_id: "dfc8152d-2520-4a4d-837d-da05eb52198a"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:44:09.225895+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement the routing input ingestion path that merges upstream datasource/routing metadata into the local `TenantProjection`.

## Implementation Approach

1. Define `RoutingInputNotification` contract: `tenantId`, datasource coordinates / routing metadata, `sequenceNumber`.
2. Implement `RoutingProjectionUpdater` listener.
3. Load current projection from store (or build skeleton if absent).
4. Merge routing fields, preserving existing lifecycle fields unchanged.
5. Derive new `inputVersion` from `sequenceNumber` and call `TenantProjectionStore.upsert(mergedProjection)`.
6. Ensure idempotency via store version guard.

## Acceptance Criteria

- `RoutingInputNotification` contract defined, decoupled from datasource registration internals.
- Routing merge is non-destructive — lifecycle fields are preserved.
- Re-delivery is idempotent; stale notifications are silently dropped.
- Routing update on absent projection creates a documented partial projection.
- Unit tests cover merge, idempotent re-delivery, stale drop, and partial projection creation.

## Technical Constraints

- Non-destructive merge: routing fields must not overwrite lifecycle state fields.
- Depend only on published notification contract.
- Idempotency required — replay-safe via store version guard.## Details

**Scope**: RoutingInputNotification contract definition, RoutingProjectionUpdater listener/observer, merge logic that updates routing fields while preserving lifecycle fields, upsert call with updated inputVersion.

**Out of Scope**: Lifecycle ingestion (previous subtask), TenantProjectionStore implementation (earlier subtask), TenantProjection model (first subtask), query API (next subtask), state machine ownership (sibling task), dynamic-datasource execution semantics (sibling task).

**Implementation**: 1. Define a `RoutingInputNotification` contract: tenantId, datasource coordinates / routing metadata fields, sequenceNumber. 2. Implement `RoutingProjectionUpdater` that receives this notification. 3. Load current projection from store (or build a minimal skeleton if absent). 4. Merge routing fields into projection, preserving lifecycle fields unchanged. 5. Increment or replace `inputVersion` using the notification's sequenceNumber. 6. Call `TenantProjectionStore.upsert(mergedProjection)`. 7. Ensure idempotency: same sequenceNumber is a no-op via store version guard. 8. Test: routing update on existing projection preserves lifecycle state; routing update on absent projection creates a partial projection (documented behaviour); re-delivery is idempotent; stale delivery is dropped.

**Constraints**: Merge must be non-destructive: routing field updates must not overwrite lifecycle state fields., Must depend only on a published routing notification contract, not on internal datasource registration implementation., Idempotency: same sequenceNumber re-delivered must not corrupt the stored projection.

## Acceptance Criteria

- [ ] RoutingInputNotification contract is defined, decoupling this component from the internal datasource registration path.
- [ ] RoutingProjectionUpdater correctly merges routing fields into the existing projection without overwriting lifecycle state fields.
- [ ] Re-delivery of the same routing notification leaves the projection unchanged.
- [ ] Stale routing notifications (lower sequenceNumber than stored) are silently dropped.
- [ ] Routing update applied to a tenant with no existing projection creates a projection with routing fields populated and lifecycle fields at their default/absent state (documented behaviour).
- [ ] Unit tests cover: merge with existing lifecycle projection, idempotent re-delivery, stale drop, and partial projection creation.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

