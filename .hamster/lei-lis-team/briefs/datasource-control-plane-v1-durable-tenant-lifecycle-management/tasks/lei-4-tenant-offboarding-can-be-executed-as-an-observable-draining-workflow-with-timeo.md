---
id: "51716aa9-8ece-458f-b1bd-0a90d00c45be"
entity_type: "task"
entity_id: "6124e75f-7caa-4062-bcda-68d3811e9d47"
title: "Tenant offboarding can be executed as an observable DRAINING workflow with timeout handling - Notes"
status: "todo"
priority: "high"
display_id: "LEI-4"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:41:21.651468+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Platform engineers can offboard a tenant through an observable DRAINING state so they can monitor progress, enforce deadlines, and audit whether drain completed gracefully or was forced.

Currently, `dynamic-datasource` can deregister a datasource, but DRAINING is invisible at the control-plane level. There is no progress visibility, no timeout enforcement, and no audit record distinguishing graceful from forced closure.

## Experience

When a tenant is offboarded, the control plane transitions it into an explicit **DRAINING** state that is externally visible. Drain progress — including open connection count and elapsed time — is queryable throughout the drain. A configurable deadline ensures the tenant eventually reaches CLOSED even if connections do not clear gracefully.

## Interaction

1. Offboard command is issued for tenant X
2. Control plane records `desired_state=CLOSED`, `actual_state=DRAINING`, and sets a drain deadline
3. The executor begins draining connections (`start-drain`)
4. Engineers and upstream systems can query `GET /tenants/{tenantId}/runtime-state` and see DRAINING with live progress
5. When drain completes: executor reports success → control plane calls `close` and transitions to CLOSED
6. If the deadline is exceeded: control plane forces `close` and records `drain_forced=true` in the audit log
7. Repeated offboard commands during DRAINING are safely ignored — no duplicate drain workflows are created## Details

**User Capability**: Platform engineers can initiate tenant offboarding and observe it progressing through a discrete DRAINING state before the tenant reaches CLOSED. The drain is tracked at the control-plane level, has a configurable deadline, and handles both graceful completion and timeout-forced closure.

**Business Value**: Currently, `dynamic-datasource` can deregister a datasource, but DRAINING is not represented as a control-plane-visible step. This means there is no external visibility into whether a tenant is draining, no timeout enforcement, and no audit record of whether offboarding completed gracefully or was forced. Without a real DRAINING workflow, platform engineers cannot safely offboard tenants from dependent systems that need to see a stable transitional state.

**Functional Requirements**:
- Model DRAINING as an explicit intermediate state in the tenant lifecycle, observable via the state read API
- On offboard command receipt, transition tenant to DRAINING before initiating any execution-level drain
- Track drain progress (e.g., open connection count, elapsed time) at the control-plane level
- Enforce a configurable drain timeout/deadline; if drain is not complete by deadline, force-transition to CLOSED
- On drain completion (reported by executor), transition tenant from DRAINING → CLOSED
- Emit observable events or expose a poll endpoint for DRAINING progress so upstream/admin tools can monitor
- Repeated offboard commands while in DRAINING state are idempotent — do not reset the drain or create duplicate drain workflows

**Data Model & Structure**:
- Extend tenant runtime record with: drain_started_at, drain_deadline, drain_forced (boolean), drain_completed_at
- Drain progress record: tenant_id, open_connections_at_start, current_open_connections, sampled_at
- DRAINING is a named state in the lifecycle state machine (already defined in the state machine task)

**Technical Approach**:
- Offboard command handler transitions tenant to DRAINING, writes drain metadata, then calls `start-drain` on the executor
- A drain monitor (scheduled task or event loop) polls executor `status` or receives drain-completion callbacks and updates control-plane drain progress
- Timeout enforcer: a scheduled job checks drain_deadline vs. current time; on expiry, calls `close` on executor and transitions tenant to CLOSED with drain_forced=true
- All drain events are recorded in the transition audit log

**User Workflows**:
1. Platform engineer or upstream system issues offboard command for tenant X
2. Control plane writes desired_state=CLOSED, actual_state=DRAINING, sets drain_deadline
3. Executor is called with `start-drain(tenantId)`
4. Control plane monitors drain progress (polls or receives callbacks)
5. When drain completes: control plane calls `close(tenantId)`, transitions actual_state to CLOSED
6. If deadline is exceeded: control plane calls `close(tenantId)` forcefully, records drain_forced=true, transitions to CLOSED
7. External tools can poll `GET /tenants/{tenantId}/runtime-state` and see DRAINING with progress details throughout

**API/Interface Specifications**:
- `GET /tenants/{tenantId}/runtime-state` → includes drain_started_at, drain_deadline, current_open_connections when in DRAINING state
- `GET /tenants/{tenantId}/drain-progress` (optional dedicated endpoint) → real-time drain progress
- No new command API needed — offboard command (from command orchestration task) triggers this workflow

**Scope - INCLUDED**: DRAINING state orchestration at the control-plane level, drain progress tracking, configurable timeout/deadline, forced closure on timeout, idempotency for repeated offboard commands during DRAINING, audit logging of drain outcome (graceful vs. forced)
**Scope - EXCLUDED**: State machine definition (covered by state machine task), command model for offboard (covered by command orchestration task), executor-level `start-drain` and `close` implementation (covered by dynamic-datasource task)

**Success Criteria**:
- A tenant being offboarded is visible in DRAINING state via the runtime-state API before reaching CLOSED
- Drain progress (open connections, elapsed time) is queryable while in DRAINING state
- A configurable drain deadline is enforced — tenants that exceed it are forcibly closed and flagged as drain_forced=true
- Issuing a second offboard command while a tenant is already DRAINING does not reset or duplicate the drain workflow
- The transition audit log records whether drain completed gracefully or was forced by timeout

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | Tenant lifecycle state machine can be managed as durable runtime state, Tenant lifecycle actions can be orchestrated as durable commands with execution feedback, dynamic-datasource can execute explicit lifecycle commands with observable semantics |
| testStrategy | Trigger offboard for a tenant and confirm it enters DRAINING state via the runtime-state API before any CLOSED transition. Query drain progress while in DRAINING and confirm open connection count is present. Let the drain deadline expire without completing and confirm tenant transitions to CLOSED with drain_forced=true. Issue a second offboard command while a tenant is already DRAINING and confirm no duplicate drain workflow is created. Inspect the audit log and verify graceful vs. forced drain outcomes are distinguishable. |

