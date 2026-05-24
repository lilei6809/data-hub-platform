---
id: "0eff72e2-aa26-4e81-84e7-9e8a86cc293c"
entity_type: "task"
entity_id: "38c3ecf1-682c-4de6-876a-8f7f82f38a26"
title: "Implement register and activate command handlers in dynamic-datasource - Notes"
status: "todo"
priority: "high"
display_id: "LEI-12"
parent_task_id: "a1318d26-212f-4e43-978d-abe515479cdc"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:43:22.450314+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement the `register` and `activate` command handlers in `dynamic-datasource`, establishing the first two explicit lifecycle stages that replace the implicit onboarding path.

## Implementation Approach

1. Create a `DynamicDatasourceCommandService` implementing `DynamicDatasourceCommandPort`.
2. Implement `register`: validate tenant config → construct JDBC pool → insert into in-memory registry in `REGISTERED` state; return `AlreadyInState` if duplicate.
3. Implement `activate`: look up by tenantId (return `NotFound` if absent) → mark as `ACTIVE`; return `InvalidTransition` if already active.
4. Ensure both handlers are idempotent: repeated calls with the same state return deterministic result types.
5. Wire implementation to the command port so the control-plane caller can inject it.

## Acceptance Criteria

- `register` with a valid config inserts the datasource in `REGISTERED` (not-routable) state and returns success.
- `register` on an already-registered tenant returns `AlreadyInState` without creating a duplicate pool.
- `activate` on a `REGISTERED` tenant transitions it to `ACTIVE` (routable) and returns success.
- `activate` on an unknown tenantId returns `NotFound`.
- `activate` on an already-`ACTIVE` tenant returns `AlreadyInState`.
- Both handlers are covered by unit tests verifying each result variant without a real JDBC connection.

## Technical Constraints

- `register` must NOT make the datasource routable until `activate` is called.
- No unchecked exceptions for known precondition failures — typed results only.
- Pool construction delegates to an existing factory/builder if one is present in the module.

## Relevant Files

### Files to Create

- `dynamic-datasource/src/main/…/DynamicDatasourceCommandService.java` - Primary implementation of the command port
- `dynamic-datasource/src/test/…/DynamicDatasourceCommandServiceTest.java` - Unit tests for register and activate## Details

**Scope**: Concrete implementation of register (configure + pool construction + registry insertion) and activate (mark datasource live for routing) command handlers conforming to the interface defined in the prior subtask. Guard conditions for already-registered tenants and invalid pre-state. Correct result types per contract.

**Out of Scope**: suspend, start-drain, close command implementations; HTTP/messaging adapter layer; durable state persistence; state machine transitions owned by sibling tasks.

**Implementation**: 1. Create a `DynamicDatasourceCommandService` (or equivalent) that implements `DynamicDatasourceCommandPort`. 2. Implement `register`: validate tenant config, construct JDBC datasource/pool, insert into in-memory registry keyed by tenantId; return `AlreadyInState` if already registered. 3. Implement `activate`: look up registered datasource by tenantId (return `NotFound` if absent); mark it as active in the registry; return `InvalidTransition` if already active. 4. Ensure both handlers are idempotent: re-registering a tenant in the same configuration is safe and returns a deterministic result. 5. Wire implementation to the interface so the control-plane caller can inject/reference it.

**Constraints**: Register must not make the datasource available for query routing until activate is explicitly called., Both handlers must return typed result values — no unchecked exceptions for known precondition failures., Pool construction must be delegated to an existing factory/builder if one exists in the module rather than duplicating logic.

## Acceptance Criteria

- [ ] Calling register with a valid tenant configuration inserts the datasource into the registry in an REGISTERED (not yet routable) state and returns a success result.
- [ ] Calling register for a tenant that is already registered returns an AlreadyInState result without creating a duplicate pool.
- [ ] Calling activate on a registered tenant transitions it to ACTIVE (routable) and returns success.
- [ ] Calling activate on an unknown tenantId returns a NotFound result.
- [ ] Calling activate on a tenant that is already ACTIVE returns an AlreadyInState result.
- [ ] Both handlers are covered by unit tests that verify each result variant without relying on a real JDBC connection.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

