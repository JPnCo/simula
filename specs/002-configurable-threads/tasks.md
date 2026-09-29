---

description: "Task list for the Configurable Thread Execution feature"
---

# Tasks: Configurable Thread Execution

**Input**: Design documents from `/specs/002-configurable-threads/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: This feature produces production code (virtual/classic thread modes, ReentrantLock replacement), so test tasks ARE included. The project constitution (I. Test-First) requires tests written first, and (II. Coverage Standard) requires ≥97% line and branch coverage.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story. Because this is an in-place refactor of existing files, some tasks touch the same files and are therefore NOT parallelizable with each other.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/main/java`, `src/test/java` at repository root (existing Maven module layout)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Build tooling required to measure the constitution coverage gate; no user story can be validated without it.

- [X] T001 Add JaCoCo Maven plugin to `pom.xml` (jacoco-maven-plugin) with `prepare-agent` and `check` goals; configure `line` and `branch` counter rules at `COVEREDRATIO` 0.97 (Constitution II, research.md)
- [X] T002 [P] Wire the JaCoCo coverage gate into the Maven lifecycle (verify that `mvn verify` runs jacoco:check after tests)
- [X] T003 [P] Add formatter enforcement for files touched by this feature (`EngineImpl.java`, `IdBuilder.java`, and new test files) per research.md formatter decision (Constitution V)

**Checkpoint**: `mvn -o verify` runs tests and reports line+branch coverage. Foundation ready.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented.

**?? CRITICAL**: No user story work can begin until this phase is complete.

- [X] T004 Create `ExecutionMode` enum in `src/main/java/jpnco/simula/engine/ExecutionMode.java` with values `VIRTUAL` and `PLATFORM` (FR-001, data-model.md; named literals per Constitution VII)
- [X] T005 [P] Add `architecture.md` at repository root documenting the framework architecture with Mermaid diagrams (classDiagram of actor/engine/event model; sequenceDiagram of start/stop and event signal), no historical content (Constitution VIII)
- [X] T006 [P] Write the Javadoc for the `ExecutionMode` enum and affected methods citing the FR/SC identifiers they implement (Constitution VI)

**Checkpoint**: Foundation ready - user story implementation can now begin.

---

## Phase 3: User Story 1 - Run actors on virtual threads by default (Priority: P1) ?? MVP

**Goal**: Actors run on virtual threads by default when no execution mode is configured (FR-002, SC-002).

**Independent Test**: Start an engine without a mode and assert actors run on virtual threads and process events.

### Tests for User Story 1 (write FIRST, ensure they FAIL before implementation)

- [X] T010 [P] [US1] Unit test: engine constructed without a mode defaults to virtual-thread execution, in `src/test/java/jpnco/simula/engine/EngineImplTest.java`
- [X] T011 [US1] Unit test: an actor started under the default mode runs on a virtual thread (assert thread is virtual via `Thread.isVirtual()`), in `src/test/java/jpnco/simula/engine/EngineImplTest.java`
- [X] T012 [US1] Unit test: an actor started under the default mode processes a subscribed event, in `src/test/java/jpnco/simula/engine/EngineImplTest.java`
- [X] T013 [US1] Unit test: actor thread carries a meaningful name in default (virtual) mode (FR-006), in `src/test/java/jpnco/simula/engine/EngineImplTest.java`

### Implementation for User Story 1

- [X] T014 [US1] Wire `ExecutionMode` into `EngineImpl` constructor(s) and store it; default to `VIRTUAL` (FR-002/003, data-model.md) in `src/main/java/jpnco/simula/engine/EngineImpl.java`
- [X] T015 [US1] Branch `EngineImpl.start(Actor)` to use `Thread.ofVirtual().name(name).start(actor)` for virtual mode (FR-004/006) in `src/main/java/jpnco/simula/engine/EngineImpl.java`

**Checkpoint**: US1 fully functional and testable independently.

---

## Phase 4: User Story 2 - Run actors on classic threads via configuration (Priority: P1)

