---
id: "33fd2c72-82d5-47fb-843a-9ce738938ed9"
entity_type: "task"
entity_id: "62cc4196-5af5-45e8-8441-89ad4f322113"
title: "Implement the TenantProjection persistence store with upsert and query operations - Notes"
status: "todo"
priority: "high"
display_id: "LEI-13"
parent_task_id: "dfc8152d-2520-4a4d-837d-da05eb52198a"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:43:23.200846+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement the durable `TenantProjectionStore` with idempotent upsert and query operations for the local tenant projection.

## Implementation Approach

1. Define `TenantProjectionStore` interface: `upsert(projection)`, `findById(tenantId)`, `findAll()`, `findByLifecycleState(state)`.
2. Implement persistence schema mapping all fields from `TenantProjection`.
3. Upsert: apply idempotency guard — skip write if stored `inputVersion` >= incoming `inputVersion`.
4. Ensure concurrent-safe upsert via optimistic locking or conditional UPDATE.
5. Implement `findById` returning Optional/absent for unknown tenants.
6. Implement `findByLifecycleState` for bulk reconciliation.
7. Unit test: first-time upsert, idempotent re-delivery, stale update rejection, concurrent upserts.

## Acceptance Criteria

- Interface defined with `upsert`, `findById`, `findAll`, `findByLifecycleState`.
- Upsert correctly applies last-write-wins: newer version overwrites, same version is no-op, older version is dropped.
- `findById` returns full projection or absent for unknown tenant.
- `findByLifecycleState` returns all matching projections.
- Concurrent upserts are safe (verified by test or documented locking strategy).
- Backed by durable storage consistent with the rest of the control plane.

## Technical Constraints

- Idempotent upsert: same `inputVersion` re-delivered must not error.
- Stale writes (lower `inputVersion`) silently dropped.
- Concurrent safety required.
- Durable storage — no in-memory-only implementation.## Details

**Scope**: TenantProjectionStore interface and its implementation: upsert (with inputVersion guard), findByTenantId, findAll / findByLifecycleState. Schema/table definition for persisting the projection fields. Concurrency safety (optimistic or pessimistic locking strategy).

**Out of Scope**: The TenantProjection model itself (previous subtask), event/command ingestion logic (next subtasks), query-facing API endpoints (later subtask), state machine ownership (sibling task).

**Implementation**: 1. Define a `TenantProjectionStore` interface with `upsert(projection)`, `findById(tenantId)`, `findAll()`, and `findByLifecycleState(state)` methods. 2. Implement the persistence schema mapping all fields from the `TenantProjection` model. 3. In the upsert implementation, apply an idempotency check: skip write if stored `inputVersion` >= incoming `inputVersion`. 4. Ensure `upsert` is safe under concurrent callers (use optimistic locking or conditional UPDATE). 5. Implement `findById` returning Optional/null if absent. 6. Implement `findByLifecycleState` for bulk reconciliation queries. 7. Write unit tests covering: first-time upsert, idempotent re-delivery (same version), stale update rejection (lower version), concurrent upserts.

**Constraints**: Upsert must be idempotent: identical inputVersion re-delivered must produce the same stored state without error., Stale writes (lower inputVersion) must be silently dropped, not error., Concurrent upserts for the same tenantId must not produce corrupt or partially written records., Store must be backed by durable storage — not in-memory.

## Acceptance Criteria

- [ ] TenantProjectionStore interface is defined with upsert, findById, findAll, and findByLifecycleState operations.
- [ ] Upsert correctly applies last-write-wins: newer inputVersion overwrites, same version is idempotent no-op, older version is silently dropped.
- [ ] findById returns the full TenantProjection for a known tenant and an empty/absent result for an unknown tenant.
- [ ] findByLifecycleState returns all projections matching the requested state.
- [ ] Concurrent upserts for the same tenantId are safe and do not corrupt stored data (verified by a concurrency test or documented locking strategy).
- [ ] All operations are backed by durable storage consistent with the rest of the control plane.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

