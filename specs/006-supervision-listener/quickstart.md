# Quickstart: Supervision Listener

**Branch**: `006-supervision-listener` | **Spec**: [spec.md](spec.md) | **Contract**: [supervision-listener.md](contracts/supervision-listener.md)

Validation guide to prove the feature works end-to-end. All commands run offline from the repository root.

## Prerequisites

- JDK 25 and Maven 3.9+ available on `PATH`
- Test dependencies (`junit-jupiter-api` 5.14.0, `mockito` 5.22.0) present in the local `~/.m2` repository

## Automated validation (primary)

```bash
mvn -o clean verify
```

Expected outcome (SC-001..SC-004, Constitution II):

- All tests pass, including the new listener tests in
  `src/test/java/jpnco/simula/actors/SimulaSupervisorTest.java`:
  - a registered listener receives `(null → STARTED)` then `(STARTED → STOPPED)` for an actor lifecycle, with the right component (SC-001, US1);
  - a no-change record notifies nobody (SC-001, FR-004);
  - two listeners both receive every notification; a throwing listener is isolated, the record survives and the error is logged (SC-002, FR-005);
  - `add(null)`/`remove(null)` throw `NullPointerException`, double add notifies once, remove of an absent listener is silent (US3);
  - concurrent `process()` + add/remove stress completes without exception and with the expected recorded count (SC-003, FR-006).
- JaCoCo reports ≥97% line and ≥97% branch coverage on the `simula` bundle.
- Spotless check passes for `SupervisionListener.java` and `SimulaSupervisor.java`.

## Manual scenario (optional, illustrative)

A scratch main (outside the module, classpath = `target/simula-0.0.1-SNAPSHOT.jar`) that:

1. creates a root engine (`new EngineImpl("probe", 2)`) and a `SimulaSupervisor` on it;
2. calls `supervisor.addSupervisionListener((c, prev, cur) -> System.out.println(c.getName() + ": " + prev + " -> " + cur))`;
3. registers and starts a small actor, then stops it, then stops the engine.

Expected outcome: printed transitions `actor: null -> STARTED`, `actor: STARTED -> STOPPED`, plus the engine's own `null -> STOPPED` transition — with no polling of `getStates()`.

## Regression check

```bash
mvn -o clean verify
```

must keep the pre-existing suites (including all supervision feature scenarios, which register no listener) green with identical observable outcomes (SC-004).
