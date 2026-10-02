# Research: Supervision Listener

**Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

## R1 — Listener shape

**Decision**: one `@FunctionalInterface SupervisionListener` with a single method `statusChanged(Actor component, Status previous, Status current)`; `Status` stays nested in `SimulaSupervisor`.
**Rationale**: single callback covers start/stop/unknown-previous uniformly and keeps the API one-method-deep; chosen by the owner over typed callbacks. Nested `Status` avoids a second public enum leaking out of the supervision vocabulary.
**Alternatives**: typed callbacks (`started`/`stopped`) — rejected: more methods, no room for generic tools; `java.util.concurrent.Flow` — rejected: subscription machinery is oversized here and would impose async semantics.

## R2 — Listener registry and concurrency (FR-006)

**Decision**: `CopyOnWriteArrayList<SupervisionListener>`; registration via `addIfAbsent`, removal via `remove`; notification iterates the snapshot without a lock.
**Rationale**: writes (registration) are rare and reads (notification) are per-transition; COW gives lock-free, `ConcurrentModificationException`-free iteration, and self-removal from within a callback cannot corrupt the walk. A `List` copy under a `ReentrantLock` would work but re-introduces a lock on the notification path for no benefit (constitution III: virtual threads).
**Alternatives**: `CopyOnWriteArraySet` — rejected: hidden `equals`-based dedup surprises are the same but the set hides ordering; synchronized list — rejected: monitor on notification path.

## R3 — Where the transition is detected (FR-003, FR-004)

**Decision**: inside `record()`: `Status previous = states.put(component, status);` then notify iff `previous != status`.
**Rationale**: `ConcurrentHashMap.put` returns exactly the per-entry previous mapping, is atomic, and is the single funnel all three lifecycle topics already share — no second read that could observe a different concurrent write.
**Alternatives**: `replace()` + `get()` — rejected: not atomic as a pair; notifying before the map update — rejected: a listener reading `getStates()` must see the new status (spec FR-003 "after the map update").

## R4 — Fault isolation (FR-005)

**Decision**: per-listener `try { ... } catch (RuntimeException e) { Logger.error(this, ...); }`; checked exceptions cannot escape a `void` lambda.
**Rationale**: one misbehaving observer must not corrupt the recorded state nor starve the others; `Logger.error` is the framework's existing reporting channel.
**Alternatives**: catching `Throwable` — rejected: swallows `Error`/`ThreadDeath`; delegating to `Thread.UncaughtExceptionHandler` — rejected: off-contract behavior.

## R5 — Regression safety (FR-008)

**Decision**: `if (listeners.isEmpty()) return;` fast path before building any notification work; existing map write path untouched.
**Rationale**: the zero-listener supervisor executes the same instructions plus one volatile-read-free `isEmpty` check on a COW list — observable behavior is bit-identical, so the existing suite is the regression proof.
