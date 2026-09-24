# Implementation Plan: Configurable Thread Execution

**Branch**: `002-configurable-threads` | **Date**: 2026-09-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-configurable-threads/spec.md`

## Summary

Add a per-engine execution-mode configuration to the simula simulation framework so actors can run on either **virtual threads** (default) or **classic platform threads**, selected via the engine constructor. In parallel, replace every `synchronized` method/block in the framework (all 14 usages across `EngineImpl` and `IdBuilder`) with explicit **`ReentrantLock`** to provide uniform, explicit lock semantics. The change applies across the whole framework, must preserve observable behavior, and must satisfy the constitution's 97% line and branch coverage gate (requires adding coverage tooling, which is currently absent).

Primary requirements: FR-001..FR-013; success criteria SC-001..SC-008.

## Technical Context

**Language/Version**: Java 23 (pom.xml: `maven.compiler.source`/`target` = 23; module `Simula` in `module-info.java`)

**Primary Dependencies**: Runtime — JDK platform only (no external runtime dependencies). Test — JUnit Jupiter 5.12.0, Mockito 5.16.0 (core + junit-jupiter), via Maven Surefire with Mockito inline javaagent.

**Storage**: N/A (in-memory framework; no persistence)

**Testing**: Maven + JUnit 5 + Mockito. **GAP**: no coverage tool is configured (no JaCoCo), so the constitution's 97% line+branch gate cannot currently be measured — a coverage plugin must be added in Phase 0 research and wired into the build.

**Target Platform**: JVM (Java 21+ required for virtual threads; project targets Java 23), cross-platform

**Project Type**: Library / framework (developer-facing simulation framework)

**Performance Goals**: Virtual-thread mode must start and stop successfully with a high number of actors without exhausting OS threads (SC-006); behavior must be equivalent across modes (SC-003).

**Constraints**:
- Default execution mode = virtual threads (FR-002); classic threads selected explicitly (FR-003, via engine constructor)
- Whole-framework scope: all `synchronized` usages (EngineImpl + IdBuilder) and all actor thread creation (FR-010)
- No functional regression; identical observable behavior (FR-013, SC-003)
- ReentrantLock must be released on all paths (no deadlocks/stuck state); preserve lock ordering where `synchronized` nested locks exist (EngineImpl `run()` nested `actors` then `children`)
- Constitution compliance (see Constitution Check)

**Scale/Scope**: Whole framework — `EngineImpl` (12 `synchronized` usages) and `IdBuilder` (1 static `synchronized`), plus actor thread creation in `EngineImpl.start(Actor)` (currently `new Thread(...)`). Test suite must cover all affected paths to reach 97% line + branch.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Gate | Status |
|---|-----------|------|--------|
| I | Test-First (NON-NEGOTIABLE) | Tests written → approved → fail (Red) → implement (Green) | PASS (workflow) |
| II | Coverage ≥97% line + branch | Requires coverage measurement | **RESOLVED (JaCoCo added per research.md) → PASS** |
| III | English code | All identifiers/literals/comments English | PASS (existing + new) |
| IV | English comments | Comments explain intent | PASS |
| V | Formatting | Automated formatter enforced | **RESOLVED (scoped formatter per research.md) → PASS** |
| VI | Documentation | Javadoc for every package/class/method citing FR-###/SC-### | PASS (new code) |
| VII | Named Literals (NON-NEGOTIABLE) | No raw literals except -1/0/1 | PASS |
| VIII | Architecture doc | `architecture.md` per branch, Mermaid diagrams, no history | **RESOLVED (created in this feature) → PASS** |

Post-design re-check: gates II and V are resolved in Phase 0 (research.md decisions on JaCoCo + scoped formatter), and gate VIII is satisfied by the `architecture.md` created in this feature's scope. All constitution gates now pass for this feature.

## Project Structure

### Documentation (this feature)

```text
specs/002-configurable-threads/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

The feature reuses the existing single-module Maven layout (Option 1). No new source directories are required; the work is an in-place refactor of existing files plus test additions.

```text
src/
├── main/java/
│   ├── module-info.java
│   └── jpnco/simula/
│       ├── Actor.java
│       ├── Engine.java
│       ├── Event.java
│       ├── PriorityActor.java
│       ├── TimedActor.java
│       ├── actors/
│       │   ├── Logger.java
│       │   └── TimeSource.java
│       └── engine/
│           ├── ActorDelegate.java
│           ├── EngineImpl.java   # thread creation + synchronized → ReentrantLock
│           ├── EventImpl.java
│           └── IdBuilder.java    # synchronized → ReentrantLock

src/test/java/jpnco/simula/
└── (existing unit tests + new tests for execution mode & locking)

architecture.md                      # NEW (constitution VIII) - per branch, Mermaid diagrams
pom.xml                              # + coverage plugin (JaCoCo) and formatter if adopted
```

