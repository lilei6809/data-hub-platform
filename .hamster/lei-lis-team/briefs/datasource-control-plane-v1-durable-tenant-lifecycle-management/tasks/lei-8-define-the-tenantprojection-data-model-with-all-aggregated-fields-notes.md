---
id: "0f098a24-a806-4d90-9fcf-57396b6a822c"
entity_type: "task"
entity_id: "405f1b53-c241-475a-b3dd-1e2d635c618a"
title: "Define the TenantProjection data model with all aggregated fields - Notes"
status: "todo"
priority: "high"
display_id: "LEI-8"
parent_task_id: "dfc8152d-2520-4a4d-837d-da05eb52198a"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:42:59.908266+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Define the `TenantProjection` immutable data model that aggregates lifecycle state and routing inputs into a single, typed, serialisable record.

## Implementation Approach

1. Enumerate lifecycle fields: `tenantId`, `lifecycleState` (enum), `stateEnteredAt`, `failureReason` (nullable).
2. Enumerate routing fields: datasource coordinates or routing metadata needed at query time.
3. Add cross-cutting fields: `lastUpdatedAt` (instant), `inputVersion` / `sequenceNumber` (long).
4. Declare model as immutable record/value object with no mutable setters.
5. Document field nullability, purpose, and model invariants inline.
6. Confirm no field ownership overlap with the sibling lifecycle state machine model.

## Acceptance Criteria

- `TenantProjection` is defined as an immutable record with all lifecycle and routing fields explicitly typed and documented.
- The model carries `lastUpdatedAt` and `inputVersion`/`sequenceNumber` for idempotent write detection.
- Each field has documented nullability and purpose; invariants are stated explicitly.
- Model is serialisable without data loss.
- Code review confirms no field ownership overlap with the sibling lifecycle task's state machine model.

## Technical Constraints

- Immutable — replace-on-write semantics only, no mutable setters.
- Must not embed execution or command state (read view only).
- `inputVersion` must support monotonic ordering for last-write-wins idempotency.## Details

**Scope**: Definition of the TenantProjection model/record: all fields, their types, nullability, the version/sequence marker, and the lastUpdatedAt timestamp. Inline documentation of each field's purpose and the invariants the model must satisfy.

**Out of Scope**: Persistence schema (next subtask), event ingestion logic, query API, state machine ownership (sibling task), routing decision logic.

**Implementation**: 1. Enumerate every field required from lifecycle inputs: tenantId, lifecycleState (enum), stateEnteredAt, failureReason (nullable). 2. Enumerate every field required from routing inputs: datasource coordinates or routing metadata relevant to query-time decisions (exact fields driven by the broader brief). 3. Add cross-cutting fields: lastUpdatedAt (instant), inputVersion / sequenceNumber (long) for idempotency. 4. Declare the model as an immutable record/value object. 5. Document invariants: tenantId is always present, lifecycleState is never null, inputVersion is monotonically non-decreasing. 6. Review model against all sibling task outputs to confirm no field duplication or ownership ambiguity.

**Constraints**: Model must be immutable — no mutable setters; replace-on-write semantics only., Must not embed execution or command state — projection is a read view only., inputVersion must support monotonic ordering to enable last-write-wins idempotency at the store level.

## Acceptance Criteria

- [ ] TenantProjection model is defined as an immutable record/value object with all lifecycle and routing fields explicitly typed and documented.
- [ ] The model carries a lastUpdatedAt timestamp and an inputVersion/sequenceNumber field suitable for idempotent write detection.
- [ ] Each field has documented nullability and an explanation of its purpose; invariants (non-null tenantId, non-null lifecycleState) are stated explicitly.
- [ ] The model is serialisable (e.g., via standard serialisation mechanism used in the project) so it can be persisted and retrieved without data loss.
- [ ] A code review confirms no field ownership overlaps with the state machine model defined in the sibling lifecycle task.

## Context

| Field | Value |
|-------|-------|
| category | design |
| complexity | 4 |

