# Contracts: Configurable Thread Execution

This feature is a developer-facing library. The contracts below describe the public API surface introduced or affected by `002-configurable-threads`. They are written as behavioral contracts (not implementation), aligned with the feature spec (FR-001..FR-013).

## ExecutionMode enum

A public enumeration selecting the actor thread execution strategy.

**Values**: `VIRTUAL`, `PLATFORM`.

**Contract**:
- `VIRTUAL` is the default when no mode is specified (FR-002).
- A caller may construct an engine with an explicit mode to override the default (FR-003).

## Engine construction with execution mode

An engine is constructed with a per-engine execution-mode parameter, fixed at creation (FR-003, clarification Q1).

**Contract**:
- Constructing an engine with an explicit `ExecutionMode` selects that mode for all actors started by it (FR-003, FR-004).
- Constructing an engine without a mode defaults to `VIRTUAL` (FR-002).
- The mode is immutable for the lifetime of the engine; it does not change for already-started actors.

**Preconditions**:
- The supplied mode must be one of the enum values; an unknown/invalid mode is rejected with a clear error (FR-007).

## Actor execution behavior

**Contract**:
- Every actor registered and started by an engine executes on a thread consistent with the engine's execution mode (FR-001, FR-004).
- Each actor thread carries a meaningful, identifiable name in both modes (FR-006).
- Observable simulation behavior (event processing, time advancement, start/stop semantics) is identical regardless of mode (FR-005, SC-003).
- Stopping the root engine terminates all actors and child engines in both modes (FR-008, SC-005).

## Concurrency / locking contract

**Contract**:
- All previously-`synchronized` operations remain mutually exclusive over the same shared state (FR-012).
- Locks are released on all paths, including exceptional paths; no deadlock or stuck state (FR-011, SC-007).
- No data race or deadlock is introduced by the locking change (SC-007).

## Runtime support

**Contract**:
- If the runtime does not support virtual threads (a Java release older than 21), an engine configured for `VIRTUAL` mode **fails clearly**: starting an actor raises `UnsupportedOperationException` from `Thread.ofVirtual()` at start time. There is **no silent fallback** to classic threads (FR-009).
- A developer who must run on a JVM without virtual threads should select `ExecutionMode.PLATFORM` explicitly.
- When the runtime supports virtual threads (the project target, Java 25), `VIRTUAL` mode works as documented (FR-001, FR-002).

## Configuration file reference

Execution mode is NOT set via config file, system property, or environment variable; it is a constructor/configuration parameter on the engine (clarification Q1, FR-003).
