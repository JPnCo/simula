# Implementation Plan: Forbid Actor Restart

**Branch**: `005-forbid-actor-restart` | **Date**: 2026-09-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-forbid-actor-restart/spec.md`

## Summary

Make the stopped state of an actor terminal: the engine refuses to (re-)register an actor instance that has already stopped (FR-001..FR-005) or that is currently registered (FR-008), with a logged error and an `IllegalArgumentException`, leaving engine state untouched (FR-003). The stopped status is reported by the actor itself via a new defaulted `Actor.isStopped()` method backed by a flag in the standard `ActorDelegate` (FR-004), and a weak identity-based memory of instances unregistered by `EngineImpl` backstops actors with custom delegates (FR-005, FR-006). No new dependency, no public API breakage.

## Technical Context

**Language/Version**: Java 25 (`maven.compiler.source/target = 25`; module `simula`)

**Primary Dependencies**: None new — existing JDK (`java.util.concurrent`, `java.util.WeakHashMap`), framework-internal only

**Storage**: N/A (in-memory simulation framework)

**Testing**: JUnit Jupiter 5.14.0 + Mockito 5.22.0 via Maven Surefire; JaCoCo gates at ≥97% line and branch coverage

**Target Platform**: JVM 25+ (virtual threads by default); built offline with `mvn -o`

**Project Type**: Library (single Maven module, `jpnco:simula`)

**Performance Goals**: Guard check on registration is O(1); no measurable overhead on normal actor lifecycle

**Constraints**: No unbounded memory growth from stopped-actor bookkeeping (FR-006); no `synchronized` monitors (virtual-thread pinning — see architecture.md); all locking via the existing per-engine `ReentrantLock`

**Scale/Scope**: 4 production files modified (`Actor.java`, `ActorDelegate.java`, `EngineImpl.java`, Javadoc touches on `Engine.java`), 2 test files extended, `pom.xml` Spotless includes, `README.md` and `architecture.md` updates

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Test-First | Tests written first per user story, verified Red before Green | PASS (enforced in tasks phase) |
| II. Coverage ≥97% line & branch | `mvn -o clean verify` JaCoCo check | PASS (design is small and fully testable) |
| III. English code | All identifiers/messages in English | PASS |
| IV. English comments | Javadoc/comments explain rationale | PASS |
| V. Formatting | Spotless (`googleJavaFormat`); modified files must be in `<includes>` (`Actor.java`, `ActorDelegate.java`, `EngineImpl.java`, `ActorDelegateTest.java` to add) | PASS with include updates |
| VI. Javadoc + FR/SC citation | `isStopped()`, `register()`/`unregister()` changes cite FR-001..FR-008 | PASS |
| VII. Named literals | Exception message template declared as `static final` constant | PASS (design decision R5) |
| VIII. architecture.md current, Mermaid-only, no history | Lifecycle/guard documented as present-state | PASS |

**Post-design re-check (Phase 1 complete)**: no violations; no Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/005-forbid-actor-restart/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── registration-guard.md  # Phase 1 output (library API contract delta)
└── tasks.md             # Phase 2 output (/speckit.tasks - NOT created here)
```

### Source Code (repository root)

```text
src/
├── main/java/
│   ├── module-info.java
│   └── jpnco/simula/
│       ├── Actor.java                      # MODIFY - default isStopped() (FR-004)
│       ├── Engine.java                     # MODIFY - Javadoc: registerAndStart refusal contract (FR-001..FR-003, FR-008)
│       └── engine/
│           ├── ActorDelegate.java          # MODIFY - volatile stopped flag set in run() finally (FR-004)
│           └── EngineImpl.java             # MODIFY - registration guard + weak stopped-instance memory (FR-001..FR-003, FR-005..FR-008)
└── test/java/
    └── jpnco/simula/
        ├── actors/                         # (unchanged this feature)
        └── engine/
            ├── ActorDelegateTest.java      # MODIFY - isStopped lifecycle tests (FR-004)
            └── EngineImplCoverageTest.java # MODIFY - refusal/no-regression tests (FR-001..FR-008, SC-001..SC-005)

architecture.md                             # UPDATE - terminal stopped state, registration guard
README.md                                   # UPDATE - "Stopping an actor" + best practices + API reference
pom.xml                                     # UPDATE - Spotless includes for modified files
```

**Structure Decision**: Existing single-module layout is kept; the feature is a localized behavior change in the registration path (`EngineImpl.register`) plus one defaulted interface method — no new packages, directories, or dependencies.

## Key Design Decisions

1. **Single guard point** — `EngineImpl.register(Actor)` is the only code path that mutates the registered-actor map; both refusals (stopped, currently-registered) are checked there under the existing engine lock, before any mutation, so FR-003 holds for every public entry point.
2. **Actor-side terminal flag** — `Actor.isStopped()` is a default method returning `false` (no breakage); `ActorDelegate` overrides it and sets a `volatile boolean stopped` exactly once in the `finally` of `run()`, i.e. after the stop/crash paths have signaled `STOPPED_ACTOR_EVENT`.
3. **Weak engine-side backstop** — `EngineImpl` keeps `stoppedInstances = Collections.newSetFromMap(new WeakHashMap<Actor, Boolean>())`, accessed under the existing `ReentrantLock` (never `synchronized`), added in `unregister()` on the successful-removal branch. Entries vanish with the instance — FR-006.
4. **Double-registration refusal** — `register()` rejects `actors.containsKey(actor.getId())` with the same log+exception (FR-008); no legitimate internal flow registers an already-registered instance.
5. **Rejection style** — `Logger.error(this, ...)` followed by `IllegalArgumentException` with a named-constant message template (user decision; Constitution VII).
