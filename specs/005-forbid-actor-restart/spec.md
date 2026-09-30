# Feature Specification: Forbid Actor Restart

**Feature Branch**: `005-forbid-actor-restart`

**Created**: 2026-09-30

**Status**: Draft

**Input**: User description: "005" (Forbid restarting a stopped actor: re-registering an actor instance that was already stopped and unregistered by its engine MUST be rejected with an error log and an exception, regardless of delegate type, without unbounded memory growth)

## Clarifications

### Session 2026-09-30

- Q: Should re-registering an actor that is still registered and running (e.g. blocked in its own processing) on the same engine also be refused? → A: Yes — registering an actor currently registered on the engine is also refused with the same error log and exception.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A stopped actor cannot be brought back (Priority: P1)

A simulation developer stops an actor (the actor finishes its run loop and its engine unregisters it). Later, by mistake or by design error, the developer tries to register and start that same actor instance again. The framework refuses: the attempt is reported clearly (error log) and the developer's code is stopped with a rejection that names the actor, instead of the actor silently resuming in a half-broken state (lost subscriptions, stale queued events).

**Why this priority**: This is the core protection. Today, re-registering a stopped instance "half works" and produces confusing, hard-to-diagnose behavior; making it a loud, immediate failure removes a class of subtle bugs.

**Independent Test**: Start an engine, register and start an actor, stop it and wait for it to be unregistered, then try to register it again: the attempt must be refused with a clear error and the actor must not appear among the engine's registered actors.

**Acceptance Scenarios**:

1. **Given** an actor that was registered then stopped and unregistered, **When** the application tries to register that same instance again on the same engine, **Then** the attempt is refused with an exception naming the actor, an error is written to the log, and the engine's registered-actor set is unchanged.
2. **Given** an actor that was registered then stopped and unregistered, **When** the application tries to register-and-start that same instance again, **Then** the attempt is refused the same way and no new execution of the actor begins.
3. **Given** an engine running normally, **When** a brand-new (never-registered) actor instance is registered and started, **Then** it behaves exactly as before this feature (no regression).
4. **Given** an actor currently registered and running on the engine (including one blocked in its own processing), **When** the application tries to register that same instance again, **Then** the attempt is refused with the same exception and error log, and the actor's current execution is undisturbed.

---

### User Story 2 - Stopped status is visible (Priority: P2)

A developer can ask an actor whether it has already run to completion and stopped. This lets tools, demos, and library code detect the terminal state before attempting any use of the instance.

**Why this priority**: Valuable for diagnosis and for making the "stopped is terminal" rule self-describing, but the rejection itself (US1) delivers the primary protection on its own.

**Independent Test**: Query a fresh actor (answer: not stopped), run it to stop, query it again (answer: stopped); both answers are consistent with the actor's lifecycle.

**Acceptance Scenarios**:

1. **Given** a never-run actor, **When** its stopped status is queried, **Then** the answer is "not stopped".
2. **Given** an actor that has completed its stop, **When** its stopped status is queried, **Then** the answer is "stopped".
3. **Given** an actor whose registration was refused because it is stopped, **When** its stopped status is queried, **Then** the answer remains "stopped".

---

### User Story 3 - Custom-delegated actors are covered too (Priority: P3)

Some actors use a custom (non-standard) delegation and may not report their own stopped status. The framework still refuses to re-register a stopped instance of any actor, because the engine remembers the instances it has finished unregistering — and this memory never grows without bound: once the application itself has forgotten a stopped actor instance, remembering it stops costing anything.

**Why this priority**: Completes the guarantee for all actor styles; the standard-delegation path (US1) already covers the vast majority of cases.

**Independent Test**: Use an actor with a custom delegate that does not report stopped status; stop it; attempt re-registration; the attempt is still refused. Then drop all references to the instance; the simulation keeps running without resource growth tied to that instance.

**Acceptance Scenarios**:

1. **Given** an actor with a custom delegate that was stopped and unregistered, **When** the application tries to re-register that same instance, **Then** the attempt is refused with the same exception and error log.
2. **Given** many short-lived actors stopped over a long simulation and no longer referenced by the application, **When** the simulation continues, **Then** memory occupied by the stopped-actor bookkeeping does not keep growing with the count of such unreferenced instances.

---

### Edge Cases

- A new, never-registered actor instance whose name is identical to a stopped actor's name must be accepted (identity, not name, determines the stopped status).
- An actor still blocked inside its own event-processing code has not finished its stop, so the terminal marker is not yet set; it nevertheless stays registered on the engine until its stop completes, and any re-registration is refused as currently registered.
- Engines are never re-registered by the framework; their lifecycle is unchanged.
- A refused registration must leave no trace: the actor is not added, not subscribed, and no thread is started.
- Actors that were never registered on the engine are unaffected and can be registered normally.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST refuse to register an actor instance that has already stopped on the engine, whatever the registration entry point used.
- **FR-002**: On refusal, the system MUST raise an exception that identifies the actor, and MUST also write a corresponding error to the engine log.
- **FR-003**: A refused registration MUST leave the engine state unchanged: the actor is not registered, not subscribed to any topic, and no execution of the actor is started.
- **FR-004**: An actor that has completed its stop MUST report itself as stopped, and an actor that has not MUST report itself as not stopped.
- **FR-005**: The refusal MUST apply to every actor style, including actors whose custom delegation does not report stopped status; the reporting contract for custom delegations MUST be documented.
- **FR-006**: Remembering stopped actor instances MUST NOT prevent unreferenced stopped instances from being reclaimed, and MUST NOT cause memory growth proportional to the cumulative count of stopped actors in a long simulation.
- **FR-007**: The system MUST accept a never-registered actor instance even if another actor with the same name has previously stopped.
- **FR-008**: The system MUST refuse to register an actor that is currently registered on the engine, whatever its stopped status, with the same error log and exception behavior; the actor's current execution MUST NOT be disturbed.

### Key Entities

- **Stopped actor status**: terminal lifecycle state of an actor instance, set once when the actor's run loop completes its stop; distinct from "never registered" and from "running". Visible via the actor itself (FR-004) and enforced by the engine at registration (FR-001, FR-005).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of re-registration attempts of a stopped actor instance are refused with both an exception naming the actor and a logged error, for standard-delegated and custom-delegated actors alike.
- **SC-002**: After any refused registration, the engine's registered-actor set and topic subscriptions are byte-for-byte identical to their state before the attempt.
- **SC-003**: Running a simulation that creates and stops thousands of short-lived actors, then releasing them, results in no stopped-actor bookkeeping that continues to grow with the cumulative stop count.
- **SC-004**: All existing simulations and the existing verification suite produce identical observable outcomes (no behavioral regression), with the project's coverage gates met.
- **SC-005**: 100% of registration attempts of an actor already registered on the engine are refused with the exception and logged error, with no disturbance to the actor's ongoing execution.

## Assumptions

- Actors do not migrate between engines; a stopped instance is never legitimately re-registered on a different engine (confirmed by the project owner).
- "Stopped" means the actor's run loop has completed and the engine has unregistered it; an actor blocked in its own processing code is not yet stopped, and its re-registration is refused as currently registered (FR-008).
- The terminal-state rule replaces the current undocumented "manual restart" workaround (re-register, resubscribe, re-signal start), which produced only partial behavior; no supported restart path is introduced by this feature.
- Logging facilities and the engine's existing registration entry points are reused; no new dependency is introduced.
