# Implementation Plan: Poisson Alarm

**Branch**: `007-poisson-alarm` | **Date**: 2026-10-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/007-poisson-alarm/spec.md`

## Summary

Extend the alarm service of `TimeSource`: a request may declare a Poisson recurrence — topic, first firing time, a `POISSON` period marker, a strictly positive rate (mean firings per unit of simulated time) and an optional seed (FR-001). The inner `Alarm` gains a rate mode: each re-arm draws its next wait from the exponential distribution of parameter rate (`-ln(U)/rate`, scaled by `TIME_FACTOR`, rounded, floored at one simulated unit) from its own `java.util.Random` (seeded when a seed is given) (FR-002, FR-003, FR-007). Malformed or non-positive-rate requests are refused with a logged error and register no alarm (FR-006); clearing is unchanged (FR-004); one-shot and fixed-period alarms are untouched (FR-005). No new dependency.

## Technical Context

**Language/Version**: Java 25 (`maven.compiler.source/target = 25`; module `simula`)

**Primary Dependencies**: None new — JDK `java.util.Random`

**Storage**: N/A (in-memory simulation framework)

**Testing**: JUnit Jupiter 5.14.0 + Mockito 5.22.0 via Maven Surefire; JaCoCo gates at ≥97% line and branch coverage

**Target Platform**: JVM 25+ (virtual threads by default); built offline with `mvn -o`

**Project Type**: Library (single Maven module, `jpnco:simula`)

**Performance Goals**: One `Random.nextDouble` and one `log` per re-arm — negligible

**Constraints**: `TimeSource.java` is legacy tab-indented and NOT in Spotless `<includes>` — edits keep its existing style; deterministic tests require seeded randomness (Constitution: deterministic suite); no `synchronized` (alarms map stays owned by the time source run thread, unchanged)

**Scale/Scope**: 1 production file modified (`TimeSource.java`), 1 test file extended (`TimeSourceTest.java`), `README.md` (Alarms) and `architecture.md` updates; no pom change

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Test-First | Tests written first per user story, verified Red before Green | PASS (enforced in tasks phase) |
| II. Coverage ≥97% line & branch | `mvn -o clean verify` JaCoCo check | PASS (rate-mode branches covered by unit + integration tests) |
| III. English code | All identifiers/messages in English | PASS |
| IV. English comments | Javadoc/comments explain rationale | PASS |
| V. Formatting | `TimeSource.java` is not a Spotless file; keep legacy tabs, no reformat | PASS (no pom include) |
| VI. Javadoc + FR/SC citation | New constant, request documentation, `Alarm` rate mode cite FR-001..FR-007 | PASS |
| VII. Named literals | `POISSON` marker constant + named error-message constants | PASS |
| VIII. architecture.md current, Mermaid-only, no history | Alarm documentation updated as present-state | PASS |

**Post-design re-check (Phase 1 complete)**: no violations; no Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/007-poisson-alarm/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── poisson-alarm-request.md  # Phase 1 output (request-format contract delta)
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
src/
├── main/java/
│   └── jpnco/simula/
│       └── actors/
│           └── TimeSource.java     # MODIFY - POISSON marker constant, Alarm rate mode, request validation
└── test/java/
    └── jpnco/simula/
        └── actors/
            └── TimeSourceTest.java # MODIFY - Poisson request/firing/seed/validation tests (FR/SC)

README.md                           # UPDATE - Alarms section: Poisson request form
architecture.md                     # UPDATE - alarm model: rate mode
```

**Structure Decision**: Strictly additive extension inside the existing alarm service; no new files besides spec documents.

## Key Design Decisions

1. **Request shape (unambiguous, FR-005)** — Poisson requests carry a String marker in place of the period: `REQUEST_ALARM_EVENT(topic, Integer firstTime, TimeSource.POISSON, Double rate[, Long seed])`. Detection: `params.length >= 4 && TimeSource.POISSON.equals(params[2])`. The existing 2-parameter and 3-parameter shapes keep their exact meaning; a `Double` period without the marker is malformed (refused, FR-006), so no silent reinterpretation.
2. **Exponential waits, integer units (FR-002)** — `wait = max(1, round(TIME_FACTOR * -ln(1-U) / rate))` in internal ticks, matching how fixed periods already scale by `TIME_FACTOR`; the first firing stays deterministic at the requested time.
3. **Random owned by the alarm (FR-003, FR-007)** — the rate-mode `Alarm` holds its own `Random` (`new Random(seed)` when seeded, `new Random()` otherwise); sequences are independent of other alarms and of request ordering.
4. **Refusal style (FR-006)** — `Logger.error(this, NAMED_CONSTANT, ...)` then return without touching the `alarms` map (same observable outcome as a malformed request today, but logged and exception-free).
5. **Testability** — the wait computation lives in a package-private static helper `drawWait(double rate, Random random, int timeFactor)` so statistical SC-002 is tested in-process (no simulated-time wall-clock cost); integration tests only verify irregular firings, seed reproducibility and clear on a short seeded run.
