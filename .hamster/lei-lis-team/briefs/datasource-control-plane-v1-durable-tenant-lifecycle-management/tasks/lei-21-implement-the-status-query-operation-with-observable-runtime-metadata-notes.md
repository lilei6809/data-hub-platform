---
id: "9b5e4765-ccb2-4195-8ff2-ddf971d1f280"
entity_type: "task"
entity_id: "264011ba-4a24-4b3f-9a91-19c192cf7040"
title: "Implement the status query operation with observable runtime metadata - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-21"
parent_task_id: "a1318d26-212f-4e43-978d-abe515479cdc"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:44:08.091363+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Implement the `status` query operation on `dynamic-datasource` so the control plane can observe the current runtime state and metadata of any tenant datasource at any point in its lifecycle.

## Implementation Approach

1. Implement the `status` method on `DynamicDatasourceCommandService`.
2. Look up the tenantId in the registry; return `NotFound` if absent.
3. Map the registry entry to a `DatasourceStatusView`: lifecycle state, last-transition timestamp, available runtime indicators (e.g., pool active count).
4. Ensure the operation is a pure read with no side effects.
5. Unit-test: verify correct state is reflected after each lifecycle transition.

## Acceptance Criteria

- `status` for a known tenant returns a `DatasourceStatusView` with the correct current lifecycle state.
- `status` for an unknown tenantId returns a `NotFound` result.
- `DatasourceStatusView` includes a last-transition timestamp and the current lifecycle state enum value.
- Calling `status` has no observable side effects on the registry or pool.
- Unit tests verify correct state after each lifecycle command: `register`, `activate`, `suspend`, `start-drain`, `close`.

## Technical Constraints

- `status` must be non-mutating — calling it must not alter the registry or pool state.
- Runtime indicators included must only be those safely readable without blocking or network I/O.
- Response value type must be immutable and serialisation-friendly for future adapter layers.

## Relevant Files

### Files to Create

- `dynamic-datasource/src/main/…/query/DatasourceStatusView.java` - Immutable value type returned by status operation
- `dynamic-datasource/src/test/…/DynamicDatasourceStatusQueryTest.java` - Unit tests verifying status across all lifecycle states## Details

**Scope**: Implementation of the status command handler that reads the current in-memory registry entry and maps it to a DatasourceStatusView response value containing lifecycle state, available runtime indicators, and last-transition timestamp.

**Out of Scope**: External HTTP health endpoints, metrics/monitoring exporters, tenant projection aggregation (sibling task), durable state queries from a persistence layer, drain progress percentage or timeout tracking (sibling task).

**Implementation**: 1. Implement the `status` method on `DynamicDatasourceCommandService`. 2. Look up the tenantId in the registry; return `NotFound` result if absent. 3. Map the registry entry to a `DatasourceStatusView`: populate lifecycle state, last-transition timestamp, and any safely available runtime indicators (e.g., pool active count). 4. Ensure the operation has no side effects — it is a pure read. 5. Unit-test: verify correct state is reflected after each lifecycle transition (register → activate → suspend → start-drain → close).

**Constraints**: status must be non-mutating — calling it must not alter the registry or pool state., Runtime indicators included must only be those safely readable without blocking or network I/O., Response value type must be immutable and serialisation-friendly for future adapter layers.

## Acceptance Criteria

- [ ] Calling status for a known tenant returns a DatasourceStatusView with the correct current lifecycle state.
- [ ] Calling status for an unknown tenantId returns a NotFound result.
- [ ] The DatasourceStatusView includes a last-transition timestamp and at least the current lifecycle state enum value.
- [ ] Calling status has no observable side effects on the datasource registry or pool.
- [ ] Unit tests verify that status returns the correct state after each lifecycle command (register, activate, suspend, start-drain, close).

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

