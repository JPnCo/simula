---

description: "Task list for the Add Supervision Actor feature"
---

# Tasks: Add Supervision Actor

**Input**: Design documents from `/specs/004-supervision-actor/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: This feature produces production code (the SimulaSupervisor, a new lifecycle event, and emission changes in three components), so test tasks ARE included. The project constitution (I. Test-First) requires tests written first, and (II. Coverage Standard) requires â‰¥97% line and branch coverage.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story. The core new component is `SimulaSupervisor.java`; the new lifecycle event `STARTED_ACTOR_EVENT` is added to `Engine.java` in the Foundational phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/main/java`, `src/test/java` at repository root (existing Maven module layout)

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented. The new lifecycle event and its documentation are the foundation for the supervision actor.

**?? CRITICAL**: No user story work can begin until this phase is complete.

- [X] T001 Add the `STARTED_ACTOR_EVENT` constant to the `Engine` interface in `src/main/java/jpnco/simula/Engine.java`, symmetric to the existing `STOPPED_ACTOR_EVENT`, with Javadoc citing FR-001/FR-007 and explaining that its source is the actor that started (FR-001; named literal per Constitution VII; Javadoc per Constitution VI)
- [X] T002 [P] Update the `Actor` interface Javadoc in `src/main/java/jpnco/simula/Actor.java` to document the per-actor startup notification (`STARTED_ACTOR_EVENT`) and the external-actor emission contract (how and when an actor that does not use standard delegation may emit it, with itself as source) (FR-007, Constitution VI)
- [X] T003 [P] Update `src/main/java/jpnco/simula/actors/package-info.java` to document `SimulaSupervisor` as a default actor alongside `Logger`, `TimeSource`, and `Barrier` (Constitution VI)
- [X] T004 [P] Update `architecture.md` at the repo root to mention the `SimulaSupervisor` and the new `STARTED_ACTOR_EVENT` lifecycle event in the actor model (Constitution VIII, Mermaid diagrams, no history)

**Checkpoint**: Foundation ready - user story implementation can now begin.

---

## Phase 2: User Story 1 - Observe when each actor starts (Priority: P1) ?? MVP

**Goal**: A per-actor startup notification is emitted by the built-in actors (via standard delegation), the engine, and the time source, and the SimulaSupervisor records each started actor (FR-001, FR-005, SC-002).

**Independent Test**: Start an engine with a SimulaSupervisor and one other standard actor, and assert the SimulaSupervisor records the other actor as started (and itself, since it also starts).

### Tests for User Story 1 (write FIRST, ensure they FAIL before implementation)

- [X] T010 [P] [US1] Unit test: a standard actor using `ActorDelegate` emits `STARTED_ACTOR_EVENT` (source = the actor) when it transitions into the running state on receiving `START_EVENT`, in `src/test/java/jpnco/simula/engine/ActorDelegateTest.java`
- [X] T011 [P] [US1] Unit test: `TimeSource` emits `STARTED_ACTOR_EVENT` (source = itself) when its run loop starts, in `src/test/java/jpnco/simula/actors/TimeSourceTest.java`
- [X] T012 [P] [US1] Unit test: `EngineImpl` emits `STARTED_ACTOR_EVENT` (source = itself) when its run loop starts, in `src/test/java/jpnco/simula/engine/EngineImplTest.java`
- [X] T013 [P] [US1] Unit test: `SimulaSupervisor` rejects a null engine at construction and subscribes to `STARTED_ACTOR_EVENT`, `STOPPED_ACTOR_EVENT`, and `STOPPED_ENGINE_EVENT` (FR-002), in `src/test/java/jpnco/simula/actors/SimulaSupervisorTest.java`
- [X] T014 [US1] Unit test: a standard actor starting is recorded as `STARTED` by the SimulaSupervisor (FR-001, SC-002), in `src/test/java/jpnco/simula/actors/SimulaSupervisorTest.java`

### Implementation for User Story 1

- [X] T015 [US1] Emit `STARTED_ACTOR_EVENT` (source = delegator) in `ActorDelegate` when a standard actor transitions into the running state on receiving `START_EVENT`, before its main loop begins (FR-001, FR-005) in `src/main/java/jpnco/simula/engine/ActorDelegate.java`
- [X] T016 [P] [US1] Emit `STARTED_ACTOR_EVENT` (source = self) in `TimeSource` when its run loop starts (FR-005) in `src/main/java/jpnco/simula/actors/TimeSource.java`
- [X] T017 [P] [US1] Emit `STARTED_ACTOR_EVENT` (source = self) in `EngineImpl` when its run loop starts (FR-005) in `src/main/java/jpnco/simula/engine/EngineImpl.java`
- [X] T018 [US1] Create `SimulaSupervisor` in `src/main/java/jpnco/simula/actors/SimulaSupervisor.java`: constructor validates a non-null engine (clear error otherwise), uses standard delegation (`ActorDelegate.createDelegate(engine, this)`), and subscribes to `STARTED_ACTOR_EVENT`, `STOPPED_ACTOR_EVENT`, and `STOPPED_ENGINE_EVENT`; `process(Event)` records a component as `STARTED` on `STARTED_ACTOR_EVENT` (FR-002, FR-005)

