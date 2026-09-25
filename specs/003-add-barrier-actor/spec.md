# Feature Specification: Add Barrier Actor

**Feature Branch**: `003-add-barrier-actor`

**Created**: 2026-09-25

**Status**: Draft

**Input**: User description: "Ajouter un acteur BARRIER dans simula. Cet acteur est créé en lui fournissant 5 paramètres : un nombre de participants, un topic pour signaler qu'un acteur est prêt vis-à-vis de cette barrière, un topic permettant au créateur d'être averti quand le nombre d'acteurs prêts est égal au nombre de participants, le mode 'à usage unique' ou cyclique, et un booléen indiquant si les participants prêts sont tous différents (si un acteur se dit prêt plusieurs fois, cela ne compte que pour 1)."

## Clarifications

### Session 2026-09-25

- Q: Is the actor that posted on a topic known to the one that processes the event? A: Yes, the event's source (`event.getSource()`) is the actor that signaled it; the Barrier can therefore distinguish participants when `distinct` is enabled.
- Q: What are the exact parameters 4 and 5? A: Parameter 4 is the mode (single-use vs cyclic) represented by an enum `BarrierMode { SINGLE_USE, CYCLIC }`. Parameter 5 is a boolean `distinct`; when true, a participant that declares itself ready several times counts only once.
- Q: In single-use mode, what does the Barrier do after firing? A: It unsubscribes from the ready topic and no longer reacts (the actor becomes inactive).
- Q: In cyclic mode, how does the counter behave after firing? A: Total reset: the counter (and the set of distinct sources when `distinct` is true) is cleared so the barrier can fire again on the next cycle.

## User Scenarios & Testing *(mandatory)*

This feature is a capability of the developer-facing simulation framework. The "users" are simulation developers who coordinate a set of actors before proceeding. Each story below is a standalone capability a developer can adopt independently.

### User Story 1 - Notify the creator when all participants are ready (Priority: P1)

As a simulation developer, I want the Barrier to signal the creator as soon as a configured number of participants have declared themselves ready, so that I can synchronize the start of a phase on a group of actors.

**Why this priority**: Firing when the participant count is reached is the primary value of the feature; it is what a developer gets out of the box.

**Independent Test**: Can be fully tested by creating a Barrier, having `N` actors signal ready on the ready topic, and asserting the creator receives the complete event.

**Acceptance Scenarios**:

1. **Given** a Barrier with `participants = N`, **When** `N` distinct actors signal ready on the ready topic, **Then** the Barrier signals the complete topic.
2. **Given** a Barrier with `participants = N`, **When** fewer than `N` actors have signaled ready, **Then** the complete topic is not signaled.
3. **Given** a Barrier, **When** it is created, **Then** it subscribes to the ready topic and is ready to count.

---

### User Story 2 - Single-use barrier deactivates after firing (Priority: P1)

As a simulation developer, I want a single-use Barrier to stop reacting once it has fired, so that it is not triggered again accidentally in the same run.

**Why this priority**: Single-use is a distinct, requested behavior; deactivating after firing prevents spurious re-fires and defines a clear lifecycle.

**Independent Test**: Can be fully tested by a Barrier in single-use mode firing once, then sending more ready events and asserting the complete topic is not signaled again.

**Acceptance Scenarios**:

1. **Given** a single-use Barrier, **When** it fires, **Then** it unsubscribes from the ready topic.
2. **Given** a single-use Barrier that has already fired, **When** further ready events arrive, **Then** they are ignored and the complete topic is not signaled again.

---

### User Story 3 - Cyclic barrier resets and can fire again (Priority: P2)

As a simulation developer, I want a cyclic Barrier to reset after firing so that it can coordinate successive phases (cycles) of the same set of actors.

**Why this priority**: Cyclic behavior extends the value of the barrier to repeated synchronization points while keeping the same actor alive.

**Independent Test**: Can be fully tested by a cyclic Barrier firing once, then having the participants signal ready again and asserting the complete topic fires a second time.

**Acceptance Scenarios**:

1. **Given** a cyclic Barrier, **When** it fires, **Then** its counter (and distinct-source set when `distinct`) is reset.
2. **Given** a cyclic Barrier that has fired once, **When** the participants signal ready again, **Then** the complete topic is signaled again.

---

### User Story 4 - Distinct participants are counted only once (Priority: P2)

As a simulation developer, I want to optionally count each participant only once even if it declares itself ready several times, so that a single participant cannot satisfy the barrier alone by flooding ready events.

