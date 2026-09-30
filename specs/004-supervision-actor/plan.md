# Implementation Plan: Add Supervision Actor

**Branch**: `004-supervision-actor` | **Date**: 2026-09-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-supervision-actor/spec.md`

## Summary

Add a **SimulaSupervisor** to the simula simulation framework (in the `jpnco.simula.actors` package, alongside `Logger`, `TimeSource`, and `Barrier`). The supervision actor observes the lifecycle of the actors and engines in a simulation and exposes a queryable state of what has started and stopped. To observe **per-actor startup**, a new lifecycle event `STARTED_ACTOR_EVENT` is introduced, symmetric to the existing `STOPPED_ACTOR_EVENT`. The built-in actors (via standard delegation), the engine, and the time source emit it for themselves; actors created by external projects are not forced to emit it — the contract is documented instead. The change must preserve observable behavior of the framework, satisfy the constitution's 97% line and branch coverage gate, and follow the established actor patterns.

Primary requirements: FR-001..FR-010; success criteria SC-001..SC-006.

## Technical Context

**Language/Version**: Java 25 (pom.xml: `maven.compiler.source`/`target` = 25; module `Simula` in `module-info.java`)

**Primary Dependencies**: Runtime — JDK platform only (no external runtime dependencies). Test — JUnit Jupiter 5.14.0, Mockito 5.22.0 (core + junit-jupiter), via Maven Surefire with Mockito inline javaagent.

**Storage**: N/A (in-memory framework; no persistence)

**Testing**: Maven + JUnit 5 + Mockito. JaCoCo 0.8.15 enforces the constitution's 97% line and branch coverage gate. Tests are written first (Constitution I) and must cover the new actor and the new event emission to reach 97%.

**Target Platform**: JVM (Java 21+ required for virtual threads; project targets Java 25), cross-platform

**Project Type**: Library / framework (developer-facing simulation framework)

**Performance Goals**: The supervision actor must record lifecycle events deterministically and add negligible overhead; it must not change the observable outcomes of the observed simulation (FR-008, SC-005).

**Constraints**:
- New actor lives in `jpnco.simula.actors`; the new event constant lives on the existing `Engine` interface in the already-exported `jpnco.simula` package (no `module-info.java` change expected).
- Use the standard delegation pattern (`ActorDelegate.createDelegate(engine, this)`) for the supervision actor's event loop, like `Logger` and `Barrier`.
- The new `STARTED_ACTOR_EVENT` is symmetric to the existing `STOPPED_ACTOR_EVENT`.
- Emission of `STARTED_ACTOR_EVENT` by the built-in actors, engine, and time source must not alter their observable behavior (FR-008).
- No functional regression; identical observable behavior for existing actors.
- Constitution compliance (see Constitution Check).

**Scale/Scope**: One new actor (`SimulaSupervisor`), one new event constant (`STARTED_ACTOR_EVENT` on `Engine`), emission additions in three existing components (`ActorDelegate`, `TimeSource`, `EngineImpl`), two accessors on `Engine`/`EngineImpl` exposing the child engines (`getChildren()`, FR-011) and the actors (`getActors()`, FR-012) as non-modifiable ordered lists, documentation updates, plus test classes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Gate | Status |
|---|-----------|------|--------|
| I | Test-First (NON-NEGOTIABLE) | Tests written → approved → fail (Red) → implement (Green) | PASS (workflow) |
| II | Coverage ≥97% line + branch | Measured by JaCoCo (from feature 002) | PASS (verify with `mvn -o verify`) |
| III | English code | All identifiers/literals/comments English | PASS (new code) |
| IV | English comments | Comments explain intent | PASS |
| V | Formatting | Spotless (google-java-format) enforced | PASS (run `mvn -o spotless:apply`) |
| VI | Documentation | Javadoc for every package/class/method citing FR-###/SC-### | PASS (new code; update package-info and `Engine`/`Actor` Javadoc) |
| VII | Named Literals (NON-NEGOTIABLE) | No raw literals except -1/0/1 | PASS |
| VIII | Architecture doc | `architecture.md` at repo root, Mermaid diagrams, no history | PASS (update to mention the supervision actor and the new lifecycle event) |

## Project Structure

### Documentation (this feature)

```text
specs/004-supervision-actor/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/main/java/jpnco/simula/
├── Engine.java                        # UPDATE - add STARTED_ACTOR_EVENT constant + Javadoc
├── Actor.java                         # UPDATE - Javadoc for lifecycle + external-actor contract
├── actors/
│   ├── SimulaSupervisor.java          # NEW - the supervision actor
│   ├── package-info.java              # UPDATE - document SimulaSupervisor as a default actor
│   ├── Logger.java                    # (unchanged)
│   ├── TimeSource.java                # UPDATE - emit STARTED_ACTOR_EVENT for itself
│   └── Barrier.java                   # (unchanged)
└── engine/
    ├── ActorDelegate.java             # UPDATE - emit STARTED_ACTOR_EVENT on standard actor start
    ├── EngineImpl.java                # UPDATE - emit STARTED_ACTOR_EVENT for itself
    └── (others unchanged)