**Goal**: Actors run on classic (platform) threads when `ExecutionMode.PLATFORM` is selected (FR-001/003/004).

**Independent Test**: Start an engine with `ExecutionMode.PLATFORM` and assert actors run on classic threads and process events.

### Tests for User Story 2 (write FIRST, ensure they FAIL before implementation)

- [X] T020 [P] [US2] Unit test: engine constructed with `ExecutionMode.PLATFORM` runs actors on classic (non-virtual) threads, in `src/test/java/jpnco/simula/engine/EngineImplTest.java`
- [X] T021 [US2] Unit test: an actor under classic mode processes a subscribed event, in `src/test/java/jpnco/simula/engine/EngineImplTest.java`
- [X] T022 [US2] Unit test: actor thread carries a meaningful name in classic mode (FR-006), in `src/test/java/jpnco/simula/engine/EngineImplTest.java`
- [X] T023 [US2] Unit test: an invalid/unknown execution-mode value is rejected with a clear error (FR-007, SC-004), in `src/test/java/jpnco/simula/engine/EngineImplTest.java`

### Implementation for User Story 2

- [X] T024 [US2] Branch `EngineImpl.start(Actor)` to use `new Thread(actor, name).start()` for classic mode, preserving existing platform-thread behavior (FR-004) in `src/main/java/jpnco/simula/engine/EngineImpl.java`
- [X] T025 [US2] Add rejection of invalid/unknown execution-mode values with a clear error (FR-007) in `src/main/java/jpnco/simula/engine/EngineImpl.java`

**Checkpoint**: US1 AND US2 both work independently.

---

## Phase 5: User Story 3 - Keep behavior identical across execution modes (Priority: P2)

**Goal**: Same simulation produces equivalent outcomes under both modes; stop cascade works in both (FR-005/008/013, SC-003/005).

**Independent Test**: Run identical scenarios under both modes and assert equivalent outcomes; verify stop terminates all actors/children in both modes.

### Tests for User Story 3 (write FIRST, ensure they FAIL before implementation)

- [X] T030 [P] [US3] Unit test: identical scenario produces equivalent observable results under virtual and classic modes (FR-005, SC-003), in `src/test/java/jpnco/simula/engine/EngineImplTest.java`
- [X] T031 [US3] Unit test: stopping the root engine terminates every actor and child engine in both modes; no actor remains running (FR-008, SC-005), in `src/test/java/jpnco/simula/engine/EngineImplTest.java`
- [X] T032 [US3] Unit test: event processing, time advancement, and start/stop semantics handled identically in both modes (FR-005), in `src/test/java/jpnco/simula/engine/EngineImplTest.java`

### Implementation for User Story 3

- [X] T033 [US3] Verify/adjust start/stop and time handling so behavior is equivalent across modes (FR-005/008/013) in `src/main/java/jpnco/simula/engine/EngineImpl.java`
- [X] T034 [US3] Ensure the stop cascade terminates all actors and child engines regardless of mode (FR-008) in `src/main/java/jpnco/simula/engine/EngineImpl.java`

**Checkpoint**: All three user stories independently functional.

---

## Phase 6: ReentrantLock replacement (Cross-cutting, FR-010..013, SC-007)

**Purpose**: Replace all `synchronized` usages with explicit `ReentrantLock` across the whole framework (12 in `EngineImpl`, 1 in `IdBuilder`). Per research.md, use a single per-engine lock to avoid nested lock-ordering deadlock risk.

### Tests for locking change (write FIRST, ensure they FAIL - a regression test would not fail before the change, so these verify correctness is preserved)

- [X] T040 [P] [X] Unit test: concurrent subscribe/unsubscribe and register/unregister remain mutually exclusive (FR-012), in `src/test/java/jpnco/simula/engine/EngineImplTest.java`
- [X] T041 [X] Unit test: `IdBuilder.nextId()` remains thread-safe and returns unique ids under concurrency (FR-012), in `src/test/java/jpnco/simula/engine/IdBuilderTest.java`
- [X] T042 [X] Unit test: locks are released on exceptional paths - no deadlock or stuck state after an exception during a locked operation (FR-011, SC-007), in `src/test/java/jpnco/simula/engine/EngineImplTest.java`

