---
id: "30491620-1a0b-4234-85b0-fcbaf422f53e"
entity_type: "task"
entity_id: "edd2c869-8208-4757-8990-888200f2105e"
title: "Wire the command port into the control-plane integration boundary - Notes"
status: "todo"
priority: "high"
display_id: "LEI-26"
parent_task_id: "a1318d26-212f-4e43-978d-abe515479cdc"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:44:32.457475+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Wire the fully implemented `DynamicDatasourceCommandPort` into the application context and expose it as the stable integration boundary for `datasource-control-plane`, while deprecating or removing the old ambiguous deregistration path.

## Implementation Approach

1. Add a `DynamicDatasourceConfiguration` class that declares `DynamicDatasourceCommandService` as the `DynamicDatasourceCommandPort` bean.
2. Ensure `datasource-control-plane` injects the port interface, not the concrete service.
3. Mark or remove the old deregistration method with a migration note.
4. Write an integration smoke test that calls all six commands in sequence against an embedded datasource.
5. Verify no callers of the old path are left without migration guidance.

## Acceptance Criteria

- `DynamicDatasourceCommandService` is registered as the `DynamicDatasourceCommandPort` bean and injectable by `datasource-control-plane`.
- The old single-path deregistration entrypoint is deprecated or removed with a documented migration note.
- `datasource-control-plane` depends on the port interface, not the concrete implementation class.
- An integration smoke test exercises all six commands in sequence and asserts each returns the expected result type.
- The integration test runs without a real JDBC database.

## Technical Constraints

- `datasource-control-plane` must depend only on the port interface, not the concrete service class.
- The old deregistration entry point must be deprecated or removed — leaving both active perpetuates the ambiguity the brief is eliminating.
- Integration test must not require a real database — use an embedded or in-memory datasource.

## Relevant Files

### Files to Create

- `dynamic-datasource/src/main/…/config/DynamicDatasourceConfiguration.java` - Spring configuration declaring the command port bean
- `dynamic-datasource/src/test/…/DynamicDatasourceCommandPortIntegrationTest.java` - Smoke test exercising all six commands end-to-end## Details

**Scope**: Wiring DynamicDatasourceCommandService as the implementation of DynamicDatasourceCommandPort in the application context (e.g., Spring configuration); exposing the port to datasource-control-plane as a stable API boundary; deprecating or removing the old ambiguous deregistration path; lightweight integration smoke test covering all six commands.

**Out of Scope**: Durable command dispatch, orchestration logic, HTTP/messaging transport adapters, drain progress tracking, state machine persistence — all in sibling tasks.

**Implementation**: 1. Add a configuration class (e.g., `DynamicDatasourceConfiguration`) in `dynamic-datasource` that declares `DynamicDatasourceCommandService` as the `DynamicDatasourceCommandPort` bean. 2. Ensure `datasource-control-plane` has a dependency on the port interface (not the concrete service). 3. Mark the old deregistration method/path as deprecated with a migration note, or remove it if it has no other callers. 4. Write an integration test (Spring context or manual wiring) that calls each of the six commands in sequence against an in-memory/test datasource and asserts correct results. 5. Verify no existing callers of the old deregistration path are broken without migration guidance.

**Constraints**: datasource-control-plane must depend only on the port interface, not on the concrete service class., The old deregistration entry point must be deprecated or removed — leaving both active would perpetuate the ambiguous path the brief is eliminating., Integration test must not require a real database — use an embedded or in-memory datasource.

## Acceptance Criteria

- [ ] DynamicDatasourceCommandService is registered in the application context as the DynamicDatasourceCommandPort bean and is injectable by datasource-control-plane.
- [ ] The old single-path deregistration entrypoint is marked deprecated or removed with a documented migration note.
- [ ] datasource-control-plane depends on DynamicDatasourceCommandPort (the interface), not on DynamicDatasourceCommandService (the implementation).
- [ ] An integration smoke test exercises all six commands (register, activate, suspend, start-drain, close, status) in sequence and asserts each returns the expected result type without errors.
- [ ] The integration test runs without a real JDBC database (embedded or test datasource).

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

