# Implementation Plan: Supervision Listener

**Branch**: `006-supervision-listener` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-supervision-listener/spec.md`

## Summary

Add push-based observation to `SimulaSupervisor`: a public `SupervisionListener` functional interface with a single callback `statusChanged(component, previous, current)` (FR-002), `addSupervisionListener`/`removeSupervisionListener` APIs with null rejection, identity no-duplicate and silent-removal semantics (FR-001, FR-007), and synchronous per-transition notification of every registered listener after the state map update (FR-003, FR-004). Listener exceptions are caught and logged per listener (FR-005); listener management is safe concurrently with `process()` (FR-006) via a copy-on-write list. No behavior change when no listener is registered (FR-008). No new dependency.

## Technical Context

**Language/Version**: Java 25 (`maven.compiler.source/target = 25`; module `simula`)

**Primary Dependencies**: None new — existing JDK (`java.util.concurrent.CopyOnWriteArrayList`)

**Storage**: N/A (in-memory simulation framework)

**Testing**: JUnit Jupiter 5.14.0 + Mockito 5.22.0 via Maven Surefire; JaCoCo gates at ≥97% line and branch coverage

**Target Platform**: JVM 25+ (virtual threads by default); built offline with `mvn -o`

**Project Type**: Library (single Maven module, `jpnco:simula`)

**Performance Goals**: Zero allocation and one null-check fast path per record when no listener is registered; COW list snapshots make notification lock-free

**Constraints**: No `synchronized` monitors (virtual-thread pinning — see architecture.md); notification is synchronous in the recording thread (no ordering/async guarantee); FR-008 zero-regression when unused

**Scale/Scope**: 1 new production file (`SupervisionListener.java`), 1 modified production file (`SimulaSupervisor.java`), 1 test file extended (`SimulaSupervisorTest.java`), `pom.xml` Spotless include, `README.md` and `architecture.md` updates

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Test-First | Tests written first per user story, verified Red before Green | PASS (enforced in tasks phase) |
| II. Coverage ≥97% line & branch | `mvn -o clean verify` JaCoCo check | PASS (small surface, fully testable) |
| III. English code | All identifiers/messages in English | PASS |
| IV. English comments | Javadoc/comments explain rationale | PASS |
| V. Formatting | Spotless; new file `SupervisionListener.java` added to `<includes>`, `SimulaSupervisor.java` already included | PASS with include update |
| VI. Javadoc + FR/SC citation | `SupervisionListener`, add/remove and `record()` changes cite FR-001..FR-008 | PASS |
| VII. Named literals | No new literal-bearing exception (JDK `Objects.requireNonNull` message reused); no magic values | PASS |
| VIII. architecture.md current, Mermaid-only, no history | Supervision section updated as present-state | PASS |

**Post-design re-check (Phase 1 complete)**: no violations; no Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/006-supervision-listener/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── supervision-listener.md  # Phase 1 output (library API contract delta)
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
src/
├── main/java/
│   ├── module-info.java                      # UNCHANGED - jpnco.simula.actors already exported
│   └── jpnco/simula/
│       └── actors/
│           ├── SupervisionListener.java      # NEW - single-callback functional interface (FR-002)
│           └── SimulaSupervisor.java         # MODIFY - add/remove + post-update notification (FR-001..FR-008)
└── test/java/
    └── jpnco/simula/
        └── actors/
            └── SimulaSupervisorTest.java     # MODIFY - listener lifecycle/fault-isolation/concurrency tests

architecture.md                               # UPDATE - supervision section: listener notification
README.md                                     # UPDATE - supervision usage + API reference
pom.xml                                       # UPDATE - Spotless include for SupervisionListener.java
```

**Structure Decision**: Existing single-module layout kept; one new interface in the already-exported `jpnco.simula.actors` package plus a localized extension of `SimulaSupervisor` — no new packages or dependencies.

## Key Design Decisions

1. **Single callback interface** — `@FunctionalInterface SupervisionListener { void statusChanged(Actor component, Status previous, Status current); }` with `Status` referenced as `SimulaSupervisor.Status` (user decision, spec clarification 2026-10-02).
2. **Copy-on-write listener list** — `CopyOnWriteArrayList` gives lock-free iteration during notification and safe add/remove from any thread, including from inside a callback (FR-006); snapshot semantics mean an in-flight notification may still reach a just-removed listener (documented as unspecified in the spec).
3. **Transition detection at the write** — `record()` becomes `Status previous = states.put(component, status); if (previous != status) notify(...)` (FR-003, FR-004). `put` keeps last-writer-wins map semantics; under concurrent same-key records the reported `previous` of each notification is that write's own previous value, which is the only race-free choice.
4. **Fault isolation per listener** — each callback runs in its own `try/catch (RuntimeException)`; the exception is reported with `Logger.error(this, ...)` and iteration continues (FR-005). A fast path skips the whole notification loop when no listener is registered (FR-008).
5. **Registration semantics** — `Objects.requireNonNull` on add/remove (FR-001); add ignores an already-present instance (`CopyOnWriteArrayList.addIfAbsent`, identity not tested — `equals` is not overridden on the interface side but `addIfAbsent` uses `equals`, so a listener with value-equality may be treated as duplicate; documented in the contract); remove of an absent listener is a silent no-op (FR-007).
