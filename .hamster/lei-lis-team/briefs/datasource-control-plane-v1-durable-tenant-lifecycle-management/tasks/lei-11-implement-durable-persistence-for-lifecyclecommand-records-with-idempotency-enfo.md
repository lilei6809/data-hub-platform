---
id: "3efe697d-b7a1-4324-807a-2ff58b620dc6"
entity_type: "task"
entity_id: "98572d06-8fcb-4299-b952-64817274f051"
title: "Implement durable persistence for LifecycleCommand records with idempotency enforcement - Notes"
status: "todo"
priority: "high"
display_id: "LEI-11"
parent_task_id: "143db2d9-61cc-4068-b884-050827fe5b4b"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:43:18.369189+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement durable persistence for `LifecycleCommand` records, including idempotency enforcement via a unique constraint on tenant + idempotency key.

## Implementation Approach

1. Define the `LifecycleCommandRepository` interface with the following operations:

- `save(LifecycleCommand)` → persisted command (insert or upsert)
- `findById(UUID commandId)` → Optional<LifecycleCommand>
- `findByTenantId(String tenantId)` → List<LifecycleCommand>
- `findByStatus(LifecycleCommandStatus status)` → List<LifecycleCommand>
- `findByTenantIdAndStatus(String tenantId, LifecycleCommandStatus status)` → List<LifecycleCommand>
- `updateStatus(UUID commandId, LifecycleCommandStatus newStatus, String errorDetail, Instant completedAt)` → updated command

1. Design the persistence schema (table or document) mapping all `LifecycleCommand` fields, with a UNIQUE constraint on `(tenant_id, idempotency_key)`.
2. Implement idempotency logic in `save`: on duplicate key, return the existing record — do not throw and do not insert.
3. Implement the concrete repository backed by the chosen persistence technology (e.g., JPA/JDBC or equivalent).
4. Write the entity-to-domain mapper so the persistence layer does not leak infrastructure types into the domain.

## Acceptance Criteria

- `LifecycleCommandRepository` interface and implementation exist with all required query and mutation methods.
- Schema has a unique constraint on `(tenant_id, idempotency_key)`; saving a duplicate returns the existing record.
- Commands in `PENDING` or `IN_PROGRESS` status can be queried by status after a process restart.

## Technical Constraints

- All write operations must be transactional to prevent partial updates.
- The persistence entity must not be the same object as the domain model — use a mapping layer.
- Schema migrations must be expressed as versioned migration scripts.## Details

**Scope**: LifecycleCommand repository interface and implementation. Schema/entity definition for the commands table. Idempotency check on insert (tenant ID + idempotency key uniqueness constraint). Query methods: findById, findByTenantId, findByStatus, findByTenantIdAndStatus. Atomic status update operation.

**Out of Scope**: Command dispatch, scheduling, or retry logic (separate subtask). Callback or execution result ingestion (separate subtask). State machine transitions are owned by the sibling task on durable runtime state.

## Acceptance Criteria

- [ ] A LifecycleCommandRepository interface exists with methods: save(command), findById(commandId), findByTenantId(tenantId), findByStatus(status), and updateStatus(commandId, newStatus, errorDetail, completedAt) — all operations are transactional and durable.
- [ ] The underlying schema enforces a unique constraint on (tenantId, idempotencyKey) so duplicate command submissions with the same key return the existing record without inserting a new row.
- [ ] After a process restart, all commands in PENDING or IN_PROGRESS status can be loaded via findByStatus and re-queued for processing without manual intervention.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

