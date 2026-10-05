---

description: "Task list for the Poisson Alarm feature"
---

# Tasks: Poisson Alarm

**Input**: Design documents from `/specs/007-poisson-alarm/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: The project constitution (I. Test-First, NON-NEGOTIABLE) requires tests written first, and (II. Coverage Standard) requires ≥97% line and branch coverage. Test tasks ARE included and MUST fail (Red) before their implementation tasks.

**Organization**: Tasks grouped by user story; US1 (Poisson request + re-arming firings) is the MVP, US2 (statistical fidelity via the pure draw helper) and US3 (regressions + refusals) layer on.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/main/java`, `src/test/java` at repository root (existing Maven module layout)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Pre-flight; no new structure and no pom change (TimeSource.java stays outside Spotless includes).

- [X] T001 Audit existing alarm request sites and tests for assumptions about `processRequestAlarm` parameter shapes (research.md R1, FR-005, SC-004) across `src/main/java/jpnco/simula/actors/TimeSource.java`, `src/test/java/jpnco/simula/actors/TimeSourceTest.java`, `src/main/java/jpnco/simula/actors/Barrier.java`, `src/test/java/jpnco/simula/actors/BarrierTest.java`; report findings (expected: none — new shape is additive)
- [X] T002 Confirm `mvn -o spotless:check` is green before touching `TimeSource.java` (legacy tabs preserved, Constitution V baseline)

**Checkpoint**: Baseline verified.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: None — the feature extends an existing component in place; all work lives in the user stories.

**Checkpoint**: Proceed directly to User Story 1.

---

## Phase 3: User Story 1 - Ask for a randomly recurring alarm (Priority: P1) — MVP

**Goal**: A Poisson-shaped request registers an alarm that fires at the requested first time, then re-arms after drawn exponential waits; seeded requests replay identically; clearing stops it (FR-001..FR-004, FR-007; SC-001, SC-003).

**Independent Test**: Seeded Poisson request on a running engine → irregular firings replayed identically on a second seeded run; clear stops firings.

### Tests for User Story 1 (write FIRST, ensure they FAIL before implementation)

- [X] T010 [US1] Unit/integration tests in `src/test/java/jpnco/simula/actors/TimeSourceTest.java`: (a) seeded Poisson request at a rate, subscribe a recording actor, run: at least 3 firings, first at the requested time, gaps not all equal, every gap >= 1 simulated unit; (b) same seed twice ⇒ identical firing-time sequences; (c) `CLEAR_ALARM_EVENT` on the topic before a firing stops further firings (FR-001..FR-004, SC-001, SC-003)

### Implementation for User Story 1

- [X] T011 [US1] In `src/main/java/jpnco/simula/actors/TimeSource.java`: add public constant `POISSON`; extend the inner `Alarm` with rate mode (`double rate`, owned `Random`, rate constructor, `isPeriodic()` true, `advance()` re-arms via the drawn wait); extend `processRequestAlarm` to detect the marker shape and build rate-mode alarms; add the package-private static helper `drawWait(double rate, Random random, int timeFactor)` = `max(1, round(timeFactor * -ln(1-U) / rate))` (FR-001, FR-002, FR-003; research R2/R3, data-model rules PA-*)

**Checkpoint**: US1 tests Green.

---

## Phase 4: User Story 2 - Statistical fidelity (Priority: P2)

**Goal**: Empirical mean of draws within 10 % of `timeFactor / rate` over >= 2000 seeded draws; dispersion present (FR-002; SC-002).

**Independent Test**: Call `drawWait` 2000 times with a fixed seed; assert mean tolerance and at least two distinct values (no wall-clock simulated time).

### Tests for User Story 2 (write FIRST, ensure they FAIL before implementation)

- [X] T020 [US2] Unit test in `src/test/java/jpnco/simula/actors/TimeSourceTest.java`: `TimeSource.drawWait(1.0, new Random(SEED), TIME_FACTOR)` over >= 2000 draws — mean within 10 % of `TIME_FACTOR`, >= 2 distinct values, all >= 1 (FR-002, SC-002, SC-003) (same file as T010: sequential)

### Implementation for User Story 2

- [X] T021 [US2] Verify the T011 helper satisfies the statistical assertions; tune rounding (`round`, not `floor`) only if the tolerance fails; no behavior redesign

**Checkpoint**: US2 tests Green.

---

## Phase 5: User Story 3 - No regression, clear errors (Priority: P3)

**Goal**: Existing alarm shapes untouched; malformed Poisson requests refused with a logged error and no registration (FR-005, FR-006; SC-004).

**Independent Test**: Pre-existing alarm tests still green; refusals leave the alarm set unchanged and log.

### Tests for User Story 3 (write FIRST, ensure they FAIL before implementation)

- [X] T030 [US3] Unit tests in `src/test/java/jpnco/simula/actors/TimeSourceTest.java`: (a) Poisson marker with rate `0.0`, with rate `-1.0`, with a non-`Number` seed → no alarm registered (topic never fires), error logged, time source still alive; (b) `Double` period without marker → refused, not treated as rate; (c) existing one-shot and fixed-period requests behave unchanged (FR-005, FR-006)

### Implementation for User Story 3

- [X] T031 [US3] In `src/main/java/jpnco/simula/actors/TimeSource.java`: validation branches in `processRequestAlarm` with `Logger.error` + named message constants (Constitution VII): invalid rate/seed → log and return, map untouched

**Checkpoint**: All story tests Green.

---

## Phase 6: Polish & Docs

- [X] T032 [P] Update `README.md` Alarms section: Poisson request form with `TimeSource.POISSON`, rate and optional seed, clear semantics (contract doc)
- [X] T033 [P] Update `architecture.md` alarm model/section: rate mode as present-state (Mermaid only if a diagram changes)
- [X] T034 Run `mvn -o clean verify` — all tests pass, JaCoCo >= 97% line and branch, Spotless clean; walk the quickstart manual scenario if convenient
- [X] T035 Mark completed tasks in this file and commit the feature (docs + code, English message referencing FR/SC)

---

## Dependencies

- T001–T002 (Setup) → US1 (T010 → T011) → US2 (T020 → T021) → US3 (T030 → T031) → Polish (T032–T035)
- All test tasks touch `TimeSourceTest.java` → sequential edit batches, one Red verification per story

## Parallel Example

```
Phase 6: T032 (README) and T033 (architecture.md) are independent documents → parallel.
```

## Implementation Strategy

MVP = Phase 1–3 (US1): Poisson alarms usable end-to-end. US2 proves the law in-process; US3 closes safety. Polish completes Constitution gates.
