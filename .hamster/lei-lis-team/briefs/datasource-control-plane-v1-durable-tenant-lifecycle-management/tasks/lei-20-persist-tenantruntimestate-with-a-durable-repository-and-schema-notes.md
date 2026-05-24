---
id: "08eba6c9-fa84-48ef-b2ad-6cc349d79c4a"
entity_type: "task"
entity_id: "ffd0d2db-96ef-4c03-b797-4a7a6626f6e5"
title: "Persist TenantRuntimeState with a durable repository and schema - Notes"
status: "todo"
priority: "high"
display_id: "LEI-20"
parent_task_id: "e8774817-16ba-4c59-934c-14803d53c035"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:43:58.427244+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement the database schema, migration script, and repository (interface + infrastructure) to durably persist TenantRuntimeState across process restarts.

## Implementation Approach

1. Define `TenantRuntimeStateRepository` interface in the domain layer: `findById`, `save` (with optimistic-lock), `findAllByDesiredState`, `findAllByActualState`.
2. Write the migration script creating `tenant_runtime_state` table with columns: `tenant_id` (PK), `desired_state`, `actual_state`, `last_transitioned_at`, `transition_reason`, `version`.
3. Implement the infrastructure repository using the project's ORM or JDBC abstraction, applying a version-check `WHERE version = :expected` on update.
4. Throw `OptimisticLockException` when the UPDATE affects zero rows.
5. Write integration tests against an in-memory or Testcontainers database covering insert, update, conflict detection, and state-based queries.

## Acceptance Criteria

- Migration script creates `tenant_runtime_state` table with all required columns including `version`.
- `TenantRuntimeStateRepository` interface is defined in the domain layer with no infrastructure imports.
- Infrastructure implementation persists and rehydrates `TenantRuntimeState` aggregates faithfully (both `desiredState` and `actualState`).
- `save()` throws `OptimisticLockException` when the database version doesn't match the aggregate's version.
- `findAllByDesiredState` and `findAllByActualState` return correct result sets for reconciliation queries.
- Integration tests confirm insert, update, optimistic-lock conflict, and state-based query behaviour.

## Technical Constraints

- Optimistic concurrency enforced via `WHERE version = :expectedVersion` in UPDATE — zero rows affected must raise `OptimisticLockException`.
- Migration must be idempotent and managed through the project's standard migration tooling.
- Domain repository interface must not import any infrastructure or ORM types.
- State column values stored as enum constant name strings.## Details

**Scope**: Database table schema (columns: tenant_id PK, desired_state, actual_state, last_transitioned_at, transition_reason, version); migration script; TenantRuntimeStateRepository domain interface (findById, save, findByDesiredState, findByActualState); infrastructure repository implementation with optimistic-locking on save.

**Out of Scope**: In-memory or test-double repository implementations beyond what is needed for unit tests; query projections for upstream tenant data (sibling parent task — Tenant Projection Management); command/event tables (sibling parent task); DRAINING-specific progress columns (sibling parent task).

**Implementation**: 1. Define TenantRuntimeStateRepository interface in the domain layer with methods: findById(tenantId), save(aggregate) with optimistic-lock enforcement (throws OptimisticLockException on version conflict), findAllByDesiredState(state), findAllByActualState(state). 2. Write the migration script creating the tenant_runtime_state table with appropriate column types and the version column. 3. Implement the infrastructure repository using the project's ORM or JDBC abstraction, mapping aggregate fields to columns and applying a version-check WHERE clause on update. 4. Write integration tests against an in-memory or test-container database confirming: successful insert, successful update, optimistic-lock conflict detection, and state-based queries.

**Constraints**: Optimistic concurrency must be enforced in the UPDATE SQL (WHERE version = :expectedVersion) — an update that finds zero rows must raise OptimisticLockException., Migration must be idempotent and managed through the project's standard migration tooling., The domain-layer repository interface must not import any infrastructure or ORM types., State column values must be stored as the string name of the enum constant for readability and schema portability.

## Acceptance Criteria

- [ ] A migration script creates the tenant_runtime_state table with columns for all TenantRuntimeState fields including the version/etag column.
- [ ] TenantRuntimeStateRepository interface defines findById, save, findAllByDesiredState, and findAllByActualState with no infrastructure imports.
- [ ] The infrastructure implementation persists and rehydrates TenantRuntimeState aggregates faithfully, including both desiredState and actualState.
- [ ] save() throws an OptimisticLockException when the version in the database does not match the aggregate's version, preventing lost updates.
- [ ] findAllByDesiredState and findAllByActualState return correct result sets, enabling reconciliation queries.
- [ ] Integration tests confirm insert, update, optimistic-lock conflict, and state-based query behaviour against a real database schema.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

