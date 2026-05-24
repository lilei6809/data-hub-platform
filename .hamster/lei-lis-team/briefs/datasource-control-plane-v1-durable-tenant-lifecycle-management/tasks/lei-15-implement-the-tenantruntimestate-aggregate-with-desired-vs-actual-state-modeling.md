---
id: "107c8f92-fd7d-4c50-bcad-fdc09ce1116b"
entity_type: "task"
entity_id: "96c5c111-ee58-4177-acbe-197c728b0f48"
title: "Implement the TenantRuntimeState aggregate with desired-vs-actual state modeling - Notes"
status: "todo"
priority: "high"
display_id: "LEI-15"
parent_task_id: "e8774817-16ba-4c59-934c-14803d53c035"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:43:31.925205+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement the TenantRuntimeState aggregate that holds desired-vs-actual lifecycle state with optimistic concurrency and domain event emission.

## Implementation Approach

1. Define the aggregate with fields: `tenantId`, `desiredState`, `actualState`, `lastTransitionedAt`, `transitionReason`, and a `version`/etag for optimistic concurrency.
2. Implement a factory/constructor initialising a new tenant in `PROVISIONING` for both desired and actual state.
3. Implement `applyDesiredTransition(targetState, reason)` — delegates to the transition guard, throws `IllegalTransitionException` if invalid, updates `desiredState` and timestamp, emits `TenantStateChanged`.
4. Implement `recordActualState(observedState, reason)` for receiving execution callbacks, with its own guard call.
5. Ensure the aggregate is immutable between operations (private setters / copy-on-write).
6. Unit-test factory creation, valid/invalid desired transitions, and actual-state recording.

## Acceptance Criteria

- Aggregate holds `desiredState`, `actualState`, `tenantId`, `lastTransitionedAt`, `transitionReason`, and a version/etag field.
- `applyDesiredTransition` rejects illegal transitions with a typed exception; valid transitions update `desiredState` and timestamp.
- `recordActualState` updates `actualState` independently, allowing desired-vs-actual drift to be represented.
- A `TenantStateChanged` domain event is emitted and accessible from the aggregate on every successful mutation.
- Factory creates a tenant with `desiredState=PROVISIONING`, `actualState=PROVISIONING`, and initial version=0.
- Unit tests confirm illegal transitions throw, valid transitions succeed, and events are collected.

## Technical Constraints

- Must delegate all state-change validation to the `TenantLifecycleState` transition guard — no duplicated transition logic.
- Optimistic concurrency: version field increments on every mutation.
- Domain events accessible via a collection on the aggregate — no event bus dependency in the aggregate class.
- No infrastructure dependencies in the aggregate class.## Details

**Scope**: TenantRuntimeState aggregate with tenantId, desiredState (TenantLifecycleState), actualState (TenantLifecycleState), lastTransitionedAt timestamp, transitionReason string, and version/etag for optimistic concurrency; factory method for initial PROVISIONING creation; methods to apply a desired-state change (validated by transition guard) and to record an actual-state observation (e.g. from an execution callback); domain events emitted on state change (at minimum TenantStateChanged).

**Out of Scope**: Persistence/repository implementation (next subtask), command definitions and orchestration (sibling parent task), any dynamic-datasource execution calls (sibling parent task), and DRAINING-specific workflow tracking (sibling parent task).

**Implementation**: 1. Define the TenantRuntimeState aggregate with fields: tenantId, desiredState, actualState, lastTransitionedAt, transitionReason, and an optimistic-concurrency version field. 2. Implement a factory/constructor that initialises a new tenant in PROVISIONING for both desired and actual state. 3. Implement applyDesiredTransition(targetState, reason) — calls the transition guard, throws IllegalTransitionException if invalid, updates desiredState and timestamp if valid, and emits TenantStateChanged. 4. Implement recordActualState(observedState, reason) for receiving execution feedback, with its own guard call. 5. Ensure the aggregate is immutable between operations (copy-on-write or private setters). 6. Unit-test factory creation, valid desired transitions, illegal desired transitions, and actual-state recording.

**Constraints**: Must delegate all state-change validation to the TenantLifecycleState transition guard — no duplicated transition logic., Optimistic concurrency: include a version/etag field that increments on every state mutation to support safe concurrent updates in the persistence layer., Domain events must be collectible from the aggregate (e.g. via a domainEvents() list) without requiring an event bus dependency in the aggregate itself., No infrastructure dependencies in the aggregate class.

## Acceptance Criteria

- [ ] TenantRuntimeState aggregate holds both desiredState and actualState as TenantLifecycleState values, plus tenantId, lastTransitionedAt, transitionReason, and a version/etag field.
- [ ] applyDesiredTransition rejects illegal transitions with a typed exception and accepts valid ones, updating desiredState and lastTransitionedAt.
- [ ] recordActualState updates actualState independently of desiredState, allowing drift to be represented.
- [ ] A TenantStateChanged domain event is emitted (and accessible from the aggregate) on every successful state mutation.
- [ ] Factory method creates a tenant with desiredState=PROVISIONING, actualState=PROVISIONING, and version=0 (or equivalent initial value).
- [ ] Unit tests confirm that illegal transitions throw, valid transitions succeed, and events are collected correctly.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

