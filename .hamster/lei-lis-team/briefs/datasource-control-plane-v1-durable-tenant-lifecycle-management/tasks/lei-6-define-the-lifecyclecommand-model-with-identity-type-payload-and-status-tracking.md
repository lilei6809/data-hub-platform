---
id: "713089b6-01b1-4e05-b109-5c68c6ec007e"
entity_type: "task"
entity_id: "c633c147-9bc7-4f05-aeb8-da19801a6d9f"
title: "Define the LifecycleCommand model with identity, type, payload, and status tracking fields - Notes"
status: "todo"
priority: "high"
display_id: "LEI-6"
parent_task_id: "143db2d9-61cc-4068-b884-050827fe5b4b"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:42:56.76335+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Define the `LifecycleCommand` domain model that represents a durable, identifiable unit of lifecycle intent for a tenant datasource.

## Implementation Approach

1. Create a `LifecycleCommandType` enum with values: `ONBOARD`, `SUSPEND`, `RESUME`, `OFFBOARD`.
2. Create a `LifecycleCommandStatus` enum with values: `PENDING`, `IN_PROGRESS`, `SUCCEEDED`, `FAILED`, `CANCELLED`.
3. Create the `LifecycleCommand` domain model (record, value object, or immutable class) with the following fields:

- `commandId` (UUID) — unique identifier for this command instance
- `tenantId` (String/UUID) — target tenant
- `type` (LifecycleCommandType) — the action being requested
- `idempotencyKey` (String) — caller-supplied key to prevent duplicate execution
- `status` (LifecycleCommandStatus) — current processing status
- `payload` (Map<String, Object> or typed sub-record) — optional action-specific parameters
- `retryCount` (int, default 0) — number of times execution has been attempted
- `errorDetail` (String, nullable) — last failure message if status is FAILED
- `createdAt`, `updatedAt` (Instant) — lifecycle timestamps
- `completedAt` (Instant, nullable) — set when terminal status is reached

1. Ensure immutability: transitions produce new instances (e.g., via `withStatus(...)` copy methods or a builder).
2. Add lightweight model-level validation (non-null required fields, no negative retry count).

## Acceptance Criteria

- A `LifecycleCommand` type exists with all specified fields at the correct types.
- `LifecycleCommandType` and `LifecycleCommandStatus` are first-class enums covering all required values.
- The model is immutable or uses copy-with semantics so in-place mutation is not possible.

## Technical Constraints

- No persistence annotations or framework coupling in the domain model itself — keep it a pure domain object.
- Model must be serialisable (for persistence and event publishing in later subtasks).
- Idempotency key must be a non-null, non-empty string.## Details

**Scope**: LifecycleCommand domain model class/record with fields: command ID, tenant ID, command type (ONBOARD/SUSPEND/RESUME/OFFBOARD), idempotency key, status (PENDING/IN_PROGRESS/SUCCEEDED/FAILED/CANCELLED), payload/parameters map, retry count, error detail, and timestamps (createdAt, updatedAt, completedAt).

**Out of Scope**: Persistence schema/repository (separate subtask). Command dispatch logic, retry scheduling, and execution callback handling (separate subtasks). State machine transitions are owned by the sibling task on durable runtime state.

## Acceptance Criteria

- [ ] A LifecycleCommand type exists with fields for: command ID (UUID), tenant ID, command type enum (ONBOARD, SUSPEND, RESUME, OFFBOARD), idempotency key (string), status enum (PENDING, IN_PROGRESS, SUCCEEDED, FAILED, CANCELLED), optional payload map, retry count (integer, default 0), optional error detail string, createdAt timestamp, updatedAt timestamp, optional completedAt timestamp.
- [ ] The command type and status are represented as first-class enums (not raw strings), so all valid values are explicitly enumerable and compile-time safe.
- [ ] The model is immutable or uses a builder/copy-with pattern so state transitions produce new instances rather than mutating in place, supporting safe concurrent access and audit trail construction.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |

