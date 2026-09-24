# Feature Specification: Discrete-Event Simulation Framework

**Feature Branch**: `001-simulation-framework`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "Formalize the existing simula discrete-event simulation framework as a spec based on the code"

## Clarifications

### Session 2026-09-23

- Q: This spec was generated from the existing simula code. What is the primary deliverable of this feature? → A: Formalize spec only (documentation; no code changes are planned for this feature).
- Q: Since the deliverable is a spec-only description of the framework, how faithfully must it record known code limitations? → A: As-is (faithful to code).

## User Scenarios & Testing *(mandatory)*

This feature is a developer-facing library: a discrete-event simulation framework. The "users" are application developers who build and run simulations on top of it. Each story below is a standalone capability a developer can adopt independently.

**Scope**: This feature formalizes the framework as a specification only. No code is produced or modified; the spec documents the framework as it exists today (as-is). Requirements and success criteria describe current behavior and are written for future reference and validation, not as work to be implemented by this feature.

### User Story 1 - Run a simulation with actors processing events (Priority: P1)

As a simulation developer, I want to create an engine that runs actors, so that my simulation steps through time and my actors process the events they subscribe to.

**Why this priority**: This is the core value of the framework; without an engine and event-driven actors there is no simulation at all.

**Independent Test**: Can be fully tested by starting an engine, registering a custom actor, signaling an event on a topic the actor subscribed to, and asserting the actor processed it.

**Acceptance Scenarios**:

1. **Given** an engine has been started, **When** an actor is registered and started, **Then** the actor runs and processes subscribed events as they are signaled.
2. **Given** an actor subscribed to a topic, **When** an event for that topic is signaled, **Then** only actors subscribed to that topic receive and process it.
3. **Given** an engine is running, **When** a stop is requested, **Then** all actors receive the stop event and the engine terminates cleanly.

---

### User Story 2 - Model time through a unique time source (Priority: P1)

As a simulation developer, I want a single time source per simulation that advances simulated seconds and signals a time event each second, so that my actors can act on elapsed time.

**Why this priority**: Time is the backbone of a discrete-event simulation and every scenario depends on it.

**Independent Test**: Can be fully tested by starting an engine and asserting that a time event is signaled and current time advances by one per simulated second.

**Acceptance Scenarios**:

1. **Given** an engine running, **When** one simulated second elapses, **Then** the current time increases by one.
2. **Given** an actor subscribed to the time event, **When** each simulated second elapses, **Then** that actor receives a time event.
3. **Given** a configurable time factor, **When** it is set to a higher value, **Then** simulated seconds expand in real duration accordingly.

---

### User Story 3 - Support delayed and prioritized event processing (Priority: P2)

As a simulation developer, I want events to be delivered as standard, delayed, or prioritized, so that I can express timing and precedence within the simulation.

**Why this priority**: Delayed and prioritized events broaden the expressiveness of simulations beyond simple broadcast, but are not required for the minimal viable engine.

**Independent Test**: Can be fully tested by posting delayed and prioritized events and asserting their processing order (delayed in reverse delay order, prioritized by priority).

**Acceptance Scenarios**:

1. **Given** a delayed event, **When** its delay has not yet elapsed, **Then** the event is not processed before the delay expires.
2. **Given** multiple prioritized events, **When** they are processed, **Then** they are processed in priority order.
3. **Given** a standard event, **When** it is signaled, **Then** it is processed in signal order.

---

### User Story 4 - Compose simulations with child engines and alarms (Priority: P3)

As a simulation developer, I want to run child engines under a parent engine and schedule alarms, so that I can structure complex simulations and trigger actions at set times or periodically.

**Why this priority**: Child engines and alarms support larger, composed simulations but are refinements on top of the core engine and time source.

**Independent Test**: Can be fully tested by adding a child engine to a parent and scheduling a one-shot and a periodic alarm, then asserting both fire as expected.

**Acceptance Scenarios**:

1. **Given** a parent engine, **When** a child engine is added, **Then** start/stop and time events propagate to the child.
2. **Given** a one-shot alarm, **When** its fire time is reached, **Then** an event on its topic is signaled and the alarm is consumed.
3. **Given** a periodic alarm, **When** each period elapses, **Then** the alarm re-arms and fires repeatedly until cleared.

---

### User Story 5 - Observe simulation activity through logging (Priority: P3)

As a simulation developer, I want each engine to expose a logger with configurable levels, so that I can trace and debug simulation activity.

**Why this priority**: Logging aids development and debugging but does not affect simulation correctness.

**Independent Test**: Can be fully tested by configuring a log level and asserting that messages at or above that level are emitted while lower-level messages are suppressed.

**Acceptance Scenarios**:

1. **Given** a logger with an active level, **When** an actor logs a message at or above that level, **Then** the message is emitted.
2. **Given** a logger with an active level, **When** an actor logs a message below that level, **Then** the message is suppressed.

