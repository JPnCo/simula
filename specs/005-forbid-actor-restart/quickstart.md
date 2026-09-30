# Quickstart: Forbid Actor Restart

**Branch**: `005-forbid-actor-restart` | **Spec**: [spec.md](spec.md) | **Contract**: [registration-guard.md](contracts/registration-guard.md)

Validation guide to prove the feature works end-to-end. All commands run offline from the repository root.

## Prerequisites

- JDK 25 and Maven 3.9+ available on `PATH`
- Test dependencies (`junit-jupiter-api` 5.14.0, `mockito` 5.22.0) present in the local `~/.m2` repository

## Automated validation (primary)

```bash
mvn -o clean verify
```

Expected outcome (SC-001..SC-005, Constitution II):

- All tests pass, including the new guard tests in
  `src/test/java/jpnco/simula/engine/EngineImplCoverageTest.java` and
  `src/test/java/jpnco/simula/engine/ActorDelegateTest.java`:
  - re-registering a stopped actor throws `IllegalArgumentException` and the actor stays absent from `engine.getActors()` (SC-001, SC-002);
  - re-registering a currently-registered (running) actor is refused, its execution undisturbed (SC-005);
  - a fresh instance with the same name as a stopped actor is accepted (FR-007);
  - `isStopped()` is `false` before the run and `true` after the stop completes (FR-004);
  - a custom-delegated actor without `isStopped()` override is refused via the weak backstop (FR-005).
- JaCoCo reports ≥97% line and ≥97% branch coverage on the `simula` bundle.
- Spotless check passes for all modified files.

## Manual scenario (optional, illustrative)

A scratch main (outside the module, classpath = `target/simula-0.0.1-SNAPSHOT.jar`) that:

1. creates a root engine (`new EngineImpl("probe", 2)`), registers and starts a small actor, calls `engine.start()`;
2. stops the actor with `actor.stopMe()` and waits until `engine.getActors()` no longer contains it;
3. calls `engine.registerAndStart(actor)` again.

Expected outcome: step 3 throws `IllegalArgumentException` whose message names the actor, an engine error line is printed on the log, and `engine.getActors()` is unchanged — where previously (pre-feature) the call silently "half restarted" the actor.

## Regression check

```bash
mvn -o clean verify
```

must keep the pre-existing suites green with identical observable outcomes (SC-004); the samples project (`../SIMULA_SAMPLES`, `mvn -o compile` after `mvn -o install`) must build and run unchanged.
