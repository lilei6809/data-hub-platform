---
id: "5bc1cc26-4baf-4b21-a683-40f09b9e7a53"
entity_type: "task"
entity_id: "d9618255-fb53-4cfc-9649-cd22d3b658f0"
title: "Define the tenant lifecycle state enum and valid transition graph - Notes"
status: "todo"
priority: "high"
display_id: "LEI-10"
parent_task_id: "e8774817-16ba-4c59-934c-14803d53c035"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-05-21T08:25:53.895198+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Define the canonical TenantLifecycleState enum and the explicit, validated transition graph that all lifecycle components reference.

## Implementation Approach

1. Enumerate all seven lifecycle states (PROVISIONING, ACTIVE, SUSPENDED, DRAINING, FAILED, DEGRADED, CLOSED) as a type-safe construct.
2. Declare valid transition pairs as a static, data-driven map: each source state lists its allowed target states.
3. Implement a guard/validator accepting `(currentState, proposedState)` and returning a typed Result (success or `IllegalTransitionError` with a descriptive message).
4. Write exhaustive unit tests covering every valid edge and a representative set of illegal edges.

## Acceptance Criteria

- All seven states are represented as a named, type-safe enum or equivalent construct.
- Every valid (from → to) transition pair is explicitly declared; all other pairs are implicitly or explicitly illegal.
- A transition-validation method returns a clear success or failure result (with reason) for any (current, proposed) state pair.
- The domain model has zero dependencies on persistence, HTTP, or any infrastructure framework.
- Unit tests cover all valid transitions and a representative set of illegal transitions.

## Technical Constraints

- Pure domain model — no framework, persistence, or I/O dependencies allowed.
- Transition rules must be encoded as data (a map/table), not as branching imperative logic.
- Immutable: the state enum and transition map must not be mutable at runtime.## Details

**Scope**: TenantLifecycleState enum with all seven states; a transition guard or allowed-transitions map encoding every valid (from → to) pair; a domain method or utility that validates a proposed transition and returns a typed result (allowed / illegal); unit-testable pure domain model with no infrastructure dependencies.

**Out of Scope**: Persistence of state, the state machine executor/aggregate, command definitions (sibling task), dynamic-datasource execution commands (sibling task), and the DRAINING workflow (sibling task).

**Implementation**: 1. Enumerate all seven lifecycle states as a type-safe construct. 2. Declare the valid transition pairs as a static map keyed by source state, with each entry listing allowed target states. 3. Implement a guard/validator that accepts (currentState, proposedState) and returns a typed Result (success or IllegalTransitionError with a descriptive message). 4. Write unit tests exhaustively covering each valid edge and a set of representative illegal edges.

**Constraints**: Pure domain model — no framework, persistence, or I/O dependencies allowed., Transition rules must be encoded as data (a map/table), not as branching imperative logic, so they can be inspected and extended without touching guard logic., Immutable: the state enum and transition map must not be mutable at runtime.

## Acceptance Criteria

- [ ] All seven states (PROVISIONING, ACTIVE, SUSPENDED, DRAINING, FAILED, DEGRADED, CLOSED) are represented as a named, type-safe enum or equivalent construct.
- [ ] Every valid (from → to) transition pair is explicitly declared; all other pairs are implicitly or explicitly illegal.
- [ ] A transition-validation method/function returns a clear success or failure result (with reason) when given a current state and a proposed next state.
- [ ] The domain model has zero dependencies on persistence, HTTP, or any infrastructure framework — it is pure domain logic.
- [ ] Unit tests cover all valid transitions and a representative set of illegal transitions, asserting correct allow/reject outcomes.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |

