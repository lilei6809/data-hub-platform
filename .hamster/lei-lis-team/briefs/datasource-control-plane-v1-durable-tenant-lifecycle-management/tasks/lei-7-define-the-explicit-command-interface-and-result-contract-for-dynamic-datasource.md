---
id: "6a275e4a-a45e-4471-9ce3-5847b1844858"
entity_type: "task"
entity_id: "33a33f40-3af4-452c-b6ca-1a51ac6e7837"
title: "Define the explicit command interface and result contract for dynamic-datasource - Notes"
status: "todo"
priority: "high"
display_id: "LEI-7"
parent_task_id: "a1318d26-212f-4e43-978d-abe515479cdc"
brief_id: "828bbf1d-5fa1-47c1-9062-4c56769c42e1"
updated_at: "2026-03-27T11:42:57.288576+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

Define the explicit command interface and result contract that `dynamic-datasource` exposes to the control plane, covering the six lifecycle operations: `register`, `activate`, `suspend`, `start-drain`, `close`, and `status`.

## Implementation Approach

1. Review the brief's six target operations and document the expected pre/post conditions for each.
2. Define typed input record/value types per operation (e.g., `RegisterDatasourceCommand`, `TenantId`).
3. Define a sealed/discriminated result type covering success and key error cases: `NotFound`, `AlreadyInState`, `InvalidTransition`.
4. Define the `DatasourceStatusView` response value type for the `status` operation.
5. Codify all of the above in a primary port interface in `dynamic-datasource` (e.g., `DynamicDatasourceCommandPort`).
6. Add interface-level documentation (Javadoc/KDoc) for each method describing observable semantics.

## Acceptance Criteria

- An interface declares all six operations: `register`, `activate`, `suspend`, `start-drain`, `close`, and `status`.
- Each operation has typed inputs and a typed result distinguishing success from at least three error kinds: `NotFound`, `AlreadyInState`, and `InvalidTransition`.
- The `status` operation returns a value type with current runtime state and sufficient metadata for control-plane routing decisions.
- The interface is documented such that no operation's semantics are ambiguous.
- No implementation logic exists in this artifact — it is a pure contract definition.

## Technical Constraints

- Interface must be decoupled from any HTTP or messaging transport (pure in-process command port).
- Result types must be non-throwing for expected error cases; exceptions reserved for unexpected infrastructure failures.
- Must not leak internal pool/connection details through the interface.

## Relevant Files

### Files to Create

- `dynamic-datasource/src/main/…/DynamicDatasourceCommandPort.java` - Primary command port interface with all six operations
- `dynamic-datasource/src/main/…/command/RegisterDatasourceCommand.java` - Typed input for register
- `dynamic-datasource/src/main/…/command/DatasourceCommandResult.java` - Sealed result type covering success and error kinds
- `dynamic-datasource/src/main/…/query/DatasourceStatusView.java` - Value type returned by the status operation## Details

**Scope**: Define the DynamicDatasourceCommands interface (or equivalent) with typed method signatures for register, activate, suspend, start-drain, close, and status. Define result/response types that encode success, error kinds (not-found, invalid-transition, already-in-state), and any metadata the caller needs. Document the observable contract for each command.

**Out of Scope**: Concrete implementation of command logic, internal pool management, state machine transitions (sibling task), persistence, or orchestration wiring.

**Implementation**: 1. Review the brief's six target operations and document the expected pre/post conditions for each. 2. Define typed input record/value types per operation. 3. Define a sealed/discriminated result type covering success and the key error cases. 4. Define the status response value type. 5. Codify the interface in the module's primary contract file (e.g., a port interface or an abstract class in `dynamic-datasource`). 6. Add interface-level documentation (Javadoc/KDoc or equivalent) for each method.

**Constraints**: Interface must be decoupled from any HTTP or messaging transport — it is a pure in-process command port., Result types must be non-throwing for expected error cases (invalid-transition, not-found) — exceptions are reserved for unexpected infrastructure failures., Must not leak internal pool/connection implementation details through the interface.

## Acceptance Criteria

- [ ] An interface (or equivalent abstraction) named DynamicDatasourceCommandPort (or agreed name) declares all six operations: register, activate, suspend, start-drain, close, and status.
- [ ] Each operation has typed input parameters (e.g., tenantId, datasource configuration) and a typed result that distinguishes success from at-least three error kinds: not-found, already-in-state, and invalid-transition.
- [ ] The status operation returns a value type that includes the current runtime state and enough metadata for the control plane to make routing decisions.
- [ ] The interface is documented such that no operation's semantics are ambiguous — callers know what side effects to expect and what result to handle.
- [ ] No implementation logic exists in this artifact; it is a pure contract definition.

## Context

| Field | Value |
|-------|-------|
| category | design |
| complexity | 4 |