### Implementation for locking change

- [X] T043 [X] Replace the 6 `synchronized` methods in `EngineImpl.java` (`addChild`, `getSubscribers`, `register`, `subscribe`, `unregister`, `unsubscribe`) with a per-instance `ReentrantLock` using try/finally (FR-010/011) in `src/main/java/jpnco/simula/engine/EngineImpl.java`
- [X] T044 [X] Replace the 6 `synchronized (…)` blocks in `EngineImpl.java` (children/actors/subscribersByTopic) with the same per-instance lock, preserving mutual exclusion and lock ordering (FR-010/012) in `src/main/java/jpnco/simula/engine/EngineImpl.java`
- [X] T045 [X] Replace `synchronized static nextId()` in `IdBuilder.java` with a static `ReentrantLock` (FR-010) in `src/main/java/jpnco/simula/engine/IdBuilder.java`

**Checkpoint**: All `synchronized` usages removed; concurrency tests pass.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Finalization, documentation, and constitution compliance.

- [X] T050 [P] Verify the coverage gate: run `mvn -o verify` and confirm line and branch coverage each ≥ 97% for affected code (Constitution II, SC-008)
- [X] T051 [P] Update `quickstart.md` validation scenarios to reflect the offline `mvn -o` build and the realigned dependency versions (junit 5.14.0, mockito 5.22.0)
- [X] T052 Run the quickstart.md validation scenarios end-to-end
- [X] T053 Verify Javadoc on all new/changed methods cites the implemented FR/SC identifiers (Constitution VI)
- [X] T054 Verify `architecture.md` uses Mermaid-only structural diagrams and contains no historical information (Constitution VIII)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup - BLOCKS all user stories
- **User Stories (Phase 3-5)**: All depend on Foundational completion
- **Locking change (Phase 6)**: Cross-cutting; touches the same files as US1-3, so it should be sequenced after the user-story implementation to avoid same-file conflicts
- **Polish (Phase 7)**: Depends on all implementation phases

### User Story Dependencies

- **US1 (P1)**: After Foundational; no dependency on other stories
- **US2 (P1)**: After Foundational; shares `EngineImpl.start` with US1 (sequential within the same file)
- **US3 (P2)**: After Foundational; depends on both US1 and US2 being in place to compare modes

### Within Each User Story

- Tests MUST be written and FAIL before implementation (Constitution I: Red-Green-Refactor)
- Enum before wiring; wiring before behavior; behavior before equivalence

### Parallel Opportunities

- Setup tasks marked [P] can run in parallel
- Tests within a user story marked [P] can run in parallel
- `architecture.md` (T005) and Javadoc (T006) run in parallel with foundational code
- The locking phase (T043-045) must NOT run in parallel with US1-3 implementation tasks because they edit the same files (`EngineImpl.java`, `IdBuilder.java`)

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (JaCoCo gate)
2. Complete Phase 2: Foundational (`ExecutionMode` enum)
3. Complete Phase 3: User Story 1 (virtual-thread default)
4. **STOP and VALIDATE**: `mvn -o test` - verify US1 independently
5. Proceed to US2, US3, then locking change

### Notes on same-file sequencing

- `EngineImpl.java` is edited by US1 (T014/015), US2 (T024/025), US3 (T033/034), and the locking phase (T043/044). These MUST run sequentially, not in parallel, to avoid conflicts.
- `IdBuilder.java` is edited only by T045.

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability; [X] = cross-cutting locking change
- Each user story should be independently completable and testable
- Verify tests fail before implementing (Red) per Constitution I
- Build offline with `mvn -o` in this environment (no network); verify coverage with `mvn -o verify`
- Commit after each task or logical group
- Stop at any checkpoint to validate independently

