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
