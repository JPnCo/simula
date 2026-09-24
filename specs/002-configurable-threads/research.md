# Research: Configurable Thread Execution

Phase 0 output for `002-configurable-threads`. Resolves the unknowns identified in the Technical Context and constitution gates.

## Unknown 1: Coverage tooling (Constitution gate II)

- **Decision**: Add the JaCoCo Maven plugin (configured for line AND branch coverage with a 97% minimum per metric) to `pom.xml`.
- **Rationale**: The constitution (II. Coverage Standard) mandates ≥97% line and branch coverage, but the project currently has no coverage tool configured. JaCoCo is the de-facto standard for JVM coverage, integrates natively with Maven Surefire, and reports both line and branch metrics independently (via `jacoco:check` with `line` and `branch` counter rules set to `COVEREDRATIO` 0.97).
- **Alternatives considered**:
  - OpenClover: less maintained, larger bytecode instrumentation overhead.
  - Manual coverage tracking: not feasible for a hard 97% gate.
  - Pitest mutation coverage: complementary but not a replacement for the mandated line/branch metric.

## Unknown 2: Formatter (Constitution gate V)

- **Decision**: Adopt the project's declared formatter. No formatter is currently configured; the existing code uses a tab-indented, braces-on-next-line style. Use a light Maven formatter integration that matches this existing style, OR (preferred given the existing hand-formatted codebase) enforce formatting via a single-command tool compatible with the current style rather than reformatting the whole legacy tree.
- **Rationale**: Constitution V requires conformance to an automated formatter and a quality gate. Reformatting the entire existing codebase in this feature would produce a massive noisy diff and obscure the behavioral changes.
- **Alternatives considered**:
  - Spotless with google-java-format: clean but would reformat all legacy files (noisy diff, conflicts with the existing hand style).
  - Manual style enforcement: violates the "automated formatter" requirement.
  - **Chosen**: scope formatting enforcement to the files touched by this feature, using a formatter profile that matches the existing style, documented in the quality gate.

## Unknown 3: Execution-mode configuration surface (FR-003)

- **Decision**: Add an execution-mode parameter to the `EngineImpl` constructor, exposed through a small enum (e.g., `VIRTUAL`, `PLATFORM`) with `VIRTUAL` as the default (FR-002).
- **Rationale**: The clarification (Q1) fixed the mechanism as a per-engine constructor/configuration parameter fixed at creation. An enum is type-safe, prevents invalid values (FR-007: reject invalid mode), and is the most discoverable for library consumers.
- **Alternatives considered**:
  - System property: violates the clarified constructor-parameter decision; global and non-type-safe.
  - Config file: overkill for a library; adds I/O dependency.
  - String parameter: allows invalid values silently (violates FR-007 intent); enum is superior.

## Unknown 4: Thread creation (FR-001/004/006)

- **Decision**: In `EngineImpl.start(Actor)`, branch on the selected mode: platform threads via `new Thread(actor, name).start()`; virtual threads via `Thread.ofVirtual().name(name).start(actor)`. Preserve the actor thread name in both modes (FR-006). The runtime already supports virtual threads (Java 23 target).
- **Rationale**: Directly maps to the two required modes with a single decision point, keeps the existing platform-thread path unchanged for backward behavior, and uses the standard JEP 425 virtual-thread builder API.
- **Alternatives considered**:
  - A shared virtual-thread executor (`Executors.newVirtualThreadPerTaskExecutor()`): also valid; a builder-based direct start is simpler and matches the existing per-actor-thread model. (Executor noted as acceptable equivalent.)

## Unknown 5: ReentrantLock replacement (FR-010/011/012/013)

- **Decision**: Replace all 14 `synchronized` usages (12 in `EngineImpl`, 1 in `IdBuilder`) with explicit `ReentrantLock` instances, guarding the same state:
  - Per-instance `ReentrantLock` in `EngineImpl` protecting the shared mutable collections (actors map, children set, subscribersByTopic map).
  - A static `ReentrantLock` in `IdBuilder` protecting the shared static counter (replacing `synchronized static nextId()`).
  - Acquire/release via try/finally (or the reentrant idiom) so locks are always released, including exceptional paths (FR-011).
- **Rationale**: `ReentrantLock` is the drop-in replacement for `synchronized` monitors with identical reentrant semantics, preserving the existing mutual-exclusion guarantees (FR-012) while making locking explicit. This is required for uniform concurrency semantics across the framework (clarification Q2: whole framework).
- **Deadlock/lock-ordering note**: The nested `synchronized (actors) { synchronized (children) {...} }` in `EngineImpl.run()` (lines 259-260) must preserve lock ordering (acquire `actors` then `children`) across all paths to avoid introducing deadlocks. If multiple per-structure locks are used, enforce a consistent global order; a single per-engine lock (acquired once per operation) eliminates ordering risk entirely.
- **Alternatives considered**:
  - `ReentrantReadWriteLock`: adds complexity with no requirement for concurrent reads; not needed.
  - `StampedLock`: over-engineered for this scope; not a direct monitor replacement.
  - **Single per-engine `ReentrantLock`** recommended over per-structure locks to avoid nested-ordering deadlock risk, unless profiling shows contention warrants finer granularity.

## Unknown 6: Architecture document (Constitution gate VIII)

- **Decision**: Create `architecture.md` at the repo root (and reference it in the feature docs) describing the framework's current architecture using Mermaid diagrams (classDiagram for the actor/engine/event model; sequenceDiagram for actor start/stop and event signal flows), with no historical information.
- **Rationale**: Constitution VIII requires a current architecture document per functional branch with Mermaid-only structural diagrams and no history. This is a documentation artifact delivered by this feature.
- **Alternatives considered**: Skipping it — not permitted by constitution gate VIII.

## Unknown 7: Behavior-equivalence verification (SC-003/005)

- **Decision**: Verify equivalence by running identical scenarios under both modes and asserting equivalent observable outcomes (event processing, start/stop, time), plus a stop-cascade test confirming no actor remains running in either mode.
- **Rationale**: Directly satisfies SC-003/SC-005 and FR-013. Deterministic test suites are required by the constitution.
- **Alternatives considered**: Randomized differential testing — higher value but not required; deterministic scenario comparison is sufficient for the gate.
