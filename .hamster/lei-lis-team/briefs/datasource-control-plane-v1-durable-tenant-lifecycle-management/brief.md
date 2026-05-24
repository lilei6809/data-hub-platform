---
id: "4619150c-0cff-4c5a-8e4f-bbb0f845af4f"
entity_type: "brief"
entity_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
title: "Datasource Control Plane V1: Durable Tenant Lifecycle Management"
status: "draft"
priority: ""
updated_at: "2026-03-27T11:38:45.015461+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

Datasource Control Plane V1 PRD

## Problem Statement The repository currently has two relevant modules: `datasource-control-plane` and `dynamic-datasource`. Together they can register tenant-specific JDBC datasources, but they do not yet form a durable control plane for tenant lifecycle management.

The current implementation has four core pain points:

Tenant lifecycle intent is implicit. There is no explicit state machine for PROVISIONING, ACTIVE, SUSPENDED, DRAINING, FAILED, DEGRADED, and CLOSED.
Runtime authority is weak. Tenant runtime state is not modeled as durable desired-vs-actual state that can survive restart and support reconciliation.
Execution coupling is too direct. Control-plane logic is tightly coupled to synchronous execution behavior instead of a durable command and execution-feedback model.
Offboarding is not observable as a workflow. dynamic-datasource can deregister a datasource, but DRAINING is not represented as a control-plane-visible step with progress and timeout handling.
Without a real control plane, platform engineers cannot safely support tenant onboarding, suspension, resumption, graceful offboarding, or failure recovery. Existing synchronous paths also make it hard to audit what happened, retry safely, or later replace dynamic-datasource with a dedicated pool manager.

Target Users
Platform engineers who need a reliable service to orchestrate tenant datasource lifecycle changes.
Backend engineers who need a clear architecture for implementing lifecycle workflows, persistence, and execution integration.
Future admin tools and automation agents that need queryable tenant runtime state and safe manual intervention APIs.
Upstream tenant, billing, and contract systems that emit lifecycle intent and expect stable downstream execution.
Success Metrics
100% of supported lifecycle actions (onboard, suspend, resume, offboard) are represented by durable runtime state and explicit orchestration steps.
0 critical lifecycle progress is stored only in memory.
A tenant can be offboarded through an externally visible DRAINING state before reaching CLOSED.
Repeated inbound events or execution callbacks do not produce duplicate terminal actions for the same tenant.
After process restart, in-progress orchestration can be resumed from persisted state without manual reconstruction.
dynamic-datasource exposes explicit execution semantics (register, activate, suspend, start-drain, close, status) instead of one ambiguous deregistration path.

## Capability Tree

Capability: Tenant Projection Management
Maintain a local, decision-ready tenant projection sourced from upstream lifecycle and routing inputs.