---

## Phase 8: Convergence

**Purpose**: Close gaps found by `/speckit.converge` between the spec/plan/tasks and the implemented codebase. Constitution gate V (formatter) is unmet; FR-009 and SC-006 are only partially addressed.

**?? CRITICAL**: T055 addresses Constitution V, which is a MUST principle; it is emitted first.

- [X] T055 Add the Spotless Maven plugin (`com.diffplug.spotless:spotless-maven-plugin:2.45.0`, cached in the local `~/.m2`) to `pom.xml`, bound to run `check` (or the `verify` phase) for Java sources, using the only formatter engine cached offline (`google-java-format` 1.35.0), scoped to the files touched by this feature so legacy files are not reformatted; run `mvn -o spotless:apply` to format, then confirm the gate is enforced per research.md formatter decision and Constitution V (CRITICAL, `missing`)
- [X] T056 Document the virtual-thread-unsupported runtime behavior (clear failure or fallback to classic threads with documented behavior) in the execution-mode contract and `EngineImpl.start(Actor)` Javadoc, and add a test asserting the defined behavior (FR-009, `partial`)
- [X] T057 Add a virtual-mode capacity/stress test that starts and stops an engine with a high number of actors under `ExecutionMode.VIRTUAL` without exhausting platform resources (SC-006, `partial`)

---

## Phase 9: Traffic-Light Illustrative Example

**Purpose**: Add a substantial, runnable sample (`jpnco.simula.examples.trafficlight`) that illustrates the simula framework and the configurable execution-mode feature (FR-001..FR-006, FR-008, SC-003). The example is demonstration code, excluded from the JaCoCo `check` rule so the framework bundle keeps its 97% gate.

**Sequencing**: All tasks touch files under the new `examples/trafficlight/` package except T058/T059 (pom.xml) and T060 (module-info). T058 and T059 edit the same file (`pom.xml`) and MUST run sequentially. The Java example tasks (T061-T066) are independent of the pom changes and can follow.

- [X] T058 Exclude `jpnco/simula/examples/**` from the JaCoCo `check` rule in `pom.xml` so the illustrative sample does not erode the 97% framework-bundle coverage gate (Constitution II, plan: coverage exclusion)
- [X] T059 Add `exec-maven-plugin` (3.6.3, cached in local `~/.m2`) to `pom.xml` with `mainClass = jpnco.simula.examples.trafficlight.TrafficLightDemo` to enable `mvn -o exec:java` (plan: execution mechanism)
- [X] T060 Add `exports jpnco.simula.examples.trafficlight;` and `requires java.desktop;` to `module-info.java` (the latter for the Swing GUI) (plan: execution mechanism)
- [X] T061 [P] Create `Direction` enum and `Vehicle` data class in `src/main/java/jpnco/simula/examples/trafficlight/` — cardinals with row/col deltas, and a vehicle holding id/direction/speed/distance-in-segment (data-model.md)
- [X] T062 [P] Create `TrafficCoordinator` actor in `src/main/java/jpnco/simula/examples/trafficlight/TrafficCoordinator.java` that owns the 5×5 grid, staggered lights, the fixed fleet, and advances continuous movement on each `TIME_EVENT`, making vehicles turn right or left at the grid edges instead of leaving it (data-model.md)
- [X] T063 [P] Create `GridState`, `VehicleView` and `GridDisplay` in `src/main/java/jpnco/simula/examples/trafficlight/` — immutable snapshot types and the display sink interface (data-model.md)
- [X] T064 [P] Create `TrafficMonitor` (console `GridDisplay`) that renders the grid each `TIME_EVENT` (data-model.md)
- [X] T065 Create `TrafficLightDemo` `main` class in `src/main/java/jpnco/simula/examples/trafficlight/TrafficLightDemo.java` that builds a root engine (with the selected `ExecutionMode`, default `VIRTUAL`), seeds and starts the grid, runs `SIMULATED_SECONDS` (120), stops, and prints an equivalent outcome summary in both modes (FR-001..008, SC-003)
- [X] T066 Create `TrafficLightGui` (Swing/Java2D) that visualizes the grid in real time — green segment (~20 m) on the green road per intersection and vehicles drawn at their current position; selected via the `gui` display token (data-model.md)
- [X] T067 Run `mvn -o compile` and `mvn -o verify` to confirm the build stays green and the 97% framework-bundle coverage gate is preserved with the example excluded (Constitution II)
- [X] T068 Run the demo in both modes (`mvn -o exec:java -Dexec.args="virtual"` and `...="classic"`) and confirm equivalent outcomes (SC-003, quickstart.md scenario 8)

