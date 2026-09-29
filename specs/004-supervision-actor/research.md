# Research: Add Supervision Actor

**Branch**: `004-supervision-actor` | **Date**: 2026-09-29 | **Spec**: [spec.md](spec.md)

Phase 0 output of `/speckit.plan`. Resolves the technical unknowns of the feature from the existing codebase.

## Decision: Per-actor startup notification

- **Decision**: Introduce a new lifecycle event `STARTED_ACTOR_EVENT`, symmetric to the existing `STOPPED_ACTOR_EVENT`, that identifies the actor that has started. It is emitted when an actor actually begins its behavior (receives the START event), not when its thread is merely created.
- **Rationale**: `START_EVENT` is a single broadcast to all subscribers and does not identify which actors are running. A per-actor startup notification is the only way a supervision actor can observe each actor starting, as required by FR-001 and SC-002.
- **Alternatives considered**: polling the engine's registered-actor set (no public enumeration API exists and it is not lifecycle-driven); emitting at thread creation time (misrepresents the moment of actual startup).

## Decision: Emission points for the startup notification

- **Decision**: The built-in actors, the engine, and the time source each emit `STARTED_ACTOR_EVENT` for themselves when they start:
  - `ActorDelegate` (the standard delegation event loop) emits it for any standard actor when that actor transitions into the running state (on receiving `START_EVENT`, after the start hook).
  - `TimeSource` emits it for itself when its own run loop starts (it has no delegate).
  - `EngineImpl` emits it for itself when its run loop starts (an engine is itself an actor).
- **Rationale**: These three components are the only built-in actors; each already manages its own lifecycle (and each already emits `STOPPED_ACTOR_EVENT` where applicable). Symmetry with the existing stop signal keeps the lifecycle model uniform.
- **Alternatives considered**: centralizing emission in `EngineImpl.start(Actor)` (fires before the thread actually runs — wrong moment); forcing every external actor (impossible — the framework does not control external code).

## Decision: External-actor contract is documented, not enforced

- **Decision**: Actors created by external projects that do not use the standard delegation are **not** forced to emit `STARTED_ACTOR_EVENT`. The contract (how and when to emit) is documented in the `Actor`/`Engine` Javadoc and in `architecture.md` (FR-007).
- **Rationale**: The framework cannot compel third-party actors to emit an event. Documentation is the practical, non-breaking way to extend coverage (SC-001, US4).
- **Alternatives considered**: adding an abstract hook to `Actor` that every external implementation must override (breaking change to a public interface used by third parties); silently emitting on behalf of external actors (not possible without controlling their run loop).

## Decision: Supervision state model

- **Decision**: The supervision actor keeps an in-memory, queryable map of observed components (`Actor`/`Engine`) to their lifecycle status (`STARTED` or `STOPPED`). Status updates follow the sequence of notifications received. A component observed stopping without a prior start is recorded directly as `STOPPED` (FR-010).
- **Rationale**: A simple started/stopped status per component satisfies US3/SC-004 with deterministic, easily testable semantics.
- **Alternatives considered**: a full event-history log (heavier than needed; the queryable status is sufficient); a live set of "running" components only (cannot answer "did X stop?").

## Decision: No behavioral change to observed actors

- **Decision**: The supervision actor observes lifecycle events by subscription only and never posts to, stops, or modifies the observed actors (FR-008, SC-005).
- **Rationale**: Supervision must be non-intrusive; this matches the existing subscription-based observation used by `Logger`.
- **Alternatives considered**: actively probing actors (intrusive, violates FR-008).

## Technical context notes

- Language/version: Java 25 (pom.xml `maven.compiler.source`/`target` = 25; module `Simula`).
- Build: Maven, offline (`mvn -o`); tests JUnit Jupiter 5.14.0 + Mockito 5.22.0; JaCoCo 0.8.15 enforces the 97% line + branch gate (Constitution II); Spotless (google-java-format) enforces formatting (Constitution V).
- New production code lives in the existing `jpnco.simula.actors` and `jpnco.simula.engine` packages (already exported; no `module-info.java` change expected unless a new public constant is needed on `Engine`, which lives in the already-exported `jpnco.simula` package).
- No external runtime dependencies; in-memory framework, no persistence.