**Checkpoint**: US1 fully functional and testable independently.

---

## Phase 3: User Story 2 - Observe when actors and engines stop (Priority: P1)

**Goal**: The SimulaSupervisor records actor stops (`STOPPED_ACTOR_EVENT`) and child-engine stops (`STOPPED_ENGINE_EVENT`) (FR-003, FR-004, SC-003).

**Independent Test**: Stop an engine that has actors and a child engine, and assert the SimulaSupervisor records each actor stop and the child-engine stop.

### Tests for User Story 2 (write FIRST, ensure they FAIL before implementation)

- [X] T020 [P] [US2] Unit test: an actor stop (`STOPPED_ACTOR_EVENT`) is recorded as `STOPPED` by the SimulaSupervisor (FR-003, SC-003), in `src/test/java/jpnco/simula/actors/SimulaSupervisorTest.java`
- [X] T021 [US2] Unit test: a child-engine stop (`STOPPED_ENGINE_EVENT`) is recorded as `STOPPED` by the SimulaSupervisor (FR-004, SC-003), in `src/test/java/jpnco/simula/actors/SimulaSupervisorTest.java`

### Implementation for User Story 2

- [X] T022 [US2] In `SimulaSupervisor.process(Event)`, record a component as `STOPPED` on `STOPPED_ACTOR_EVENT` and `STOPPED_ENGINE_EVENT`, creating the component as `STOPPED` when it was not previously observed (FR-010) in `src/main/java/jpnco/simula/actors/SimulaSupervisor.java`

**Checkpoint**: US1 AND US2 both work independently.

---

## Phase 4: User Story 3 - Maintain a queryable supervision state (Priority: P2)

**Goal**: The SimulaSupervisor exposes a queryable, read-only state of observed components and their lifecycle status, consistent with the sequence of notifications (FR-006, FR-009, SC-004).

**Independent Test**: Run a scenario with starts and stops, query the SimulaSupervisor state, and assert it matches the notification sequence; a stop without a prior start is recorded as `STOPPED` without error.

### Tests for User Story 3 (write FIRST, ensure they FAIL before implementation)

- [X] T030 [P] [US3] Unit test: the SimulaSupervisor state matches the notification sequence for a component that starts then stops (FR-006, FR-009, SC-004), in `src/test/java/jpnco/simula/actors/SimulaSupervisorTest.java`
- [X] T031 [US3] Unit test: a stop notification for a component not previously observed is recorded as `STOPPED` without error (FR-010), in `src/test/java/jpnco/simula/actors/SimulaSupervisorTest.java`

### Implementation for User Story 3

- [X] T032 [US3] Expose a queryable, read-only view of the SimulaSupervisor state (each observed component and its `STARTED`/`STOPPED` status) in `src/main/java/jpnco/simula/actors/SimulaSupervisor.java` (FR-006)
- [X] T033 [US3] Add logging of the lifecycle events observed by the SimulaSupervisor via the framework logging facilities, with English comments (Constitution IV) in `src/main/java/jpnco/simula/actors/SimulaSupervisor.java`

**Checkpoint**: US1, US2 AND US3 all work independently.

---

## Phase 5: User Story 4 - Provide a documented contract for external actors (Priority: P3)

**Goal**: Actors from external projects that do not use standard delegation can emit `STARTED_ACTOR_EVENT` (with themselves as source) to be observed, and the contract is documented; a SimulaSupervisor never fails when a component emits no notification (FR-007).

**Independent Test**: An actor that follows the documented contract (emits `STARTED_ACTOR_EVENT` with itself as source) is observed by the SimulaSupervisor.

### Tests for User Story 4 (write FIRST, ensure they FAIL before implementation)

- [X] T040 [US4] Unit test: an actor that emits `STARTED_ACTOR_EVENT` with itself as source (following the documented contract) is recorded as `STARTED` by the SimulaSupervisor, and a component that emits no notification causes no failure (FR-007, FR-010), in `src/test/java/jpnco/simula/actors/SimulaSupervisorTest.java`

### Implementation for User Story 4

