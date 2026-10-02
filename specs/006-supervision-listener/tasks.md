---

description: "Task list for the Supervision Listener feature"
---

# Tasks: Supervision Listener

**Input**: Design documents from `/specs/006-supervision-listener/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: The project constitution (I. Test-First, NON-NEGOTIABLE) requires tests written first, and (II. Coverage Standard) requires ≥97% line and branch coverage. Test tasks ARE included and MUST fail (Red) before their implementation tasks.

**Organization**: Tasks are grouped by user story. US1 (notification of transitions) is the MVP; US2 (safe registration management + fault isolation) and US3 (misuse-proof API) layer on top of the same two files.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/main/java`, `src/test/java` at repository root (existing Maven module layout)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Pre-flight verification and formatting setup; no new structure (single existing module).

- [X] T001 Audit existing supervisor tests and samples usage for assumptions about `record()`/`process()` timing that per-transition notification could disturb (research.md R5, SC-004) across `src/test/java/jpnco/simula/actors/SimulaSupervisorTest.java` and `src/main/java/jpnco/simula/actors/SimulaSupervisor.java`; report findings (expected: none — the feature is additive)
- [X] T002 Add the new production file to the Spotless `<includes>` in `pom.xml` (`src/main/java/jpnco/simula/actors/SupervisionListener.java`; `SimulaSupervisor.java` and `SimulaSupervisorTest.java` already included), then run `mvn -o spotless:apply` (Constitution V)

**Checkpoint**: Baseline verified, formatter configured for the files to be touched.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Create the new public type that all stories' tests reference, so the test phase can compile against it. (Type declaration only — no notification behavior yet.)

- [X] T003 Create `src/main/java/jpnco/simula/actors/SupervisionListener.java`: `@FunctionalInterface` with `void statusChanged(Actor component, SimulaSupervisor.Status previous, SimulaSupervisor.Status current)`, Javadoc stating the synchronous any-thread invocation contract, the `null` previous convention, the thread-safety duty and the fault-isolation behavior (FR-002, contract doc)

**Checkpoint**: `mvn -o compile` passes; listener type importable from tests.

---

## Phase 3: User Story 1 - Observe status transitions as they happen (Priority: P1) — MVP

**Goal**: Every actual recorded transition notifies registered listeners synchronously after the map update with `(component, previous, current)`; no-change records notify nobody; no-listener behavior is unchanged (FR-002, FR-003, FR-004, FR-008; SC-001).

**Independent Test**: Attach a recording lambda, feed start/stop/no-change events, assert the exact notification sequence and arguments.

### Tests for User Story 1 (write FIRST, ensure they FAIL before implementation)

- [X] T010 [US1] Unit tests in `src/test/java/jpnco/simula/actors/SimulaSupervisorTest.java`: (a) first lifecycle event notifies `(component, null, STARTED)`; (b) stop after start notifies `(component, STARTED, STOPPED)`; (c) engine-stop event notifies the engine transition; (d) a repeated same-status event notifies nobody; (e) notification happens after the map update — inside the callback `getStates().get(component) == current`; (f) with no listener registered, processing all three topics records exactly as before and throws nothing (FR-008) (FR-002, FR-003, FR-004, FR-008, SC-001)

### Implementation for User Story 1

- [X] T011 [US1] In `src/main/java/jpnco/simula/actors/SimulaSupervisor.java`: add `private final List<SupervisionListener> listeners = new CopyOnWriteArrayList<>();` (field Javadoc, FR-006 rationale); change `record()` to capture `Status previous = states.put(component, status);` and call a new private `notifyListeners(component, previous, status)` iff `previous != status`; `notifyListeners` returns early on `listeners.isEmpty()` (R5 fast path) and iterates calling `statusChanged` (FR-003, FR-004; data-model N-TRANS/N-ORDER/N-FAST)

**Checkpoint**: US1 tests Green; existing supervision tests still Green.

---

## Phase 4: User Story 2 - Register and unregister listeners safely (Priority: P2)

**Goal**: Multiple listeners each notified; a throwing listener is isolated and logged; add/remove are safe concurrently with `process()` and from inside callbacks; removal takes effect for subsequent records (FR-001 add/remove, FR-005..FR-007; SC-002, SC-003).

**Independent Test**: Two listeners, first always throws → state recorded + error logged + second still notified; concurrent producers + add/remove stress completes clean.

