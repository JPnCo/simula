# Feature Specification: Add Supervision Actor

**Feature Branch**: `004-supervision-actor`

**Created**: 2026-09-29

**Status**: Draft

**Input**: User description: "l'objectif est de créer un acteur de supervision de simula. Cet acteur doit surveiller le démarrage et l'arrêt des autres acteurs et des fils éventuels du moteur."

## Clarifications

### Session 2026-09-29

- Q: How should the supervisor observe the startup of each individual actor? → A: Introduce a dedicated per-actor startup signal (`STARTED_ACTOR_EVENT`), symmetric to the existing `STOPPED_ACTOR_EVENT`, so the supervisor can observe each actor starting.
- Q: Does the supervisor track the low-level threads of the engine? → A: No. Supervision is limited to actors and engines (whose lifecycle reflects their threads); low-level thread state is out of scope.
- Q: What does the supervisor do with the observed information? → A: It maintains a queryable state of actors/engines (started vs stopped) and logs the lifecycle events it observes.
- Q: Who is responsible for emitting the per-actor startup signal? → A: The built-in actors (via their standard delegation) and the engine and time source emit it for themselves. Actors created by external projects that do not use the standard delegation are not forced to emit it; the contract (how and when to emit) is documented instead.

## User Scenarios & Testing *(mandatory)*

This feature is a capability of the developer-facing simulation framework. The "users" are simulation developers who want to observe the lifecycle of the actors and engines running in a simulation.

### User Story 1 - Observe when each actor starts (Priority: P1)

As a simulation developer, I want a supervision actor to be notified when each actor starts, so that I can know the set of actors that are running at any moment.

**Why this priority**: Per-actor startup awareness is the primary value of the feature; it is what a developer gets out of the box.

**Independent Test**: Can be fully tested by starting an engine with several actors and a supervision actor, then asserting the supervision actor is notified of each actor's startup.

**Acceptance Scenarios**:

1. **Given** a running simulation with a supervision actor and one other actor, **When** that other actor starts, **Then** the supervision actor receives a notification identifying the started actor.
2. **Given** a simulation with several actors, **When** each actor starts, **Then** the supervision actor receives a notification for each one.
3. **Given** a supervision actor, **When** it subscribes to startup notifications, **Then** it receives them without disturbing the behavior of the observed actors.

---

### User Story 2 - Observe when actors and engines stop (Priority: P1)

As a simulation developer, I want a supervision actor to be notified when each actor and child engine stops, so that I can know when components have terminated.

**Why this priority**: Stopping awareness is essential for supervision and uses the framework's existing stop notifications.

**Independent Test**: Can be fully tested by stopping an engine with actors and child engines, then asserting the supervision actor is notified of each component's stop.

**Acceptance Scenarios**:

1. **Given** a running simulation with actors, **When** an actor stops, **Then** the supervision actor receives a notification identifying the stopped actor.
2. **Given** an engine with a child engine, **When** the child engine stops, **Then** the supervision actor receives a notification identifying the stopped child engine.

---

### User Story 3 - Maintain a queryable supervision state (Priority: P2)

As a simulation developer, I want the supervision actor to keep a queryable state of the actors and engines it has observed, so that I can inspect the current and historical lifecycle at any time.

**Why this priority**: A queryable state adds inspection value on top of the raw notifications and the logging.

**Independent Test**: Can be fully tested by running a scenario and asserting the supervision actor's reported state (started/stopped components) is consistent with what happened.

**Acceptance Scenarios**:

1. **Given** a supervision actor that has observed starts and stops, **When** its state is queried, **Then** it reports each observed component and its lifecycle status.
2. **Given** a supervision actor, **When** lifecycle events occur, **Then** the reported state is always consistent with the sequence of notifications received.

---

### User Story 4 - Provide a documented contract for external actors (Priority: P3)

As a developer of an actor in an external project that does not use the framework's standard delegation, I want clear documentation of how and when to emit the per-actor startup signal, so that my actor can participate in supervision without being forced to.

**Why this priority**: The framework cannot force external actors to emit the signal; documentation is the practical way to extend coverage to them.

**Independent Test**: Can be fully tested by reviewing the documentation for a clear, actionable description of how and when to emit the startup signal.

**Acceptance Scenarios**:

