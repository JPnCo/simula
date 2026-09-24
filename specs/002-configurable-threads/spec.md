# Feature Specification: Configurable Thread Execution

**Feature Branch**: `002-configurable-threads`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "simula doit pouvoir fonctionner avec des threads classiques ou des threads virtuels (paramètre de configuration)"

## Clarifications

### Session 2026-09-23

- Q: How should a developer select the execution mode (virtual vs classic) for an engine? → A: Engine constructor/configuration parameter (per-engine, fixed at creation).
- Q: Should the virtual-thread support and the synchronized→ReentrantLock replacement be applied across the whole framework or a subset? → A: Whole framework (all synchronized usages in EngineImpl + IdBuilder, and all actor thread creation).
- Q: Does this code-changing feature need to satisfy the constitution's 97% line and branch coverage gate as part of its definition of done? → A: Yes, full 97% line and branch coverage.

## User Scenarios & Testing *(mandatory)*

This feature is a capability of the developer-facing simulation framework. The "users" are simulation developers who configure how their actors are executed. Each story below is a standalone capability a developer can adopt independently.

### User Story 1 - Run actors on virtual threads by default (Priority: P1)

As a simulation developer, I want actors to run on virtual threads by default when no execution preference is given, so that simulations scale to a large number of actors without exhausting platform resources.

**Why this priority**: Virtual-thread-by-default is the requested behavior and the primary value of the feature; it is what a developer gets out of the box.

**Independent Test**: Can be fully tested by starting an engine with the default configuration and asserting actors run on virtual threads and process events correctly.

**Acceptance Scenarios**:

1. **Given** an engine started without specifying an execution mode, **When** actors are registered and started, **Then** they run on virtual threads and process events.
2. **Given** the default configuration, **When** a simulation runs to completion, **Then** actors run on virtual threads and the simulation produces the expected outcomes.

---

### User Story 2 - Run actors on classic threads via configuration (Priority: P1)

As a simulation developer, I want to select classic (platform) thread execution through a configuration parameter, so that I can opt out of virtual threads when needed.

**Why this priority**: The configuration parameter is the requested mechanism; choosing classic threads provides an explicit escape hatch from the virtual-thread default.

**Independent Test**: Can be fully tested by starting an engine with classic-thread mode configured and asserting that actors run and process events correctly under that mode.

**Acceptance Scenarios**:

1. **Given** an engine configured for classic-thread execution, **When** actors are registered and started, **Then** they run on classic threads and process subscribed events.
2. **Given** classic-thread mode, **When** a simulation runs to completion, **Then** results match the expected outcomes for the same simulation.

---

### User Story 3 - Keep behavior identical across execution modes (Priority: P2)

As a simulation developer, I want the same simulation to produce the same outcome regardless of the chosen thread mode, so that switching between classic and virtual threads does not change simulation results.

**Why this priority**: Behavioral equivalence is what makes the mode a safe, transparent configuration rather than a fork in functionality.

**Independent Test**: Can be fully tested by running the same scenario under both classic and virtual modes and asserting the observable outcomes are equivalent.

**Acceptance Scenarios**:

1. **Given** an identical simulation scenario, **When** run under classic threads and then under virtual threads, **Then** the observable results are equivalent.
2. **Given** either mode, **When** start, stop, and event-processing events occur, **Then** they are handled with the same semantics.

---

### Edge Cases