---

## Phase 10: Convergence

**Purpose**: Close gaps found by `/speckit.converge` between the spec/plan/tasks and the implemented codebase. Constitution gate VI (documentation citing FR/SC identifiers) is unmet for six example classes.

**?? CRITICAL**: T069 addresses Constitution VI, which is a MUST principle; it is emitted first.

- [X] T069 Remove the FR/SC identifier citations from the Javadoc of the example classes in `src/main/java/jpnco/simula/examples/trafficlight/` (`TrafficCoordinator.java`, `TrafficLightDemo.java`, `TrafficLightGui.java`; the six files named in the original task were already clean). Samples do not participate in the implementation, so they must not reference FR-###/SC-### identifiers. This aligns with the plan.md addendum Constitution Check for gate VI ("Javadoc on example classes/methods" without FR/SC citations). (Constitution VI; plan.md addendum Constitution Check declares "Javadoc on example classes/methods citing FR/SC | PASS" — currently contradicted) (CRITICAL, `contradicts`)

---

## Phase 11: Extraction of the sample (addendum)

The `trafficlight` sample was **extracted** from this project into a separate Maven project **`C:\JPC\PERSO\SDD\SIMULA_SAMPLES`** (artifact `jpnco:simula-samples:0.0.1-SNAPSHOT`, package `jpnco.simula.samples.trafficlight`). The `examples` package was deleted from this `simula` project and its wiring removed (see plan.md addendum). Phases 9–10 above remain valid as the historical record of how the sample was first created and hardened before extraction.

- [X] T070 Delete the `jpnco/simula/examples` package from the `simula` project (`src/main/java/jpnco/simula/examples/**`), whose `trafficlight` sample now lives in `SIMULA_SAMPLES`.
- [X] T071 Remove from `pom.xml` the JaCoCo `check` exclusion `jpnco/simula/examples/**`, the `exec-maven-plugin` (`mainClass = …TrafficLightDemo`), and the spotless include for `examples/**`.
- [X] T072 Remove from `module-info.java` the `exports jpnco.simula.examples.*` lines and `requires java.desktop;` (the Swing GUI in the sample was the only `java.desktop` consumer).
- [X] T073 Update docs (`data-model.md` addendum, `plan.md` addendum, `quickstart.md` §8, `tasks.md` this section) to reflect that the sample lives in `SIMULA_SAMPLES` and how to run it.

---

## Phase 12: Convergence

**Purpose**: Close gaps found by `/speckit.converge` between the spec/plan/tasks and the implemented codebase. All requirements (FR-001..FR-013) and success criteria (SC-001..SC-008) are satisfied and the build is green with the 97% line+branch coverage gate met. One unrequested addition remains for awareness.

- [X] T074 Review/justify or remove the unrequested child-engine constructor `EngineImpl(String title, Engine parent, int timeFactor)` added to the working tree in `src/main/java/jpnco/simula/engine/EngineImpl.java`; it is not called for by any task in this feature (`unrequested`). **Resolution**: retained — it completes the root/child symmetry for an explicit time factor (the root equivalent `EngineImpl(title, timeFactor)` already existed). Its Javadoc incorrectly described "platform-thread" while delegating to `ExecutionMode.VIRTUAL`; corrected to "virtual-thread".