1. **Given** an actor that does not use the standard delegation, **When** its developer reads the documentation, **Then** it explains clearly how and when to emit the per-actor startup signal.
2. **Given** an external actor that follows the documented contract, **When** it emits the startup signal, **Then** the supervision actor observes it like any built-in actor.

---

### Edge Cases

- What happens if an actor stops without having been observed starting? The supervision actor should still record the stop and mark the component as stopped.
- What happens if the same actor signals ready multiple times? Each startup notification should be recorded distinctly so the state reflects the actual sequence.
- What happens when the supervision actor itself is stopped? It should stop observing cleanly, like any other actor, without affecting the rest of the simulation.
- What happens if a child engine stops while its parent continues? The supervision actor records the child engine stop independently.
- What happens if an actor from an external project does not emit the startup signal? The supervision actor does not fail; it simply records only the events that are actually emitted (documented behavior).
- What happens if a component is observed starting but never stopping? Its state remains "started" for the lifetime of the simulation.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a per-actor startup notification that identifies the actor that has started, emitted when an actor begins its behavior.
- **FR-002**: The system MUST provide a supervision actor that subscribes to lifecycle notifications (start and stop) of actors and engines.
- **FR-003**: The system MUST notify the supervision actor of each actor's stop, reusing the framework's existing stop notifications.
- **FR-004**: The system MUST notify the supervision actor of each child engine's stop, reusing the framework's existing engine-stop notifications.
- **FR-005**: The system MUST have the built-in actors (via standard delegation), the engine, and the time source emit the per-actor startup notification for themselves.
- **FR-006**: The system MUST maintain a queryable supervision state that reports each observed component and its lifecycle status.
- **FR-007**: The system MUST document how and when an actor that does not use standard delegation can emit the per-actor startup notification.
- **FR-008**: The system MUST NOT change the observable behavior of the observed actors or engines as a result of being supervised.
- **FR-009**: The system MUST keep the supervision state consistent with the sequence of lifecycle notifications received.
- **FR-010**: The system MUST handle stop notifications for components that were not observed starting, without error.
- **FR-011**: The system MUST expose the child engines of an engine as a non-modifiable, ordered collection.
- **FR-012**: The system MUST expose the actors of an engine as a non-modifiable, ordered collection.

### Key Entities

- **Supervision Actor**: An actor that subscribes to lifecycle notifications, records them, and exposes a queryable state. Attributes: unique identity, set of observed components.
- **Startup Notification**: A notification emitted when an actor starts, identifying the actor. Attribute: source actor.
- **Stop Notification**: A notification emitted when an actor or engine stops, identifying the component. Attribute: source component.
- **Supervision State**: The recorded lifecycle information for observed components. Attributes: component identity, lifecycle status (started/stopped).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can add a supervision actor to a simulation and observe the startup of every built-in actor, with setup in under 5 minutes using the public contract.
- **SC-002**: 100% of built-in actors that start emit a startup notification that the supervision actor receives.
- **SC-003**: 100% of actor and child-engine stops are observed by the supervision actor.
- **SC-004**: The supervision state always matches the sequence of notifications received, in 100% of cases.
- **SC-005**: The presence of the supervision actor does not change the observable outcomes of the observed simulation.
- **SC-006**: The delivered code achieves at least 97% line coverage and at least 97% branch coverage, measured independently.
- **SC-007**: The exposed child-engine collection is non-modifiable in 100% of cases and reflects the added children in the order they were added.
- **SC-008**: The exposed actor collection is non-modifiable in 100% of cases and reflects the registered actors.

## Assumptions

- The framework is a developer-facing library; the "user" is the developer configuring and running simulations.
- The supervision actor is a standard actor in the `jpnco.simula.actors` package, created with an engine reference and registered/started through the engine, like the existing `Logger` and `Barrier` actors.
- The supervision actor uses the standard delegation pattern provided by the framework for its event loop, and only its event-processing implements the recording and state logic.
- A new per-actor startup notification is introduced, symmetric to the existing per-actor stop notification.
- The built-in actors, the engine, and the time source emit the per-actor startup notification for themselves.
- Actors created by external projects that do not use standard delegation are not forced to emit the notification; the contract is documented instead of enforced.
- Low-level thread state is out of scope; supervision covers actors and engines whose lifecycle reflects their threads.
- This feature includes code changes to the framework and is subject to the project constitution (test-first, 97% line and branch coverage, English code/comments, named literals, Javadoc citing FR/SC identifiers).
