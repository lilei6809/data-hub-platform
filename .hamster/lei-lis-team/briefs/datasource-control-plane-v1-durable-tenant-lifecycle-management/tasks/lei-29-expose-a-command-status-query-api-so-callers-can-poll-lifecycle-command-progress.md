---
id: "c95fa2e8-ce6c-4be4-939a-3674654eef3f"
entity_type: "task"
entity_id: "8f1ca5d8-1603-4b7e-b128-5e6d4328e1c2"
title: "Expose a command status query API so callers can poll lifecycle command progress - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-29"
parent_task_id: "143db2d9-61cc-4068-b884-050827fe5b4b"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:44:57.291388+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Expose read endpoints for querying lifecycle command status by command ID and listing all commands for a tenant, providing the observability surface for the durable command model.

## Implementation Approach

1. Define `LifecycleCommandResponse` DTO with fields: `commandId`, `tenantId`, `commandType`, `status`, `retryCount`, `errorDetail`, `createdAt`, `updatedAt`, `completedAt`.
2. Implement `GET /lifecycle-commands/{commandId}`:

- Call `repository.findById(commandId)`.
- If present, map to `LifecycleCommandResponse` and return 200.
- If not found, return 404 with a descriptive error body.

1. Implement `GET /tenants/{tenantId}/lifecycle-commands`:

- Accept optional query parameter `status` (e.g., `?status=FAILED`).
- Call `repository.findByTenantId(tenantId)` or `findByTenantIdAndStatus(tenantId, status)`.
- Return list of `LifecycleCommandResponse` ordered by `createdAt` descending.

1. Map domain objects to DTOs in a dedicated mapper — do not return raw domain or entity objects.
2. Register both endpoints in the controller layer with appropriate HTTP method and path conventions.

## Acceptance Criteria

- `GET /lifecycle-commands/{commandId}` returns full command detail or 404 for unknown IDs.
- `GET /tenants/{tenantId}/lifecycle-commands` returns ordered list with optional status filter.
- Response schema is a clean DTO with no persistence or internal fields exposed.

## Technical Constraints

- Endpoints are read-only; no mutation logic belongs here.
- Responses must include all auditable timestamp fields so callers can determine duration and timing.
- The status filter parameter should be validated against the `LifecycleCommandStatus` enum; invalid values return 400.## Details

**Scope**: GET /lifecycle-commands/{commandId} endpoint returning command status and full detail. GET /tenants/{tenantId}/lifecycle-commands endpoint returning list of commands for a tenant. LifecycleCommandResponse DTO with all auditable fields. 404 response for unknown command IDs.

**Out of Scope**: Command submission endpoint (POST) — that is part of the service/API wiring subtask. Callback ingestion endpoint — separate subtask. State machine or runtime state queries — sibling task. Authentication/authorization middleware — out of scope for this brief.

## Acceptance Criteria

- [ ] GET /lifecycle-commands/{commandId} returns the full command detail (commandId, tenantId, commandType, status, retryCount, errorDetail, createdAt, updatedAt, completedAt) for a known command, and returns 404 for an unknown ID.
- [ ] GET /tenants/{tenantId}/lifecycle-commands returns a list of all LifecycleCommand records for the given tenantId ordered by createdAt descending, supporting at minimum an optional status filter query parameter.
- [ ] The response schema is a stable, documented DTO that does not leak persistence entity fields or internal implementation details.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |

