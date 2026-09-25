---

description: "Task list for the Add Barrier Actor feature"
---

# Tasks: Add Barrier Actor

**Input**: Design documents from `/specs/003-add-barrier-actor/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), data-model.md, contracts/

**Tests**: This feature produces production code (the Barrier actor and BarrierMode enum), so test tasks ARE included. The project constitution (I. Test-First) requires tests written first, and (II. Coverage Standard) requires ≥97% line and branch coverage.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story. Because this is a small, focused addition, most tasks touch distinct files; the implementation is concentrated in `Barrier.java`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/main/java`, `src/test/java` at repository root (existing Maven module layout)

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented.

- [ ] T001 Create the `BarrierMode` enum in `src/main/java/jpnco/simula/actors/BarrierMode.java` with values `SINGLE_USE` and `CYCLIC` (FR-005, FR-006, FR-008, data-model.md; named literals per Constitution VII)
- [ ] T002 [P] Write the Javadoc for the `BarrierMode` enum and the `Barrier` class/methods citing the FR/SC identifiers they implement (Constitution VI)
- [ ] T003 [P] Update `src/main/java/jpnco/simula/actors/package-info.java` to document `Barrier` as a default actor (Constitution VI)
- [ ] T004 [P] Update `architecture.md` at the repo root to mention the `Barrier` actor in the actor model (Constitution VIII, Mermaid diagrams, no history)

**Checkpoint**: Foundation ready - user story implementation can now begin.

---

## Phase 2: User Story 1 - Notify the creator when all participants are ready (Priority: P1)

**Goal**: A Barrier with `participants = N` fires the complete topic once `N` actors signal ready on the ready topic (FR-002, FR-003, FR-004, SC-001).

**Independent Test**: Create a Barrier, have `N` actors signal ready on the ready topic, and assert the creator receives the complete event.

### Tests for User Story 1 (write FIRST, ensure they FAIL before implementation)

- [ ] T010 [P] [US1] Unit test: a Barrier constructed with `participants = N` subscribes to the ready topic, in `src/test/java/jpnco/simula/actors/BarrierTest.java`
- [ ] T011 [US1] Unit test: `N` distinct ready signals fire the complete topic (FR-004), in `src/test/java/jpnco/simula/actors/BarrierTest.java`
- [ ] T012 [US1] Unit test: fewer than `N` ready signals do not fire the complete topic (FR-003), in `src/test/java/jpnco/simula/actors/BarrierTest.java`
- [ ] T013 [US1] Unit test: the complete event is signaled with the Barrier as source (FR-004), in `src/test/java/jpnco/simula/actors/BarrierTest.java`

### Implementation for User Story 1

- [ ] T014 [US1] Create `src/main/java/jpnco/simula/actors/Barrier.java` implementing `Actor` with fields `participants`, `readyTopic`, `completeTopic`, `mode`, `distinct`, `count`, `readySources`, `id`, `delegate`, `engine`; constructor subscribes to `readyTopic` (FR-002, data-model.md)
- [ ] T015 [US1] Implement `process(Event)` in `Barrier.java` to count ready events on `readyTopic` and signal the complete topic when `count >= participants` (FR-003, FR-004)

**Checkpoint**: US1 fully functional and testable independently.

---

## Phase 3: User Story 2 - Single-use barrier deactivates after firing (Priority: P1)

**Goal**: A single-use Barrier fires once, then unsubscribes from the ready topic and no longer reacts (FR-005, SC-002).

**Independent Test**: A single-use Barrier fires once, then further ready events are ignored and the complete topic is not signaled again.

### Tests for User Story 2 (write FIRST, ensure they FAIL before implementation)

- [ ] T020 [P] [US2] Unit test: a single-use Barrier unsubscribes from the ready topic after firing (FR-005), in `src/test/java/jpnco/simula/actors/BarrierTest.java`
- [ ] T021 [US2] Unit test: after a single-use Barrier fires, further ready events do not signal the complete topic again (FR-005, SC-002), in `src/test/java/jpnco/simula/actors/BarrierTest.java`

### Implementation for User Story 2

- [ ] T022 [US2] In `Barrier.process(Event)` and/or `afterStart`, when `mode == SINGLE_USE` and the barrier fires, unsubscribe from the ready topic and stop reacting (FR-005) in `src/main/java/jpnco/simula/actors/Barrier.java`

**Checkpoint**: US1 AND US2 both work independently.

---

## Phase 4: User Story 3 - Cyclic barrier resets and can fire again (Priority: P2)

**Goal**: A cyclic Barrier resets after firing and can fire again on subsequent cycles (FR-006, SC-003).

**Independent Test**: A cyclic Barrier fires once, then the participants signal ready again and the complete topic fires a second time.

### Tests for User Story 3 (write FIRST, ensure they FAIL before implementation)

- [ ] T030 [P] [US3] Unit test: a cyclic Barrier resets its counter after firing (FR-006), in `src/test/java/jpnco/simula/actors/BarrierTest.java`
- [ ] T031 [US3] Unit test: a cyclic Barrier fires the complete topic once per completed cycle (FR-006, SC-003), in `src/test/java/jpnco/simula/actors/BarrierTest.java`

### Implementation for User Story 3

- [ ] T032 [US3] In `Barrier.process(Event)`, when `mode == CYCLIC` and the barrier fires, reset `count` (and `readySources` when `distinct`) and remain subscribed (FR-006) in `src/main/java/jpnco/simula/actors/Barrier.java`

**Checkpoint**: US1, US2 AND US3 all work independently.

---

## Phase 5: User Story 4 - Distinct participants are counted only once (Priority: P2)

**Goal**: When `distinct = true`, each distinct source counts only once; when `distinct = false`, each ready event counts (FR-007, FR-010, SC-004).

**Independent Test**: With `distinct = true` and `participants > 1`, a single actor signaling ready several times does not fire; with `distinct = false`, it does.

### Tests for User Story 4 (write FIRST, ensure they FAIL before implementation)

- [ ] T040 [P] [US4] Unit test: with `distinct = true` and `participants > 1`, one source signaling ready repeatedly counts once and does not fire (FR-007, SC-004), in `src/test/java/jpnco/simula/actors/BarrierTest.java`
- [ ] T041 [US4] Unit test: with `distinct = false` and `participants = N`, one source signaling ready `N` times fires (FR-007), in `src/test/java/jpnco/simula/actors/BarrierTest.java`
- [ ] T042 [US4] Unit test: the source of each ready event is the participant (FR-010), in `src/test/java/jpnco/simula/actors/BarrierTest.java`

### Implementation for User Story 4

- [ ] T043 [US4] In `Barrier.process(Event)`, when `distinct = true`, use `readySources` keyed on `event.getSource()` so a source counts once; when `distinct = false`, increment on every ready event (FR-007, FR-010) in `src/main/java/jpnco/simula/actors/Barrier.java`

**Checkpoint**: All four user stories independently functional.

---

## Phase 6: Constructor validation and edge cases

**Purpose**: Reject invalid construction arguments and invalid BarrierMode values with clear errors (FR-008, FR-009, SC-005).

### Tests (write FIRST, ensure they FAIL before implementation)

- [ ] T050 [P] Unit test: non-positive `participants` is rejected with a clear error (FR-009), in `src/test/java/jpnco/simula/actors/BarrierTest.java`
- [ ] T051 Unit test: null or blank `readyTopic` / `completeTopic` is rejected with a clear error (FR-009), in `src/test/java/jpnco/simula/actors/BarrierTest.java`
- [ ] T052 Unit test: null `mode` or null `distinct` (if boxed) is rejected with a clear error (FR-008, FR-009), in `src/test/java/jpnco/simula/actors/BarrierTest.java`

### Implementation

- [ ] T053 Add validation in the `Barrier` constructor for `participants >= 1`, non-null/non-blank topics, and non-null mode, throwing clear exceptions (FR-008, FR-009) in `src/main/java/jpnco/simula/actors/Barrier.java`

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Finalization, documentation, and constitution compliance.

- [ ] T060 [P] Verify the coverage gate: run `mvn -o verify` and confirm line and branch coverage each ≥ 97% (Constitution II, SC-006)
- [ ] T061 [P] Run `mvn -o spotless:apply` to format the new files (Constitution V)
- [ ] T062 Verify Javadoc on all new/changed methods cites the implemented FR/SC identifiers (Constitution VI)
- [ ] T063 Verify `architecture.md` uses Mermaid-only structural diagrams and contains no historical information (Constitution VIII)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies - can start immediately; BLOCKS all user stories
- **User Stories (Phase 2-5)**: All depend on Foundational completion
- **Constructor validation (Phase 6)**: Touches the same `Barrier.java`; sequence after the user-story implementation to avoid same-file conflicts
- **Polish (Phase 7)**: Depends on all implementation phases

### User Story Dependencies

- **US1 (P1)**: After Foundational; no dependency on other stories
- **US2 (P1)**: After Foundational; shares `Barrier.process` with US1 (sequential within the same file)
- **US3 (P2)**: After Foundational; shares `Barrier.process` with US1/US2
- **US4 (P2)**: After Foundational; shares `Barrier.process` with US1-3

### Parallel Opportunities

- Foundational tasks marked [P] can run in parallel
- Tests within a user story marked [P] can run in parallel
- US implementation tasks touch the same `Barrier.java` and MUST run sequentially

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Foundational (`BarrierMode` enum, Javadoc, package-info, architecture)
2. Complete Phase 2: User Story 1 (count + fire)
3. **STOP and VALIDATE**: `mvn -o test` - verify US1 independently
4. Proceed to US2, US3, US4, then constructor validation and polish

### Notes on same-file sequencing

- `Barrier.java` is edited by US1 (T014/015), US2 (T022), US3 (T032), US4 (T043), and Phase 6 (T053). These MUST run sequentially, not in parallel, to avoid conflicts.

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing (Red) per Constitution I
- Build offline with `mvn -o` in this environment (no network); verify coverage with `mvn -o verify`
- Commit after each task or logical group
- Stop at any checkpoint to validate independently