src/test/java/jpnco/simula/
├── actors/SimulaSupervisorTest.java   # NEW - tests for the supervision actor
├── engine/ActorDelegateTest.java      # UPDATE - tests for startup emission
├── engine/EngineImplTest.java         # UPDATE - tests for engine startup emission
└── actors/TimeSourceTest.java         # UPDATE - tests for time-source startup emission

architecture.md                        # UPDATE - mention SimulaSupervisor + STARTED_ACTOR_EVENT
```

**Structure Decision**: Add `SimulaSupervisor` to the existing `jpnco.simula.actors` package and `STARTED_ACTOR_EVENT` to the existing `Engine` interface. No new modules or source trees are warranted; the packages are already exported, so no `module-info.java` change is needed.

## Key Decisions

1. **Delegation pattern**: `SimulaSupervisor` implements `Actor` and delegates its event loop to `ActorDelegate.createDelegate(engine, this)`, exactly like `Logger` and `Barrier`. The recording/state logic lives in `process(Event)`.
2. **New lifecycle event**: `STARTED_ACTOR_EVENT` added to the `Engine` interface, symmetric to `STOPPED_ACTOR_EVENT` (FR-001). Its source is the actor that started.
3. **Startup emission — standard actors**: `ActorDelegate` emits `STARTED_ACTOR_EVENT` (source = delegator) when a standard actor transitions into the running state (on receiving `START_EVENT`, after the start hook) (FR-005).
4. **Startup emission — time source**: `TimeSource` (which has no delegate) emits `STARTED_ACTOR_EVENT` for itself when its own run loop starts (FR-005).
5. **Startup emission — engine**: `EngineImpl` (an engine is itself an actor) emits `STARTED_ACTOR_EVENT` for itself when its run loop starts (FR-005).
6. **External-actor contract**: external actors that do not use standard delegation are not forced to emit the notification; the contract (how and when) is documented in the `Actor`/`Engine` Javadoc and in `architecture.md` (FR-007).
7. **Queryable state**: the supervision actor keeps a map of observed components to their lifecycle status (`STARTED`/`STOPPED`), updated in the sequence notifications are received; a stop without a prior start is recorded as `STOPPED` without error (FR-006, FR-009, FR-010).
8. **Non-intrusive**: the supervision actor observes by subscription only and never posts to, stops, or modifies observed components (FR-008).
9. **Expose engine children**: `Engine` exposes `getChildren()` returning a non-modifiable, ordered `List<Engine>` snapshot of the child engines; `EngineImpl.children` becomes a `LinkedHashSet` to preserve insertion order, guarded by the engine lock (FR-011, 002-FR-012).
10. **Expose engine actors**: `Engine` exposes `getActors()` returning a non-modifiable, ordered `List<Actor>` snapshot of the registered actors, ordered by id (FR-012).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none) | — | — |

No constitutional violations are being introduced.

## Tests

The constitution requires test-first (Red) and ≥97% line and branch coverage. The tests target the new `SimulaSupervisor`, the new `STARTED_ACTOR_EVENT`, and its emission points:

- Supervision actor construction and subscription (FR-002): null engine rejected; subscribes to the lifecycle events.
- Startup observation (FR-001, FR-005, US1, SC-002): a standard actor starting emits `STARTED_ACTOR_EVENT` that the supervision actor records as `STARTED`.
- Stop observation (FR-003, FR-004, US2, SC-003): actor stops (`STOPPED_ACTOR_EVENT`) and child-engine stops (`STOPPED_ENGINE_EVENT`) are recorded.
- Queryable state (FR-006, FR-009, US3, SC-004): state matches the notification sequence; stop-without-start recorded as `STOPPED` without error (FR-010).
- No behavioral change (FR-008, SC-005): an identical scenario yields the same observable outcomes with and without a supervision actor.
- Coverage gate (Constitution II, SC-006): `mvn -o verify` reports ≥97% line and branch coverage.

## Quickstart / Validation

The feature is a framework capability; validation runs through the unit tests and the build gate. After implementation:

```text
mvn -o verify          # runs tests + JaCoCo check (97% line + branch)
mvn -o spotless:apply  # formats new files (Constitution V)
```

See [quickstart.md](quickstart.md) for the end-to-end validation scenarios.