**Structure Decision**: Reuse the existing single-module Maven project (Option 1). The feature is an in-place refactor of `EngineImpl` and `IdBuilder` plus new tests; no new modules or source trees are warranted. A coverage plugin (JaCoCo) is added to `pom.xml` to satisfy constitution gate II. A new `architecture.md` (constitution VIII) is created to document the framework architecture with Mermaid diagrams.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none) | — | — |

No constitutional violations are being introduced; the only unmet gates are pre-existing infrastructure gaps (coverage tooling, formatter, missing architecture doc) which are resolved, not waived.

---

# Addendum: Traffic-Light Illustrative Example

**Branch**: `002-configurable-threads` | **Spec**: [spec.md](spec.md)

**Input**: User request for a substantial, runnable sample illustrating the simula framework and the configurable-threads feature delivered above.

## Summary

Add a self-contained, runnable **traffic-light simulation** under `jpnco.simula.examples.trafficlight` that exercises the framework end-to-end: two intersections (child engines), per-intersection traffic-light and vehicle-sensor actors, an aggregating monitor, event subscription/signaling, `TimeSource` alarms, and the newly configurable **virtual vs classic** execution mode (FR-001..FR-006). The same scenario runs under both modes to illustrate behavioral equivalence (FR-005, SC-003). The example is illustrative (not unit-tested) and is therefore **excluded from the JaCoCo coverage gate** so the framework bundle keeps its 97% line/branch requirement (Constitution II).

## Technical Context (addendum)

**Language/Version**: Java 25 (pom.xml `maven.compiler.source`/`target` = 25; module `Simula` in `module-info.java`)

**Primary Dependencies**: Runtime — JDK platform only. Test — JUnit Jupiter 5.14.0, Mockito 5.22.0 (existing).

**Storage**: N/A (in-memory framework; no persistence)

**Testing**: The example is a runnable demo, not a unit-test target. It is excluded from the JaCoCo `check` rule so the 97% gate on the framework bundle is preserved. It is validated end-to-end via `mvn -o verify` (build intact) plus manual `exec:java` runs in both modes.

**Target Platform**: JVM (Java 21+ for virtual threads; project targets Java 25)

**Project Type**: Library / framework with an illustrative sample (`main` class)

**Constraints**:
- Example must use the public framework API only (no changes to framework internals except the agreed JaCoCo `excludes` and `exec` plugin in `pom.xml`).
- Example must demonstrate both execution modes without altering the default.
- English code/comments/Javadoc, named constants (Constitution III, IV, VI, VII).
- Framework bundle coverage gate must remain ≥97% (exclude `jpnco/simula/examples/**` from JaCoCo `check`).

## Constitution Check (addendum)

| # | Principle | Gate | Status |
|---|-----------|------|--------|
| II | Coverage ≥97% | Example excluded from JaCoCo `check`; framework bundle unchanged | PASS (justified) |
| VI | Documentation | Javadoc on example classes/methods citing FR/SC | PASS |
| VII | Named Literals | All durations/periods/topics as named constants | PASS |

All gates pass; the JaCoCo exclusion is a deliberate, documented decision so the illustrative sample does not erode the framework coverage gate.

## Project Structure (addendum)

```text
src/main/java/jpnco/simula/examples/trafficlight/
├── TrafficLightDemo.java       # main - orchestrates 2 intersections, runs in a mode
├── TrafficLight.java           # actor - cycles RED/GREEN/ORANGE via TimeSource alarms
├── VehicleSensor.java          # actor - counts vehicles on a VEHICLE event
├── IntersectionController.java # actor - per-intersection state (light + count)
└── TrafficMonitor.java         # actor - aggregates both intersections, prints on TIME_EVENT

pom.xml                         # + JaCoCo check excludes for examples/**, + exec-maven-plugin
module-info.java                # (unchanged unless required for java -m execution)
```

## Key Decisions (addendum)

1. **Runnable demo not a test target**: example code lives in `src/main/java` under `examples/` and is excluded from the JaCoCo `check` rule (option a, user-approved). Rationale: an illustrative sample is demonstration code, not framework logic; including it in coverage would force either extra tests (defeating the "sample" purpose) or dropping coverage.
2. **Execution mechanism**: run via `mvn -o exec:java -Dexec.args=<mode>` using `exec-maven-plugin` (3.6.3, cached in `~/.m2`). Fallback: `java -p target/classes -m Simula/jpnco.simula.examples.trafficlight.TrafficLightDemo <mode>`.
3. **Two intersections under one root engine**: demonstrates child engines, `signalToChildren`, and start/stop cascade (FR-008) in a compact scenario.
4. **TimeSource alarms** drive the traffic-light cycle; `TIME_EVENT` drives the monitor output — illustrates the simulated-clock mechanism.
5. **Same scenario, both modes**: `TrafficLightDemo` runs the scenario once per selected mode and prints an equivalent outcome summary (FR-005, SC-003).
