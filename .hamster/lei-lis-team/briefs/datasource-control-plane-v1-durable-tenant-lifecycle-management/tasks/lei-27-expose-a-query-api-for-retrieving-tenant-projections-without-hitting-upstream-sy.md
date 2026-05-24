---
id: "60aaa12c-4387-4c5e-9ceb-0c1160e5a8ff"
entity_type: "task"
entity_id: "753b4aff-08e5-4a59-8a12-345bac4d31e9"
title: "Expose a query API for retrieving tenant projections without hitting upstream systems - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-27"
parent_task_id: "dfc8152d-2520-4a4d-837d-da05eb52198a"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:44:34.960908+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Expose a read-only query API for retrieving `TenantProjection` records from the local store without hitting upstream systems.

## Implementation Approach

1. Define response DTOs for single and list projection responses.
2. Implement single-tenant lookup: `store.findById(tenantId)` → projection DTO or structured 404.
3. Implement bulk listing: `store.findAll()` or `store.findByLifecycleState(state)` → list DTO.
4. Add optional `lifecycleState` query parameter for filtering bulk results.
5. Ensure all endpoints are strictly read-only.
6. Document in API comments that responses reflect the last ingested local state and may lag upstream.

## Acceptance Criteria

- Single-tenant lookup returns full projection DTO or structured not-found for unknown tenants.
- Bulk listing returns all or filtered projections based on `lifecycleState` parameter.
- All responses served from local store — zero upstream calls at query time.
- API documentation states local-projection semantics explicitly.
- Endpoints are strictly read-only with no side effects.
- Empty result sets return valid empty list, not an error.

## Technical Constraints

- Read-only API — no writes or side effects.
- Responses must communicate local-projection (not live) semantics.
- Bulk listing must not trigger upstream calls.
- Empty-list case handled gracefully.## Details

**Scope**: Query API handler(s) or service methods for: GET /tenants/{tenantId}/projection (single lookup) and GET /tenants/projections?lifecycleState=... (bulk/filtered listing). Response models/DTOs wrapping TenantProjection. Structured not-found and empty-list responses. Documentation of projection-vs-live semantics.

**Out of Scope**: TenantProjectionStore implementation (earlier subtask), TenantProjection model (first subtask), ingestion paths (previous two subtasks), write/mutation endpoints, state machine or command APIs (sibling tasks).

**Implementation**: 1. Define response DTOs for single projection and list projection responses — map from `TenantProjection` model. 2. Implement single-tenant lookup handler: call `store.findById(tenantId)`, return projection DTO or structured 404. 3. Implement bulk listing handler: call `store.findAll()` or `store.findByLifecycleState(state)`, return list DTO. 4. Add optional `lifecycleState` query parameter to the bulk endpoint for filtering. 5. Ensure all endpoints are strictly read-only — reject or do not define any write methods. 6. Document in API comments that responses reflect the local projection (last ingested state) and may lag upstream by propagation delay. 7. Test: known tenant returns full projection; unknown tenant returns structured not-found; filtered listing returns only matching tenants; empty filter returns all tenants.

**Constraints**: API must be strictly read-only — no side effects or writes triggered by queries., Responses must clearly communicate that data is from the local projection store, not a live upstream call., Bulk listing must not perform upstream calls — served entirely from the local store., API must handle the case of no projections existing gracefully (return empty list, not error).

## Acceptance Criteria

- [ ] Single-tenant lookup returns the full TenantProjection DTO for a known tenant and a structured not-found response for an unknown tenant.
- [ ] Bulk listing returns all projections when no filter is applied, and only matching projections when lifecycleState filter is provided.
- [ ] All query responses are served entirely from the local projection store — no upstream calls are made at query time.
- [ ] API documentation explicitly states that responses reflect locally projected state and may lag upstream changes by propagation delay.
- [ ] Endpoints are strictly read-only: no write, upsert, or side-effect operations are exposed.
- [ ] Empty result sets (no tenants, or no tenants matching filter) return a valid empty list response, not an error.

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