---

### Edge Cases

- What happens when an actor is stopped or dies unexpectedly mid-simulation? The framework should unregister it and keep the rest of the simulation running.
- What happens when an actor stops itself? It must signal its engine to be unregistered and its thread must terminate.
- What happens when a stop event is signaled while events remain queued? Queued events should be handled in an orderly way (e.g., logger purges remaining queue).
- What happens when a child engine stops? The parent must unregister it and only terminate when no actors and no children remain.
- What happens when a delayed event has a negative delay or a priority event has a negative priority? These should be rejected as invalid.
- What happens when an alarm is requested without a period versus with a period? One-shot alarms consume on fire; periodic alarms re-arm.
- What happens when the root engine stops while child engines are still active? Stopping must cascade so the whole simulation terminates.
- How does the system handle an actor subscribed to a topic that never receives events? The actor stays alive until a stop is requested.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide an engine that registers, starts, and runs actors as independent execution units.
- **FR-002**: The system MUST allow actors to subscribe and unsubscribe to named topics and receive only the events of the topics they subscribed to.
- **FR-003**: The system MUST support signaling events that are delivered to all subscribers of the event's topic.
- **FR-004**: The system MUST provide a single time source per simulation that advances simulated time and signals a time event each simulated second.
- **FR-005**: The system MUST support a configurable time factor that expands the real duration of a simulated second.
- **FR-006**: The system MUST support three event kinds: standard (signal order), prioritized (priority order), and delayed (reverse delay order).
- **FR-007**: The system MUST reject delayed events with negative delay and prioritized events with negative priority.
- **FR-008**: The system MUST support a hierarchy of engines where a parent engine propagates start, stop, and time events to its child engines.
- **FR-009**: The system MUST support scheduling one-shot and periodic alarms that signal an event on a given topic at a set time.
- **FR-010**: The system MUST support clearing an alarm before it fires.
- **FR-011**: The system MUST provide logging with configurable levels, where messages are emitted only at or above the active level.
- **FR-012**: The system MUST unregister an actor when it stops or terminates, and keep the remaining simulation running.
- **FR-013**: The system MUST stop the whole simulation when the root engine is stopped, cascading to all child engines and actors.
- **FR-014**: Each actor MUST support custom start and stop behavior hooks invoked around the corresponding start and stop events.

### Key Entities

- **Engine**: The orchestrator that runs actors and child engines, owns a logger and (for the root) a time source, and manages topic subscriptions. Attributes: unique identity, name, current time, time factor, parent, set of child engines, set of registered actors.
- **Actor**: An independent execution unit that subscribes to topics and processes events. Attributes: unique identity, name, owning engine, delegate.
- **Event**: A fact signaled on a named topic at a given time, with optional parameters. Attributes: topic, source, time, parameters, priority (for prioritized), delay (for delayed).
- **Time Source**: The unique clock of a simulation. Attributes: current time, time factor, set of scheduled alarms.
- **Alarm**: A scheduled firing of an event at a set time, optionally periodic. Attributes: topic, time to fire, period.
- **Logger**: Per-engine component that captures and emits log messages filtered by level. Attributes: active level per actor, forced level.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can start an engine, register a custom actor, signal an event, and observe the actor process it in under 1 minute of setup using only the public contract.
- **SC-002**: A simulation with a time factor of 1 advances simulated time by exactly one per real second.
- **SC-003**: 100% of events signaled on a topic are delivered to every actor subscribed to that topic.
- **SC-004**: Delayed events are never processed before their delay elapses, and prioritized events are processed strictly in priority order.
- **SC-005**: Stopping the root engine terminates every actor and child engine; no actor remains running after termination.
- **SC-006**: 100% of configurable log levels filter messages correctly (emit at or above the active level, suppress below).
- **SC-007**: Scheduling, clearing, and firing of one-shot and periodic alarms behave deterministically for the same inputs (same inputs always produce the same result).

## Assumptions

- The framework is delivered as a developer-facing library; the "user" is the developer integrating it, not an end consumer.
- This feature is documentation-only: it formalizes the framework as-is without producing or modifying code.
- Simulated time is expressed in whole seconds; time factor determines the real duration of each simulated second.
- A single root engine owns the unique time source; child engines read time from their parent.
- Alarm fire times are expressed in real-time units; the alarm period, however, is scaled by the time factor in the current implementation (documented as-is, noting the discrepancy with the stated intent).
- Only one alarm can be scheduled per topic at a time.
- Events are delivered asynchronously through per-actor queues.
- The existing code under the version control repository is the authoritative reference for expected behavior; where this spec conflicts with the code, the code governs (as-is documentation).
- New dependencies are avoided; any future dependency must satisfy the same quality standards as existing code.
