# Quickstart: Add Supervision Actor

**Branch**: `004-supervision-actor` | **Date**: 2026-09-29 | **Spec**: [spec.md](spec.md)

Phase 1 output of `/speckit.plan`. Validation guide for the supervision-actor feature. It proves the feature works end-to-end: per-actor startup notifications, stop observation, a queryable state, and no behavioral change to observed actors.

## Prerequisites

- JDK 25 (pom.xml `maven.compiler.source`/`target` = 25), Maven 3.9+.
- Offline build (`mvn -o`); test dependencies cached in `~/.m2` (junit-jupiter-api 5.14.0, mockito 5.22.0).
- JaCoCo 0.8.15 (97% line + branch gate), Spotless (google-java-format).

## Setup

1. Implement the feature (see [tasks.md](../tasks.md) once generated).
2. Build and test from the repo root (offline):

   ```bash
   mvn -o verify
   ```

   This runs the full test suite and the JaCoCo coverage gate.

## Validation scenarios

### 1. Per-actor startup notifications (FR-001, SC-002)

- Start an engine with a `SimulaSupervisor` and several other standard actors.
- **Expected**: the supervision actor records each built-in actor as `STARTED` when it starts, and observes every startup.

### 2. Stop observation (FR-003, FR-004, SC-003)

- Stop an engine that has actors and a child engine.
- **Expected**: the supervision actor records each actor stop (`STOPPED_ACTOR_EVENT`) and the child-engine stop (`STOPPED_ENGINE_EVENT`).

### 3. Queryable state (FR-006, FR-009, SC-004)

- Run a scenario with starts and stops, then query the supervision actor's state.
- **Expected**: the reported state matches the sequence of notifications; a component stopped without a prior start is recorded as `STOPPED` without error (FR-010).

### 4. No behavioral change (FR-008, SC-005)

- Run an identical scenario with and without a supervision actor.
- **Expected**: observable outcomes of the observed simulation are identical in both cases.

### 5. External-actor contract (FR-007)

- An external actor that does not use standard delegation but follows the documented contract emits `STARTED_ACTOR_EVENT` with itself as source.
- **Expected**: the supervision actor observes it; a supervision actor never fails when a component emits no notification.

### 6. Coverage gate (Constitution II, SC-006)

- Run `mvn -o verify`.
- **Expected**: line and branch coverage each ≥ 97% over the whole framework bundle.

## References

- Contract: [contracts/supervision-actor.md](contracts/supervision-actor.md)
- Data model: [data-model.md](data-model.md)
- Spec: [spec.md](spec.md)
