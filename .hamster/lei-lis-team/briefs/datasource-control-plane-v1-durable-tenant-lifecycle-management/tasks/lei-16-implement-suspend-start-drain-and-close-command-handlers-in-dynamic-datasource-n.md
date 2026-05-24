---
id: "5384ef36-f3d9-4ec1-b590-f099aa482427"
entity_type: "task"
entity_id: "abc6e04f-7293-44de-80bb-2a905fef7b32"
title: "Implement suspend, start-drain, and close command handlers in dynamic-datasource - Notes"
status: "todo"
priority: "high"
display_id: "LEI-16"
parent_task_id: "a1318d26-212f-4e43-978d-abe515479cdc"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:43:46.462325+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement the `suspend`, `start-drain`, and `close` command handlers in `dynamic-datasource`, completing the full six-operation lifecycle execution surface and replacing the single ambiguous deregistration path.

## Implementation Approach

1. Add `suspend`, `start-drain`, and `close` logic to `DynamicDatasourceCommandService`.
2. `suspend`: verify tenant is `ACTIVE` (return `InvalidTransition` otherwise) → update registry state to `SUSPENDED` → stop routing without destroying pool.
3. `start-drain`: verify tenant is `ACTIVE` or `SUSPENDED` → update state to `DRAINING` → reject new connection acquisition.
4. `close`: look up tenant (return `NotFound` if absent) → teardown pool resources → remove from registry. Guard against double-close.
5. Ensure none of these operations synchronously wait for in-flight work — they are state signals only.
6. Unit-test each handler covering success and key invalid-transition paths.

## Acceptance Criteria

- `suspend` on an `ACTIVE` tenant transitions to `SUSPENDED`, stops routing, returns success.
- `suspend` on a non-`ACTIVE` tenant returns `InvalidTransition`.
- `start-drain` on an `ACTIVE` or `SUSPENDED` tenant transitions to `DRAINING`, prevents new connections, returns success.
- `close` on any non-`CLOSED` tenant tears down the pool, removes the registry entry, returns success.
- `close` on an already-removed tenantId returns `NotFound`.
- All three handlers are covered by unit tests verifying each result variant without a real JDBC connection.

## Technical Constraints

- `start-drain` must NOT synchronously wait for in-flight connections to finish — it is a state signal only.
- `close` must release all pool resources deterministically to avoid connection leaks.
- All handlers return typed results — no unchecked exceptions for known precondition failures.

## Relevant Files

### Files to Create

- `dynamic-datasource/src/test/…/DynamicDatasourceCommandServiceSuspendDrainCloseTest.java` - Unit tests for suspend, start-drain, close handlers## Details

**Scope**: Concrete implementation of suspend (pause routing, preserve pool), start-drain (mark draining, stop new connection acceptance), and close (pool teardown, registry removal) handlers conforming to the command port interface. Guard conditions and typed result emission for each.

**Out of Scope**: Drain timeout enforcement, drain progress tracking and observable workflow (sibling task), durable state machine persistence (sibling task), HTTP/messaging adapter, register and activate handlers (prior subtask).

**Implementation**: 1. Add `suspend`, `start-drain`, and `close` handler logic to `DynamicDatasourceCommandService`. 2. `suspend`: verify tenant is ACTIVE (InvalidTransition if not); update registry state to SUSPENDED; stop routing without destroying pool. 3. `start-drain`: verify tenant is ACTIVE or SUSPENDED; update state to DRAINING; reject new connection acquisition attempts; return success. 4. `close`: look up tenant (NotFound if absent); teardown pool resources (close connections, release pool); remove entry from registry; return success. Guard against double-close by returning AlreadyInState. 5. Ensure none of these operations synchronously wait for in-flight work — they are state transitions only. 6. Unit-test each handler covering success and key invalid-transition paths.

**Constraints**: start-drain must NOT wait synchronously for in-flight connections to finish — it is a state signal only; drain completion tracking is out of scope., close must release all pool resources deterministically to avoid connection leaks., All three handlers must return typed results — no unchecked exceptions for known precondition failures.

## Acceptance Criteria

- [ ] Calling suspend on an ACTIVE tenant transitions it to SUSPENDED, stops routing, and returns success.
- [ ] Calling suspend on a non-ACTIVE tenant (e.g., DRAINING, CLOSED) returns an InvalidTransition result.
- [ ] Calling start-drain on an ACTIVE or SUSPENDED tenant transitions it to DRAINING, prevents new connection acquisition, and returns success.
- [ ] Calling close on a tenant in any non-CLOSED state tears down the pool, removes the registry entry, and returns success.
- [ ] Calling close on an already-removed tenantId returns NotFound.
- [ ] All three handlers are covered by unit tests verifying each result variant without a real JDBC connection.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