- What happens when the execution mode is changed after an engine has already started? The mode should apply at actor-start time; behavior for mid-run changes should be clearly defined (e.g., mode is fixed when the engine is created).
- What happens if the platform does not support virtual threads? The framework should fail clearly or fall back to classic threads with a documented behavior.
- What happens when a large number of actors run under virtual-thread mode? Virtual threads should allow a higher count without exhausting OS threads.
- How does the system handle thread naming so logs remain identifiable? Each actor thread should still carry a meaningful name in both modes.
- What happens to stop/termination semantics in virtual-thread mode? Stopping the root engine must still terminate every actor and child engine regardless of mode.
- What happens when an invalid or unknown execution-mode value is configured? It should be rejected with a clear error rather than silently ignored.
- What happens if a thread fails while holding a lock during the locking change? Every lock must be released in a finally-equivalent path so no deadlock or stuck state remains.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support two actor execution modes: classic (platform) threads and virtual threads.
- **FR-002**: The system MUST default to virtual-thread execution when no mode is configured.
- **FR-003**: The system MUST allow the execution mode to be selected through a per-engine constructor/configuration parameter, fixed when the engine is created.
- **FR-004**: The system MUST apply the selected execution mode when starting each actor.
- **FR-005**: The system MUST preserve identical observable simulation behavior (event processing, time, start/stop semantics) regardless of the chosen execution mode.
- **FR-006**: The system MUST assign each actor thread a meaningful, identifiable name in both execution modes.
- **FR-007**: The system MUST reject an unknown or invalid execution-mode configuration value with a clear error.
- **FR-008**: The system MUST ensure that stopping the root engine terminates all actors and child engines in both execution modes.
- **FR-009**: The system MUST clearly handle the case where virtual-thread execution is unsupported by the runtime, either by a documented fallback to classic threads or a clear error.
- **FR-010**: The system MUST replace all `synchronized` methods and blocks with explicit reentrant locks across the whole framework (all `synchronized` usages in the engine implementation and the id builder).
- **FR-011**: The system MUST guarantee that every lock acquired is released, including on exceptional paths.
- **FR-012**: The system MUST preserve the thread-safety guarantees (mutual exclusion over shared state such as registered actors, child engines, and subscriptions) that the replaced `synchronized` blocks provided.
- **FR-013**: The system MUST maintain identical observable behavior after the locking change; no functional regression is allowed.

### Key Entities

- **Engine**: Owns the actor-execution configuration and uses it to start each registered actor. Attributes: unique identity, name, execution mode.
- **Execution Mode**: A configuration value selecting classic (platform) threads or virtual threads for actor execution. Attributes: mode identifier, default value.
- **Actor**: An independent execution unit started by the engine under the selected execution mode. Attributes: unique identity, name, owning engine.
- **Lock**: An explicit reentrant mutual-exclusion mechanism used to guard shared engine state, replacing intrinsic `synchronized` monitors. Attributes: reentrant, explicitly released.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can select the execution mode (virtual or classic) via configuration and observe actors running under that mode, with setup in under 5 minutes using the public contract.
- **SC-002**: 100% of simulations run on virtual threads under the default configuration.
- **SC-003**: For an identical scenario, the observable outcomes under classic and virtual modes are equivalent in 100% of cases.
- **SC-004**: An invalid execution-mode value is rejected with a clear error 100% of the time.
- **SC-005**: Stopping the root engine terminates every actor and child engine in both modes; no actor remains running after termination in either mode.
- **SC-006**: A simulation using virtual-thread mode can start and stop successfully with a high number of actors without exhausting platform resources.
- **SC-007**: After the locking change, no shared-state data race or deadlock occurs; all previously `synchronized` operations remain mutually exclusive and correct.
- **SC-008**: The delivered code achieves at least 97% line coverage and at least 97% branch coverage, measured independently.

## Assumptions

- The framework is a developer-facing library; the "user" is the developer configuring and running simulations.
- The runtime used by the framework supports virtual threads (the project targets a Java version that provides them).
- The execution mode is a per-engine configuration passed via the engine constructor, fixed when the engine is created; it does not change for actors already started.
- Virtual threads are the default execution mode; classic (platform) threads are selected explicitly via configuration when required.
- Behavior equivalence across modes is the goal; minor scheduling/timing differences inherent to thread types are acceptable as long as observable results match.
- Replacing `synchronized` blocks with reentrant locks is part of this feature's scope and must not alter observable behavior or weaken thread-safety guarantees.
- Both the virtual-thread/classic execution mode and the `synchronized`→`ReentrantLock` replacement apply across the whole framework, not just a single component.
- This feature includes code changes to the framework (unlike the documentation-only simulation-framework feature) and is subject to the project constitution (test-first, 97% line and branch coverage, English code/comments, named literals, Javadoc citing FR/SC identifiers).
