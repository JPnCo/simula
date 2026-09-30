# Contract: Registration Guard and Stopped Status

**Branch**: `005-forbid-actor-restart` | **Date**: 2026-09-30 | **Spec**: [spec.md](spec.md) | **Data model**: [data-model.md](data-model.md)

Phase 1 output of `/speckit.plan`. Library API contract delta introduced by this feature.

## `jpnco.simula.Actor` — new defaulted method

```java
default boolean isStopped() { return false; }
```

- Returns `true` when the actor's run loop has completed its stop (terminal state).
- Standard-delegated actors: reports the `ActorDelegate` terminal flag automatically.
- Custom-delegated actors: SHOULD override to report their own terminal state; the contract is documented in Javadoc (FR-004, FR-005).

## `jpnco.simula.Engine` — strengthened `registerAndStart(Actor)`

Registration of an actor instance is refused — with a logged engine error **and** an `IllegalArgumentException` naming the actor — when any of the following holds (FR-001, FR-002, FR-005, FR-008):

1. the actor reports `isStopped()` (or its instance was previously unregistered by this engine, covering custom delegates that do not report);
2. an actor with the same id is currently registered on this engine (including a currently-running or blocked instance).

On refusal (FR-003): the actor is not added to the engine, is not subscribed to any topic, no thread is started, and the currently running actor (case 2) is undisturbed (SC-005).

Accepted cases are unchanged (FR-007, SC-004): fresh instances — including same-name instances of previously stopped actors — register and run exactly as before.

## Memory behavior (FR-006)

The engine's memory of stopped instances uses weak references only: it never prevents an unreferenced stopped instance from being reclaimed, and does not grow with the cumulative count of stopped actors once the application releases them (SC-003).

## Non-goals

- No restart/re-arm API is introduced; creating a new instance is the supported way to run the same behavior again.
- Engines' and the framework's internal actors are unaffected (they never re-register).
