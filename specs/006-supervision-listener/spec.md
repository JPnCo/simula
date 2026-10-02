# Feature Specification: Supervision Listener

**Feature Branch**: `006-supervision-listener`

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: "Notify a registered listener synchronously each time the SimulaSupervisor records a status transition for a component (actor start, actor stop, engine stop), with add/remove listener APIs"

## Clarifications

### Session 2026-10-02

- Q: Forme de l'interface listener ? → A: Un seul callback `statusChanged(component, previous, current)` (choix du propriétaire), plutôt que plusieurs callbacks typés.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Observe status transitions as they happen (Priority: P1)

A supervision tool (dashboards, recorders, alerters) needs to react the moment the `SimulaSupervisor` observes a component's lifecycle transition, instead of polling `getStates()`. The tool registers a listener on the supervisor; from then on, every recorded transition — an actor starting, an actor stopping, a child engine stopping — invokes the listener with the component, its previous status and its new status.

**Why this priority**: This is the core value: push-based observation of the supervision state. Without it, consumers must poll and can miss intermediate transitions.

**Independent Test**: Create a supervisor, attach a listener, feed it lifecycle events (start then stop of an actor; stop of a child engine); the listener receives exactly the sequence `(null → STARTED)`, `(STARTED → STOPPED)` with the right components.

**Acceptance Scenarios**:

1. **Given** a supervisor with a registered listener, **When** a component's first lifecycle event is recorded, **Then** the listener is called once with `previous = null` and `current` equal to the recorded status.
2. **Given** a listener and a component already recorded as `STARTED`, **When** the component's stop is recorded, **Then** the listener is called once with `previous = STARTED` and `current = STOPPED`.
3. **Given** a listener, **When** an event for a component whose recorded status would not change is processed, **Then** the listener is NOT called for that event.
4. **Given** no listener is registered, **When** any lifecycle event is processed, **Then** the supervisor behaves exactly as before this feature (no regression).

---

### User Story 2 - Register and unregister listeners safely (Priority: P2)

The supervision tool can attach and detach listeners at any moment, including while the simulation is running and while a notification is in flight. Several listeners can be attached at once; each receives every transition notification. A listener that misbehaves (throws) must not corrupt the recorded state nor prevent the other listeners from being notified.

**Why this priority**: The supervisor's `process()` is callable from any thread (documented external contract FR-007 of the supervision feature); listener management must be safe under that contract, and fault isolation is what makes the extension production-usable.

**Independent Test**: Attach two listeners; make the first throw on notification; feed events; the state map is still recorded and the second listener is still notified. Register and unregister while producers hammer `process()`; no exception, and a removed listener is no longer called afterwards.

**Acceptance Scenarios**:

1. **Given** two registered listeners, **When** a transition is recorded, **Then** both are notified with the same arguments.
2. **Given** two registered listeners where the first throws on notification, **When** a transition is recorded, **Then** the status is recorded, the error is logged, and the second listener is still notified.
3. **Given** a registered listener, **When** it is unregistered, **Then** it receives no notification for any transition recorded after the unregistration completed.
4. **Given** a supervisor under concurrent `process()` calls, **When** listeners are added and removed concurrently, **Then** no exception is raised and the recorded state stays consistent.

---

### User Story 3 - API is hard to misuse (Priority: P3)

A developer wiring a listener gets clear, immediate feedback on programming errors: a `null` listener is rejected at registration time, and registering the same listener instance twice is a no-op rather than a double notification.

**Why this priority**: Quality-of-use; prevents the most common listener bugs (duplicate registration, null).

**Independent Test**: `addSupervisionListener(null)` and `removeSupervisionListener(null)` raise `NullPointerException`; adding the same listener instance twice results in a single notification per transition; removing a listener that was never added is a silent no-op.

**Acceptance Scenarios**:

1. **Given** a supervisor, **When** `null` is passed to add or remove, **Then** a `NullPointerException` is raised and the listener list is unchanged.
2. **Given** a listener already registered, **When** the same instance is registered again, **Then** the registration is ignored and the listener receives exactly one notification per transition.
3. **Given** a listener never registered, **When** it is unregistered, **Then** nothing happens and no error is raised.

---

### Edge Cases

- Notifications are synchronous in the thread that records the transition (which may be any thread under the documented `process()` contract); a slow listener delays the notifying thread — this is documented, not prevented.
- A listener that unregisters itself (or another listener) from within its callback must not raise `ConcurrentModificationException`; whether the concurrently-removed listener still receives the notification in flight is unspecified.
- A listener that throws is isolated per notification: the exception is caught and logged, the recording and the other listeners are unaffected; a throwing listener keeps being notified on later transitions unless removed.
- The first ever record for a component is a transition from `null`; there is no "initial dump" replay of already-recorded states at registration time.
- The supervisor never signals events itself (existing FR of the supervision feature); notifying listeners is not signaling.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The supervisor MUST expose an API to register and unregister a supervision listener; registering a `null` listener MUST raise a `NullPointerException`, and registering the same listener instance twice MUST be a no-op.
- **FR-002**: The listener contract MUST be a single callback receiving the component, its previous status (`null` when the component had never been recorded) and its new status.
- **FR-003**: Whenever the recorded status of a component actually changes, the supervisor MUST notify every registered listener synchronously in the thread performing the record, after the map update.
- **FR-004**: The supervisor MUST NOT notify listeners when processing an event that does not change any recorded status.
- **FR-005**: An exception thrown by a listener MUST be caught and logged as an error by the supervisor; it MUST NOT prevent the status from being recorded nor any other listener from being notified.
- **FR-006**: Registering and unregistering listeners MUST be safe concurrently with `process()` calls and with notifications in flight (no exception, no lost recording).
- **FR-007**: Unregistering a listener that is not registered MUST be a silent no-op; unregistering a registered listener MUST take effect for every subsequent record.
- **FR-008**: The behavior of the supervisor without any registered listener MUST be identical to its behavior before this feature.

### Key Entities

- **Supervision listener**: observer callback `statusChanged(component, previous, current)` registered on a `SimulaSupervisor`; instances are identified by identity for registration purposes.
- **Status transition**: a change of the recorded `Status` of a component, from `null` or a previous status to a different new status; the unit of notification (FR-003, FR-004).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every recorded transition, each registered listener is notified exactly once with the correct component, previous and current status; for every no-change event, zero notification.
- **SC-002**: A listener throwing on every notification never corrupts the recorded state, never blocks another listener, and leaves a logged error per notification attempt.
- **SC-003**: A stress scenario mixing `process()` calls, listener registrations/unregistrations and state reads completes without exception and with the expected recorded count.
- **SC-004**: The existing verification suite and the supervision feature's scenarios produce identical observable outcomes when no listener is registered; project coverage gates are met.

## Assumptions

- Notification is synchronous, in the recording thread; no asynchronous dispatch, no ordering guarantee across threads (the multi-threaded `process()` contract of the supervision feature already provides no ordering guarantee for the map).
- Listeners are expected to be thread-safe and to never block for long; this is documented in the listener's contract, not enforced.
- Identity, not `equals`, distinguishes listener registrations in practice (documents: registering two `equals`-but-distinct instances may both be kept; the no-duplicate rule (FR-001) is stated in terms of the same instance).
- No listener is persisted or replayed: attaching a listener late only yields future transitions.
