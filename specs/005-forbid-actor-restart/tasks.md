---

description: "Task list for the Forbid Actor Restart feature"
---

# Tasks: Forbid Actor Restart

**Input**: Design documents from `/specs/005-forbid-actor-restart/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: The project constitution (I. Test-First, NON-NEGOTIABLE) requires tests written first, and (II. Coverage Standard) requires ≥97% line and branch coverage. Test tasks ARE included and MUST fail (Red) before their implementation tasks.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story. The refusal itself (US1) is deliverable engine-side only (weak-instance memory + double-registration check); the actor-side visibility (US2) and the custom-delegate contract completion (US3) layer on top without invalidating US1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/main/java`, `src/test/java` at repository root (existing Maven module layout)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Pre-flight verification and formatting setup; no new structure (single existing module).

- [X] T001 Audit existing tests and framework flows for any re-registration of a stopped or concurrently-registered actor instance (research.md R7, SC-004) across `src/test/java/jpnco/simula/engine/*.java`, `src/test/java/jpnco/simula/actors/*.java`, `src/main/java/jpnco/simula/engine/EngineImpl.java`, and `src/main/java/jpnco/simula/actors/*.java`; report findings (expected: none — the guards are additive)
- [X] T002 Add the production and test files modified by this feature to the Spotless `<includes>` in `pom.xml` if not already present (`src/main/java/jpnco/simula/Actor.java`, `src/main/java/jpnco/simula/engine/ActorDelegate.java`, `src/main/java/jpnco/simula/engine/EngineImpl.java`, `src/test/java/jpnco/simula/engine/ActorDelegateTest.java`; `EngineImplCoverageTest.java` is already included), then run `mvn -o spotless:apply` (Constitution V)

**Checkpoint**: Baseline verified, formatter configured for the files to be touched.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: None — this feature introduces no new event, component, or infrastructure. All work happens inside the three user stories on existing files.

**Checkpoint**: Proceed directly to User Story 1.

---

## Phase 3: User Story 1 - A stopped actor cannot be brought back (Priority: P1) — MVP

**Goal**: The engine refuses to register (or register-and-start) an actor instance it has already unregistered after a stop, or that is currently registered, with a logged error and an `IllegalArgumentException` naming the actor, leaving engine state untouched (FR-001, FR-002, FR-003, FR-008; SC-001, SC-002, SC-005).

**Independent Test**: Register/start an actor, stop it, wait for unregistration, attempt `registerAndStart` again → `IllegalArgumentException` + error log + actor absent from `getActors()`; and register a currently-registered actor → same refusal, running execution undisturbed.

### Tests for User Story 1 (write FIRST, ensure they FAIL before implementation)

- [X] T010 [P] [US1] Unit tests: re-registering (both `registerAndStart`) an actor instance previously stopped and unregistered throws `IllegalArgumentException` naming the actor, the engine logs an error, and `getActors()` is identical before/after the attempt (FR-001, FR-002, FR-003, SC-001, SC-002), in `src/test/java/jpnco/simula/engine/EngineImplCoverageTest.java`
- [X] T011 [P] [US1] Unit tests: registering an actor whose id is currently registered on the engine (running actor) throws `IllegalArgumentException` with the currently-registered message and does not disturb the running actor, which still processes events afterwards (FR-008, SC-005), in `src/test/java/jpnco/simula/engine/EngineImplCoverageTest.java`
- [X] T012 [P] [US1] Unit test: a fresh (never-registered) actor instance is accepted even when another actor with the same name previously stopped; it registers, runs, and stops normally (FR-007, ACCEPT-1), in `src/test/java/jpnco/simula/engine/EngineImplCoverageTest.java`
- [X] T013 [US1] Unit test: after a refused registration the actor is subscribed to no topic and no new thread executes it — verify via `signal` of its business topic producing no processing and `getActors()` unchanged (NO-TRACE rule, FR-003), in `src/test/java/jpnco/simula/engine/EngineImplCoverageTest.java` (same file as T010-T012: sequential)

### Implementation for User Story 1

