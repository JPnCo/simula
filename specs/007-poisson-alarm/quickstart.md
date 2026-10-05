# Quickstart: Poisson Alarm

**Branch**: `007-poisson-alarm` | **Spec**: [spec.md](spec.md) | **Contract**: [poisson-alarm-request.md](contracts/poisson-alarm-request.md)

Validation guide. All commands run offline from the repository root.

## Prerequisites

- JDK 25 and Maven 3.9+ on `PATH`; test dependencies present in the local `~/.m2` repository

## Automated validation (primary)

```bash
mvn -o clean verify
```

Expected outcome (SC-001..SC-004, Constitution II):

- New tests in `src/test/java/jpnco/simula/actors/TimeSourceTest.java`:
  - `drawWait` over >= 2000 seeded draws: empirical mean within 10 % of `TIME_FACTOR / rate`, at least two distinct values, every value >= 1 (SC-002, SC-003);
  - seeded integration: same rate + seed twice ⇒ identical firing times; unseeded ⇒ irregular gaps; `CLEAR_ALARM_EVENT` stops the firings (SC-001, FR-003, FR-004);
  - refusal: rate `0`, rate `-1.0`, `Double` period without marker ⇒ error logged, no alarm, existing behavior intact (FR-006, FR-005).
- Pre-existing alarm tests unchanged and green (FR-005, SC-004).
- JaCoCo >= 97% line and branch; Spotless clean (`TimeSource.java` is outside Spotless includes — legacy tabs preserved).

## Manual scenario (optional, illustrative)

Scratch main (classpath = `target/simula-0.0.1-SNAPSHOT.jar`): create a root engine, an actor subscribing to `"CLICK"`, request `("CLICK", 3, TimeSource.POISSON, 1.0, 7L)`, start the engine, print the firing times: first at 3, then irregular gaps averaging 1 unit; rerun with seed 7 — same times.

## Regression check

`mvn -o clean verify` must keep all pre-existing suites green with identical outcomes (SC-004).
