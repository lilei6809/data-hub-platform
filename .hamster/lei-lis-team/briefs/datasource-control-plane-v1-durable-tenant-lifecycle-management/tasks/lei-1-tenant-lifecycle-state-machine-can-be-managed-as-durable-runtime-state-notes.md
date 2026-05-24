---
id: "e01f5451-457a-4793-92be-4864912d723c"
entity_type: "task"
entity_id: "e8774817-16ba-4c59-934c-14803d53c035"
title: "Tenant lifecycle state machine can be managed as durable runtime state - Notes"
status: "todo"
priority: "urgent"
display_id: "LEI-1"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:39:43.406024+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Platform engineers can manage tenant datasource lifecycle through a durable state machine so they can safely drive onboarding, suspension, and offboarding across restarts.

Tenant lifecycle intent is currently implicit — there are no defined states, no durable persistence, and no reconciliation. This means a process restart destroys all in-flight lifecycle context, and there is no way to audit what happened or retry safely.

## Experience

Platform engineers interact with an explicit, queryable lifecycle state per tenant. Every tenant has a **desired state** and an **actual state** persisted to a durable store. The control plane continuously reconciles discrepancies. Engineers can query any tenant's runtime state at any time, and the system guarantees that duplicate or replayed inbound events never produce duplicate terminal transitions.

## Interaction

1. An upstream system emits a lifecycle intent (e.g., onboard tenant X)
2. The control plane creates a tenant runtime record with `desired_state=PROVISIONING` and `actual_state=PROVISIONING`, persisted immediately
3. The reconciler continuously monitors desired ≠ actual and drives convergence
4. After any process restart, in-progress orchestration is resumed from persisted state without manual reconstruction
5. Engineers query `GET /tenants/{tenantId}/runtime-state` to inspect current and desired state
6. Duplicate lifecycle events are safely deduplicated — no double transitions into terminal states## Details

**User Capability**: Platform engineers can observe and drive a tenant's lifecycle through explicit states (PROVISIONING, ACTIVE, SUSPENDED, DRAINING, FAILED, DEGRADED, CLOSED), with all state transitions persisted durably so the system can resume after restart and reconcile actual vs. desired state.

**Business Value**: The current system has no explicit state machine — lifecycle intent is implicit and runtime state lives only in memory. Without persisted, reconcilable state, platform engineers cannot safely support onboarding, suspension, resumption, graceful offboarding, or failure recovery. This task establishes the foundation that every other lifecycle capability depends on.

**Functional Requirements**:
- Define an explicit tenant lifecycle state machine with at minimum the following states: PROVISIONING, ACTIVE, SUSPENDED, DRAINING, FAILED, DEGRADED, CLOSED
- Define all valid state transitions and guard conditions (e.g., ACTIVE → DRAINING → CLOSED, PROVISIONING → ACTIVE, ACTIVE → SUSPENDED → ACTIVE)
- Persist tenant runtime state (desired state and actual state separately) to a durable store so it survives process restarts
- Support a reconciliation loop or mechanism that compares desired-vs-actual state and drives convergence
- Expose a queryable API for reading current tenant state (desired and actual) by tenant ID
- Ensure that idempotent handling of duplicate inbound lifecycle events does not produce duplicate terminal transitions

**Data Model & Structure**:
- Tenant runtime record: tenant_id, desired_state, actual_state, version/etag, last_transition_at, created_at, metadata
- State transition audit log: tenant_id, from_state, to_state, triggered_by, timestamp, correlation_id
- States must be modeled as a closed enum with explicit valid transition map

**Technical Approach**:
- Introduce a `TenantRuntimeState` domain entity within `datasource-control-plane` module
- Persist records via a relational store with optimistic locking (version field) to prevent concurrent write races
- Implement a state machine library or hand-rolled transition validator that enforces the valid transition graph
- Reconciliation: a scheduled or event-triggered loop reads tenants where desired_state ≠ actual_state and issues corrective commands

**User Workflows**:
1. Upstream system emits a lifecycle intent event (e.g., "onboard tenant X")
2. Control plane writes a new tenant record with desired_state=PROVISIONING and actual_state=PROVISIONING
3. On restart, the reconciler reads persisted records and resumes in-progress workflows
4. Engineers query the state API to inspect any tenant's current and desired state
5. Duplicate inbound events are detected via idempotency key / version check and ignored

**API/Interface Specifications**:
- `GET /tenants/{tenantId}/runtime-state` → returns desired_state, actual_state, last_transition_at
- State transition endpoint or internal service method that validates and persists transitions

**Scope - INCLUDED**: Tenant state machine definition, persistence schema, transition validator, desired-vs-actual model, reconciliation mechanism, read API for state inspection, idempotency handling for inbound events
**Scope - EXCLUDED**: Specific lifecycle action orchestration (covered by "Tenant lifecycle actions can be orchestrated as durable commands"), dynamic-datasource execution semantics (covered by "dynamic-datasource can execute explicit lifecycle commands"), DRAINING workflow specifics (covered by offboarding task)

**Success Criteria**:
- All seven lifecycle states and their valid transitions are documented and enforced in code
- Tenant runtime state survives a process restart and can be queried immediately after restart
- Reconciliation loop detects and acts on desired ≠ actual state discrepancies
- Duplicate inbound lifecycle events for a tenant in terminal state produce no further state changes
- State transition history is queryable for audit purposes

## Context

| Field | Value |
|-------|-------|
| testStrategy | Verify that all seven lifecycle states and their valid/invalid transitions are enforced. Simulate a process restart mid-lifecycle and confirm reconciliation resumes correctly from persisted state. Send duplicate inbound events for a tenant in a terminal state and confirm no further transitions occur. Query the state read API before and after transitions to verify correctness of desired vs. actual state fields. |

