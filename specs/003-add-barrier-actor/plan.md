# Implementation Plan: Add Barrier Actor

**Branch**: `003-add-barrier-actor` | **Date**: 2026-09-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-add-barrier-actor/spec.md`

## Summary

Add a **Barrier** actor to the simula simulation framework (in the `jpnco.simula.actors` package, alongside `Logger` and `TimeSource`). The Barrier coordinates a set of participants by counting "ready" signals signaled on a ready topic; once the count reaches the configured participant count it signals a complete topic to notify the creator. It supports single-use and cyclic modes and an optional distinct flag so that a participant signaling ready several times counts only once. The change must preserve observable behavior of the framework, satisfy the constitution's 97% line and branch coverage gate, and follow the established actor patterns (delegation via `ActorDelegate`).

Primary requirements: FR-001..FR-010; success criteria SC-001..SC-006.

## Technical Context

**Language/Version**: Java 23 (pom.xml: `maven.compiler.source`/`target` = 23; module `Simula` in `module-info.java`)

**Primary Dependencies**: Runtime — JDK platform only (no external runtime dependencies). Test — JUnit Jupiter 5.14.0, Mockito 5.22.0 (core + junit-jupiter), via Maven Surefire with Mockito inline javaagent.

**Storage**: N/A (in-memory framework; no persistence)

**Testing**: Maven + JUnit 5 + Mockito. JaCoCo is configured and enforces the constitution's 97% line and branch coverage gate (from feature 002). Tests are written first (Constitution I) and must cover the new actor to reach 97%.

**Target Platform**: JVM (Java 21+ required for virtual threads; project targets Java 23), cross-platform

**Project Type**: Library / framework (developer-facing simulation framework)

**Performance Goals**: The Barrier must count and fire deterministically for a configured number of participants (SC-001..SC-005).

**Constraints**:
- New actor lives in `jpnco.simula.actors` (already exported by `module-info.java`; no module change required).
- Use the standard delegation pattern (`ActorDelegate.createDelegate(engine, this)`) for the event loop, like `Logger`.
- `BarrierMode` is an enum (consistent with the existing `ExecutionMode` enum convention), fixed at construction.
- No functional regression; identical observable behavior for existing actors (FR of feature 002 remain green).
- Constitution compliance (see Constitution Check).

**Scale/Scope**: One new actor (`Barrier`), one new enum (`BarrierMode`), one new test class, plus a doc update to `package-info.java` and the actors documentation.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Gate | Status |
|---|-----------|------|--------|
| I | Test-First (NON-NEGOTIABLE) | Tests written → approved → fail (Red) → implement (Green) | PASS (workflow) |
| II | Coverage ≥97% line + branch | Measured by JaCoCo (from feature 002) | PASS (verify with `mvn -o verify`) |
| III | English code | All identifiers/literals/comments English | PASS (new code) |
| IV | English comments | Comments explain intent | PASS |
| V | Formatting | Spotless (google-java-format) enforced | PASS (run `mvn -o spotless:apply`) |
| VI | Documentation | Javadoc for every package/class/method citing FR-###/SC-### | PASS (new code; update package-info) |
| VII | Named Literals (NON-NEGOTIABLE) | No raw literals except -1/0/1 | PASS |
| VIII | Architecture doc | `architecture.md` at repo root, Mermaid diagrams, no history | PASS (update to mention Barrier) |

## Project Structure

```text
src/main/java/jpnco/simula/actors/
├── Barrier.java         # NEW - the barrier actor (Actor impl, delegate pattern)
├── BarrierMode.java     # NEW - enum { SINGLE_USE, CYCLIC }
├── package-info.java    # UPDATE - document Barrier as a default actor
├── Logger.java          # (unchanged)
└── TimeSource.java      # (unchanged)

src/test/java/jpnco/simula/actors/
└── BarrierTest.java     # NEW - tests for the barrier actor

architecture.md          # UPDATE - mention Barrier in the actor model
specs/003-add-barrier-actor/
├── spec.md              # This feature's spec
├── plan.md              # This file
├── data-model.md        # Phase 1 output
├── tasks.md             # Phase 2 output
└── contracts/barrier.md # Phase 1 output - public contract of Barrier
```

**Structure Decision**: Add `Barrier` and `BarrierMode` as new files in the existing `jpnco.simula.actors` package. No new modules or source trees are warranted. The package is already exported, so no `module-info.java` change is needed.

## Key Decisions

1. **Delegation pattern**: `Barrier` implements `Actor` and delegates its event loop to `ActorDelegate.createDelegate(engine, this)`, exactly like `Logger`. The counting/firing logic lives in `process(Event)`.
2. **Enum `BarrierMode`**: a separate top-level enum file in the same package, mirroring the `ExecutionMode` convention in `engine/`. Values `SINGLE_USE` and `CYCLIC`.
3. **Firing condition**: the Barrier counts ready events; when `count >= participants` it signals the complete topic via `engine.signal(EventImpl.createEvent(completeTopic, this))`.
4. **Single-use behavior**: after firing, the Barrier unsubscribes from the ready topic (`engine.unsubscribe(this, readyTopic)`) and stops reacting.
5. **Cyclic behavior**: after firing, the Barrier resets its counter (and its distinct-source set when `distinct`) and remains subscribed so it can fire again.
6. **Distinct counting**: when `distinct = true`, the Barrier keeps a `Set<Actor>` of the sources that have signaled ready (from `event.getSource()`) and counts only new sources; when `distinct = false`, every ready event increments the counter.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none) | — | — |

No constitutional violations are being introduced.

## Tests

The constitution requires test-first (Red) and ≥97% line and branch coverage. The tests target the new `Barrier` and `BarrierMode`:

- Constructor validation (FR-008, FR-009, SC-005): non-positive participants, null/blank topics, null mode, null distinct → clear error.
- Counting and firing (FR-003, FR-004, US1): `participants = N` with `N` distinct ready signals → complete topic signaled; fewer than `N` → not signaled.
- Single-use deactivation (FR-005, US2, SC-002): fires once, unsubscribes, further ready events ignored.
- Cyclic reset and refire (FR-006, US3, SC-003): fires, resets, fires again on next cycle.
- Distinct counting (FR-007, US4, SC-004): `distinct = true` single source repeated → counts once; `distinct = false` repeated source → counts each time.
- Source identification (FR-010): the source of each ready event is the participant.

## Quickstart / Validation

The feature is a framework capability; validation runs through the unit tests and the build gate. After implementation:

```text
mvn -o verify          # runs tests + JaCoCo check (97% line + branch)
mvn -o spotless:apply  # formats new files (Constitution V)
```