### Tests for User Story 2 (write FIRST, ensure they FAIL before implementation)

- [X] T020 [P] [US2] Unit tests in `src/test/java/jpnco/simula/actors/SimulaSupervisorTest.java`: (a) two listeners both receive every notification with identical arguments; (b) first listener throws `RuntimeException` on every call → status still recorded, second listener still notified, processing never propagates the exception; (c) after `removeSupervisionListener`, no further notification arrives but a still-registered listener keeps receiving them (FR-005, FR-007, SC-002)
- [X] T021 [P] [US2] Stress unit test in `src/test/java/jpnco/simula/actors/SimulaSupervisorTest.java`: 3 producer threads calling `process()` with distinct components while a control thread adds/removes short-lived listeners and an observer iterates `getStates()`; assert no exception escapes anywhere and the recorded count equals the produced count (FR-006, SC-003) — reuse the executor/latch pattern of `concurrentRecordingAndReadingKeepsTheMapConsistent`

### Implementation for User Story 2

- [X] T022 [US2] In `src/main/java/jpnco/simula/actors/SimulaSupervisor.java`: add `public void addSupervisionListener(SupervisionListener listener)` (`Objects.requireNonNull` + `listeners.addIfAbsent`) and `public void removeSupervisionListener(SupervisionListener listener)` (`Objects.requireNonNull` + `listeners.remove`), with Javadoc citing FR-001, FR-006, FR-007 and the contract (any-thread safety, in-flight removal unspecified); wrap each `statusChanged` call in `notifyListeners` with `try/catch (RuntimeException e)` logging `Logger.error(this, ...)` (named message constant per Constitution VII) (FR-001, FR-005, FR-006, FR-007)

**Checkpoint**: US2 tests Green; fault isolation proven.

---

## Phase 5: User Story 3 - API is hard to misuse (Priority: P3)

**Goal**: `null` rejected at add/remove; double-add is a no-op (one notification per transition); removing an absent listener is silent (FR-001, FR-007; US3 scenarios).

**Independent Test**: `assertThrows(NullPointerException, ...)`, double-add notifies once, silent absent-remove.

### Tests for User Story 3 (write FIRST, ensure they FAIL before implementation)

- [X] T030 [US3] Unit tests in `src/test/java/jpnco/simula/actors/SimulaSupervisorTest.java`: (a) `addSupervisionListener(null)` and `removeSupervisionListener(null)` throw `NullPointerException` and leave the registry unchanged; (b) adding the same listener instance twice yields exactly one notification per transition; (c) removing a never-registered listener completes silently and the registered listeners keep working (FR-001, FR-007)

### Implementation for User Story 3

- [X] T031 [US3] Verify semantics already satisfied by T011/T022 (`addIfAbsent`, `remove`, `requireNonNull`); adjust only if a test exposes a gap; document the `equals`-based duplicate detection in the `addSupervisionListener` Javadoc (contract doc)

**Checkpoint**: All story tests Green.

---

## Phase 6: Polish & Docs

**Purpose**: Constitution VIII (architecture.md current) and user-facing docs.

- [X] T032 [P] Update `architecture.md`: supervision section documents the listener notification (present-state wording, Mermaid only if a diagram changes)
- [X] T033 [P] Update `README.md`: supervision usage example with `addSupervisionListener`, API reference entries for `SupervisionListener`, `addSupervisionListener`, `removeSupervisionListener`
- [X] T034 Run `mvn -o spotless:apply` then `mvn -o clean verify` — all tests pass, JaCoCo ≥97% line and branch, Spotless clean (Constitution II, V); walk quickstart.md manual scenario if convenient
- [X] T035 Mark completed tasks in this file and commit the feature (docs + code, English message referencing FR/SC)

---

## Dependencies

- T001–T002 (Setup) → T003 (Foundational) → US1 (T010 → T011) → US2 (T020/T021 → T022) → US3 (T030 → T031) → Polish (T032–T035)
- Within US phases: tests before implementation (Constitution I); [P] tasks touch the same test file — run them as one sequential edit batch

## Parallel Example

```
Phase 4 tests: T020 and T021 cover different scenarios but share the test file →
execute as sequential edits, single Red verification run.
```

## Implementation Strategy

MVP = Phase 1–3 (US1). US2 and US3 are incremental hardening on the same two files; Polish completes Constitution gates.
