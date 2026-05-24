---
id: "f1bfcb85-5f3c-4a5c-b1f9-28485ed54ddb"
entity_type: "task"
entity_id: "dfc8152d-2520-4a4d-837d-da05eb52198a"
title: "Tenant projection can be maintained as a local, decision-ready view from upstream inputs - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-5"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:41:52.510737+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Platform engineers and automation agents can query a local, decision-ready tenant projection so the control plane can make lifecycle decisions without depending on upstream system availability at runtime.

Without a local projection, every routing or lifecycle decision requires a synchronous upstream call. This adds latency, creates a hard dependency on upstream availability, and makes the control plane fragile under upstream outages.

## Experience

The control plane maintains a local tenant projection — a materialized view of tenant configuration, contract status, and routing intent sourced from upstream systems. This projection is always query-ready locally. Upstream changes are ingested asynchronously, deduplicated, and applied in order.

## Interaction

1. Upstream billing or contract system emits a lifecycle or routing event
2. The event consumer receives it, deduplicates by event ID, and updates the local projection
3. The lifecycle orchestrator reads the local projection to make decisions — no upstream round-trip
4. Admin tools and automation agents call `GET /tenants/{tenantId}/projection` to inspect current contract status and routing config
5. Out-of-order or replayed events are safely handled — newer state is never overwritten by a stale event
6. The `last_upstream_sync_at` field makes staleness visible for monitoring and alerting## Details

**User Capability**: The control plane maintains a local tenant projection — a materialized, decision-ready view of tenant configuration and routing intent — sourced from upstream lifecycle and routing systems. This projection is queryable locally without round-tripping to upstream systems, and is kept current via event consumption or polling.

**Business Value**: This directly maps to the brief's stated capability: "Tenant Projection Management — Maintain a local, decision-ready tenant projection sourced from upstream lifecycle and routing inputs." Without a local projection, every routing or lifecycle decision requires a synchronous upstream call, which adds latency and creates a hard dependency on upstream availability. A local projection decouples the control plane from upstream system availability at decision time.

**Functional Requirements**:
- Define the tenant projection model: fields sourced from upstream (e.g., tenant_id, contract_status, routing_config, jdbc_config, effective_from, effective_until, last_upstream_sync_at)
- Ingest upstream lifecycle events (e.g., contract activated, billing suspended) and update the local projection accordingly
- Ingest upstream routing configuration changes and reflect them in the projection
- Expose a query API for reading the current tenant projection by tenant_id
- Support bulk or paginated listing of all tenant projections (for reconciliation and admin tooling)
- Handle upstream event deduplication and out-of-order delivery gracefully
- Record the timestamp of last upstream synchronization per tenant for staleness detection

**Data Model & Structure**:
- Tenant projection record: tenant_id, contract_status, routing_config (JSON), jdbc_config (JSON), effective_from, effective_until, last_upstream_sync_at, projection_version
- Upstream event log (for deduplication): event_id, tenant_id, event_type, received_at, applied (boolean)

**Technical Approach**:
- Projection updater: a consumer of upstream events (queue, webhook, or polling) that applies changes to the local projection record
- Idempotency: upstream events are deduplicated by event_id before applying
- Out-of-order handling: projection_version or effective_from timestamps used to prevent stale events from overwriting newer state
- Query layer: simple read repository on top of the projection store

**User Workflows**:
1. Upstream billing system emits "tenant suspended" event
2. Event consumer receives it, deduplicates by event_id, updates local projection (contract_status=SUSPENDED)
3. Control plane's lifecycle orchestrator reads local projection to determine whether to issue a SUSPEND command — no upstream call needed
4. Admin tool queries `GET /tenants/{tenantId}/projection` to see current routing config and contract status
5. Reconciler compares projection data against actual runtime state to detect drift

**API/Interface Specifications**:
- `GET /tenants/{tenantId}/projection` → returns tenant projection fields
- `GET /tenants/projections` (paginated) → returns all tenant projections
- Internal `ProjectionRepository` interface for use by orchestration components

**Scope - INCLUDED**: Tenant projection model and persistence, upstream event ingestion and deduplication, out-of-order event handling, projection query API (single and bulk), staleness tracking via last_upstream_sync_at
**Scope - EXCLUDED**: Lifecycle state machine (covered by state machine task), command orchestration (covered by command task), DRAINING workflow (covered by offboarding task), executor implementation (covered by dynamic-datasource task)

**Success Criteria**:
- Tenant projection is populated and queryable without a synchronous upstream call at query time
- Upstream events update the projection and are deduplicated — replayed events do not overwrite newer state
- Out-of-order events (older effective_from than current projection) are safely ignored
- `last_upstream_sync_at` accurately reflects the last time each tenant's projection was updated from upstream
- Bulk listing of projections supports pagination and is usable by reconciliation and admin tooling

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | Tenant lifecycle state machine can be managed as durable runtime state |
| testStrategy | Emit an upstream lifecycle event and verify the local projection is updated correctly without any synchronous upstream call at query time. Replay the same event with the same event_id and confirm the projection is not changed. Deliver events out of order (older effective_from than current state) and confirm newer state is preserved. Query the projection API and confirm last_upstream_sync_at is accurate. Verify bulk listing endpoint supports pagination and returns all tenant projections. |