- [X] T041 [US4] Ensure the external-actor emission contract (how and when to emit `STARTED_ACTOR_EVENT`, with the actor itself as source) is documented in the `Engine`/`Actor` Javadoc and in `architecture.md` (completing T001/T002/T004 where needed) (FR-007, Constitution VI/VIII)

**Checkpoint**: All four user stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Finalization, documentation, and constitution compliance.

- [X] T050 [P] Verify the coverage gate: run `mvn -o verify` and confirm line and branch coverage each â‰¥ 97% for the framework bundle (Constitution II, SC-006)
- [X] T051 [P] Add the new and modified source/test files to the Spotless includes in `pom.xml` (e.g. `SimulaSupervisor.java`, `SimulaSupervisorTest.java`, and any modified files not yet included), then run `mvn -o spotless:apply` to format (Constitution V)
- [X] T052 Verify Javadoc on all new/changed methods cites the implemented FR/SC identifiers (Constitution VI)
- [X] T053 Verify `architecture.md` uses Mermaid-only structural diagrams and contains no historical information (Constitution VIII)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies - can start immediately; BLOCKS all user stories
- **User Stories (Phase 2-5)**: All depend on Foundational completion
- **User Story 2 (Phase 3)**: Shares `SimulaSupervisor.process` with US1 (sequential within the same file)
- **User Story 3 (Phase 4)**: Adds a query API to `SimulaSupervisor.java` (sequential after US1/US2)
- **User Story 4 (Phase 5)**: Documentation/contract; depends on the `STARTED_ACTOR_EVENT` foundation
- **Polish (Phase 6)**: Depends on all implementation phases

### User Story Dependencies

- **US1 (P1)**: After Foundational; no dependency on other stories
- **US2 (P1)**: After Foundational; shares `SimulaSupervisor.java` with US1 (sequential within the same file)
- **US3 (P2)**: After Foundational; extends `SimulaSupervisor.java` built by US1/US2
- **US4 (P3)**: After Foundational; documents the contract for the event introduced in Foundational/US1

### Parallel Opportunities

- Foundational tasks marked [P] can run in parallel
- US1 emission tasks T015 (ActorDelegate), T016 (TimeSource), T017 (EngineImpl) touch different files and can run in parallel
- US1 test tasks T010/T011/T012/T013 touch different files and can run in parallel
- `SimulaSupervisor.java` is edited by T018 (US1), T022 (US2), T032/T033 (US3) and MUST run sequentially, not in parallel
- `SimulaSupervisorTest.java` is edited by T013/T014 (US1), T020/T021 (US2), T030/T031 (US3), T040 (US4) and MUST run sequentially, not in parallel

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Foundational (`STARTED_ACTOR_EVENT`, Javadoc, package-info, architecture)
2. Complete Phase 2: User Story 1 (startup emission + SimulaSupervisor records started)
3. **STOP and VALIDATE**: `mvn -o test` - verify US1 independently
4. Proceed to US2, US3, US4, then polish

### Notes on same-file sequencing

- `SimulaSupervisor.java` is edited by US1 (T018), US2 (T022), US3 (T032/T033). These MUST run sequentially.
- `SimulaSupervisorTest.java` is edited by all four user stories. These MUST run sequentially.
- `ActorDelegate.java` is edited only by T015; `TimeSource.java` only by T016; `EngineImpl.java` only by T017.

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing (Red) per Constitution I
- Build offline with `mvn -o` in this environment (no network); verify coverage with `mvn -o verify`
- Commit after each task or logical group
- Stop at any checkpoint to validate independently

---

## Phase 7: Expose engine children (FR-011, SC-007)

**Purpose**: The engine exposes its child engines as a non-modifiable, ordered collection (FR-011, SC-007). This is a framework accessor addition to the supervision feature.

### Tests (write FIRST, ensure they FAIL before implementation)

- [X] T054 [P] Unit tests: `getChildren()` returns the added children in insertion order, returns a non-modifiable list, and returns a snapshot (later additions do not alter an already-returned list) (FR-011, SC-007), in `src/test/java/jpnco/simula/engine/EngineImplCoverageTest.java`

### Implementation

- [X] T055 Implement `getChildren()` on the `Engine` interface and in `EngineImpl`, returning a non-modifiable, ordered `List<Engine>` snapshot of the child engines (guarded by the engine lock), with `EngineImpl.children` as a `LinkedHashSet` to preserve insertion order (FR-011, FR-012) in `src/main/java/jpnco/simula/Engine.java` and `src/main/java/jpnco/simula/engine/EngineImpl.java`

### Polish

- [X] T056 Verify the coverage gate with `mvn -o verify` and confirm the Javadoc on the new method cites FR-011 (Constitution II, VI)
