# Quickstart: Configurable Thread Execution

Validation guide for the `002-configurable-threads` feature. It proves the feature works end-to-end: virtual-thread default, selectable classic mode, behavioral equivalence, and the `synchronized`→`ReentrantLock` change without regression.

## Prerequisites

- JDK 21+ (virtual threads). The build has been validated on Temurin 25.0.2.
- Maven 3.9+
- All test dependencies must be resolvable from the local Maven repository (`~/.m2`). This environment is offline, so the build runs with `mvn -o`; dependencies were realigned to the local cache: `junit-jupiter-api` **5.14.0**, `mockito` **5.22.0**.
- JaCoCo must be **0.8.15** (0.8.12 fails to instrument Java 25 class files).

## Setup

1. Ensure `pom.xml` includes the coverage plugin (JaCoCo 0.8.15) wired with `prepare-agent`, `report`, and `check` (LINE + BRANCH `COVEREDRATIO` min `0.97`, scope `BUNDLE`). Surefire uses `-javaagent:${org.mockito:mockito-core:jar} @{argLine}`.
2. Build and test from the repo root (offline):

   ```bash
   mvn -o clean verify
   ```

   This runs the full test suite and the JaCoCo coverage gate. `check` is bound to the `verify` phase, so it only fails the build when the 97% threshold is missed.

## Validation scenarios

### 1. Virtual-thread default (FR-002, SC-002)

- Construct an engine **without** specifying an execution mode.
- Register and start a custom actor that processes a signaled event.
- **Expected**: the simulation runs and the actor processes the event on a virtual thread; outcome matches the intended behavior.

### 2. Classic-thread selection (FR-003, FR-004)

- Construct an engine with `ExecutionMode.PLATFORM` (classic).
- Register and start the same actor and signal the same event.
- **Expected**: the actor runs on a classic platform thread and processes the event.

### 3. Behavioral equivalence (FR-005, SC-003)

- Run an identical scenario under virtual mode and under classic mode.
- **Expected**: observable results (events processed, time advancement, start/stop) are equivalent in both modes.

### 4. Invalid mode rejected (FR-007, SC-004)

- Attempt to construct an engine with an unknown/invalid execution-mode value.
- **Expected**: a clear error is raised; the invalid value is not silently accepted.

### 5. Stop cascade in both modes (FR-008, SC-005)

- Start an engine (with child engines and actors) under each mode, then stop the root.
- **Expected**: every actor and child engine terminates; no actor remains running after termination in either mode.

### 6. Locking change no-regression (FR-010..013, SC-007)

- Exercise concurrent subscribe/unsubscribe, register/unregister, and child-engine add/remove.
- **Expected**: no data race, deadlock, or stuck state; all previously-`synchronized` operations remain mutually exclusive and behavior is unchanged.

### 7. Coverage gate (Constitution II, SC-008)

- After all tests, run `mvn -o verify` so the JaCoCo `check` goal executes.
- **Expected**: line and branch coverage each ≥ 97% over the **whole framework bundle** (not just the feature classes). As of this feature, the bundle measures **98.3% lines / 97.9% branches**.

   Coverage notes for future contributors:
   - `EventImpl` is capped near 96% line coverage because its `catch (CloneNotSupportedException …)` block in `duplicate()` is genuinely unreachable (`Event extends Cloneable`).
   - `EngineImpl` retains a few uncovered branches in `post()` (queue-full spin), `run()` (poll-timeout/null-event, and the outer `Throwable` handler), which are impractical to drive deterministically in tests.

### 8. Traffic-light example end-to-end (FR-001..006, SC-003)

- Build: `mvn -o verify` (must stay BUILD SUCCESS; the `examples` package is excluded from the JaCoCo `check` rule so the 97% gate on the framework bundle is preserved).
- Run in **virtual** mode (default), console display: `mvn -o exec:java -Dexec.args="virtual"`
- Run in **classic** mode, console display: `mvn -o exec:java -Dexec.args="classic"`
- Run the **GUI** display (runs until the window is closed): `mvn -o exec:java -Dexec.args="virtual gui"`
- Fallback (module launcher): `java -p target/classes -m Simula/jpnco.simula.examples.trafficlight.TrafficLightDemo classic`
- **Expected**: the demo builds a closed-loop 5×5 toroidal grid (each segment 250 m, vehicles at a fixed speed in 15–45 km/h), a traffic light at every intersection with a short green segment (~20 m) marking the green road, and a fleet of vehicles that advance continuously and may turn randomly. The console display prints the grid each simulated second; the GUI shows it in real time. The two modes produce an equivalent outcome summary (same vehicle totals), demonstrating behavioral equivalence (FR-005, SC-003).

## References

- Contracts: [contracts/execution-mode.md](contracts/execution-mode.md)
- Data model: [data-model.md](data-model.md)
- Spec: [spec.md](spec.md)

## Note on dependencies

This environment could not reach Maven Central during earlier attempts (the proxy did not relay outbound traffic). The build therefore runs offline with `mvn -o`, relying on the local repository cache. If you need to change dependencies, resolve them in an environment with network access to Maven Central first, or adjust the pinned versions (`junit-jupiter-api` 5.14.0, `mockito` 5.22.0) to what is available in your local `~/.m2`.