- [X] T014 [US1] In `src/main/java/jpnco/simula/engine/EngineImpl.java`: add two `static final String` message-template constants naming the actor (stopped case and currently-registered case — raw literals forbidden per Constitution VII) and the field `stoppedInstances` = `Collections.newSetFromMap(new WeakHashMap<Actor, Boolean>())` (R3); in `unregister()`, on the successful-removal branch (non-engine actor, `actors.remove` returned non-null), add the instance to `stoppedInstances` under the existing `ReentrantLock` (NEVER `synchronized`) (FR-005, FR-006)
- [X] T015 [US1] In `src/main/java/jpnco/simula/engine/EngineImpl.java`: in the private `register(Actor)` — the single registration funnel — before any mutation and under the engine lock, refuse (data-model order REF-3 then REF-2): if `actors.containsKey(actor.getId())` → `Logger.error(this, ...)` + `throw new IllegalArgumentException(...)` with the currently-registered message (FR-008); else if `stoppedInstances.contains(actor)` → same pattern with the stopped message (FR-001, FR-002); Javadoc cites FR-001, FR-002, FR-003, FR-008 (Constitution VI)
- [X] T016 [US1] In `src/main/java/jpnco/simula/Engine.java`: update the `registerAndStart(Actor)` Javadoc to document the refusal contract (both refusal cases, logged error, `IllegalArgumentException`, no-trace guarantee) per `specs/005-forbid-actor-restart/contracts/registration-guard.md` (FR-001..FR-003, FR-008; Constitution VI)

**Checkpoint**: US1 fully functional — stopped and currently-registered instances are refused loudly; fresh actors unaffected. Validate with `mvn -o test`.

---

## Phase 4: User Story 2 - Stopped status is visible (Priority: P2)

**Goal**: An actor reports its own terminal stopped state via `isStopped()`, backed by the standard delegate; the engine guard consults it first (FR-004; SC-001 coverage for standard-delegated actors without relying on the weak memory).

**Independent Test**: Query a fresh actor → `false`; run it to stop → `true`; the refusal path still fires after this change.

### Tests for User Story 2 (write FIRST, ensure they FAIL before implementation)

- [X] T020 [US2] Unit tests: `isStopped()` is `false` on a fresh standard-delegated actor and while it is running, and becomes `true` after its run loop completes its stop (including the stop-before-START path and the crash path where `run()` exits through the catch) (FR-004, US2 acceptance scenarios 1-3), in `src/test/java/jpnco/simula/engine/ActorDelegateTest.java`

### Implementation for User Story 2

- [X] T021 [US2] In `src/main/java/jpnco/simula/Actor.java`: add `default boolean isStopped() { return false; }` with Javadoc documenting the terminal-state semantics and the contract that custom delegations SHOULD override it (FR-004, FR-005; Constitution VI)
- [X] T022 [US2] In `src/main/java/jpnco/simula/engine/ActorDelegate.java`: add `private volatile boolean stopped;` set to `true` exactly once in a new `finally` block of `run()` (after all stop/crash paths have signaled `STOPPED_ACTOR_EVENT`, per R2), and override `isStopped()`; Javadoc cites FR-004
- [X] T023 [US2] In `src/main/java/jpnco/simula/engine/EngineImpl.java`: in the `register(Actor)` guard, check `actor.isStopped()` first (REF-1) before the currently-registered and weak-memory checks, same log + exception behavior; Javadoc updated (FR-001, FR-004)

**Checkpoint**: US1 AND US2 work independently; standard actors self-report the terminal state.

---

## Phase 5: User Story 3 - Custom-delegated actors are covered too (Priority: P3)

**Goal**: Actors whose custom delegation does not report stopped status are still refused via the weak engine-side memory (already built in US1); the backstop memory is demonstrably bounded; the custom-delegation contract is fully documented (FR-005, FR-006; SC-001, SC-003).

**Independent Test**: A custom-delegated actor without `isStopped()` override is refused after stop; stopping and releasing many short-lived actors leaves the simulation healthy and the bookkeeping non-growing.

### Tests for User Story 3 (write FIRST, ensure they FAIL before implementation)