**Why this priority**: Distinct counting is an explicit, requested configuration; it defines fairness of the barrier when the same source may report more than once.

**Independent Test**: Can be fully tested with `distinct = true` by having a single actor signal ready multiple times and asserting the barrier does not fire (participant count stays at 1).

**Acceptance Scenarios**:

1. **Given** a Barrier with `distinct = true` and `participants > 1`, **When** the same actor signals ready several times, **Then** it counts only once and the barrier does not fire.
2. **Given** a Barrier with `distinct = false` and `participants = N`, **When** one actor signals ready `N` times, **Then** the barrier fires (each signal counts).

---

### Edge Cases

- What happens when `participants` is zero or negative? It should be rejected with a clear error at construction.
- What happens when the ready or complete topic is null or blank? It should be rejected with a clear error at construction.
- What happens when the mode or distinct argument is null? It should be rejected with a clear error at construction.
- What happens in single-use mode if more than `participants` signals arrive before firing? Only the configured count is observed; the barrier fires once and then deactivates.
- What happens in cyclic mode with `distinct = true` when not all participants re-signal in the next cycle? The barrier fires only once the distinct set is refilled for that cycle.
- What happens when a participant signals ready after the barrier has fired in single-use mode? The event is ignored because the barrier has unsubscribed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a Barrier actor that can be constructed with exactly five parameters: a participant count, a ready topic, a complete topic, a mode, and a distinct flag.
- **FR-002**: The system MUST have the Barrier subscribe to the ready topic so that it receives ready events signaled by participants.
- **FR-003**: The system MUST count each ready event received on the ready topic, respecting the distinct flag.
- **FR-004**: The system MUST signal the complete topic when the counted ready participants reach the configured participant count.
- **FR-005**: The system MUST, in single-use mode, unsubscribe from the ready topic after firing so that it does not fire again.
- **FR-006**: The system MUST, in cyclic mode, reset the counter (and the distinct-source set when distinct) after firing so that it can fire again on a subsequent cycle.
- **FR-007**: The system MUST, when the distinct flag is true, count each distinct source (the event's source actor) only once even if it signals ready multiple times.
- **FR-008**: The system MUST reject an invalid or unknown BarrierMode value with a clear error.
- **FR-009**: The system MUST reject invalid construction arguments (non-positive participant count, null/blank topics, null mode, null distinct) with a clear error.
- **FR-010**: The system MUST expose the ready source to the Barrier through the event's source so that distinct counting is possible.

### Key Entities

- **Barrier**: An actor that coordinates a set of participants by counting ready signals and notifying the creator when the participant count is reached. Attributes: unique identity, participant count, ready topic, complete topic, mode, distinct flag, current count.
- **BarrierMode**: A configuration value selecting single-use or cyclic behavior. Attributes: mode identifier (SINGLE_USE or CYCLIC).
- **Participant**: An actor that signals readiness by signaling an event on the ready topic. Identified by the event's source.
- **Creator**: The actor that builds the Barrier and subscribes to the complete topic to be notified when all participants are ready.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can create a Barrier with the five required parameters and observe it firing the complete topic when the participant count is reached, with setup in under 5 minutes using the public contract.
- **SC-002**: For an identical scenario, a single-use Barrier fires the complete topic exactly once.
- **SC-003**: For an identical scenario, a cyclic Barrier fires the complete topic once per completed cycle, with the counter reset between cycles.
- **SC-004**: With `distinct = true`, a single source that signals ready multiple times increments the count only once, in 100% of cases.
- **SC-005**: An invalid construction argument or an invalid BarrierMode value is rejected with a clear error 100% of the time.
- **SC-006**: The delivered code achieves at least 97% line coverage and at least 97% branch coverage, measured independently.

## Assumptions

- The framework is a developer-facing library; the "user" is the developer configuring and running simulations.
- The Barrier is a standard actor in the `jpnco.simula.actors` package, created by a developer with an engine reference and registered/started through the engine, like the existing `Logger` actor.
- The Barrier uses the standard delegation pattern provided by `ActorDelegate` for its event loop, and only its `process(Event)` method implements the counting logic.
- The creator of the Barrier subscribes to the complete topic to be notified when all participants are ready.
- The event's source identifies the participant that signaled ready, enabling distinct counting.
- A BarrierMode enum with values `SINGLE_USE` and `CYCLIC` is used (consistent with the existing `ExecutionMode` enum convention), fixed at construction.
- This feature includes code changes to the framework and is subject to the project constitution (test-first, 97% line and branch coverage, English code/comments, named literals, Javadoc citing FR/SC identifiers).