- [X] T030 [P] [US3] Unit test: an actor with a custom delegate that does NOT override `isStopped()`, once stopped and unregistered, is refused on re-registration via the engine's weak-instance memory with the same exception and error log (FR-005, SC-001), in `src/test/java/jpnco/simula/engine/EngineImplCoverageTest.java`
- [X] T031 [US3] Unit test: create, start, and stop a batch of short-lived actors, drop all references, and continue the simulation — the engine stays healthy, no error is logged, and a subsequent fresh registration still succeeds (SC-003 operational check of BOUNDED rule), in `src/test/java/jpnco/simula/engine/EngineImplCoverageTest.java` (same file as T030: sequential)

### Implementation for User Story 3

- [X] T032 [US3] In `src/main/java/jpnco/simula/Actor.java`: complete the `isStopped()` Javadoc with the custom-delegation contract wording from `specs/005-forbid-actor-restart/contracts/registration-guard.md` (what a custom delegation must guarantee if it overrides: `true` only after its run loop has terminated; the engine backstop covers non-overriders) (FR-005; Constitution VI)

**Checkpoint**: All three user stories independently functional; refusal covers every actor style.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T040 [P] Update `README.md`: "Stopping an actor" section (stop is terminal — create a new instance to run the behavior again), a Best practices bullet (never re-register a stopped instance), and the API reference (add `isStopped()` to the `Actor` method list)
- [ ] T041 [P] Update `architecture.md`: document the registration guard (terminal stopped state, checks REF-1/REF-2/REF-3, weak-instance memory under the engine lock) in the lifecycle/concurrency sections as present-state only, Mermaid diagrams only, no history (Constitution VIII)
- [ ] T042 Verify Javadoc on all new/changed methods cites the implemented FR/SC identifiers (Constitution VI)
- [ ] T043 Verify the coverage gate: run `mvn -o clean verify` and confirm line and branch coverage each ≥ 97% for the framework bundle (Constitution II, SC-004)
- [ ] T044 Run the `specs/005-forbid-actor-restart/quickstart.md` validation: full suite green, and the samples project (`../SIMULA_SAMPLES`, after `mvn -o install`) compiles unchanged (SC-004)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Empty - nothing blocks the stories
- **US1 (Phase 3)**: Depends only on Setup; the MVP
- **US2 (Phase 4)**: Tests/impl touch different files than US1's remaining work, but T023 edits `EngineImpl.java` after T014/T015 → run after US1
- **US3 (Phase 5)**: Backstop behavior already exists from US1; tests + contract Javadoc only; run after US1 (and after US2 for T032 file overlap with T021)
- **Polish (Phase 6)**: Depends on all desired user stories

### Within Each User Story

- Tests MUST be written and FAIL before implementation (Constitution I)
- Same-file tasks MUST run sequentially (marked in descriptions)

### Parallel Opportunities

- T001 and T002 can run in parallel
- US1 tests T010/T011/T012 can be drafted in parallel (all in `EngineImplCoverageTest.java` — coordinate edits; T013 sequential after them)
- US2/US3 test authoring can start while US1 implementation runs, but execution of Red/Green cycles stays sequential per file

### Same-File Coordination (CRITICAL)

- `EngineImpl.java`: T014 → T015 → T023 (sequential)
- `EngineImplCoverageTest.java`: T010 → T011 → T012 → T013 → T030 → T031 (sequential)
- `Actor.java`: T021 → T032 (sequential)
- `ActorDelegate.java`: only T022; `Engine.java`: only T016; `ActorDelegateTest.java`: only T020

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (audit + Spotless includes)
2. Complete Phase 3: US1 tests T010-T013, verify Red, implement T014-T016, verify Green
3. **STOP and VALIDATE**: `mvn -o test` — refusals work for every registration attempt type; no regression
4. Proceed to US2, US3, then polish

### Incremental Delivery

1. US1 → loud refusal (core protection delivered, MVP)
2. US2 → actors self-report terminal state (API visibility)
3. US3 → full-style coverage demonstrated + bounded-memory check + contract doc
4. Polish → docs, coverage gate, quickstart validation

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Build offline with `mvn -o` in this environment (no network); verify coverage with `mvn -o clean verify`
- Never introduce `synchronized` monitors — use the existing `ReentrantLock` (architecture.md concurrency rule)
- Exception messages MUST be `static final` named constants (Constitution VII)
- Commit after each task or logical group; stop at any checkpoint to validate independently
