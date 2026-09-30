# Phase 0 Research: Forbid Actor Restart

**Branch**: `005-forbid-actor-restart` | **Date**: 2026-09-30 | **Spec**: [spec.md](spec.md)

No [NEEDS CLARIFICATION] markers remain in the spec; this research resolves the design unknowns identified during planning.

## R1. Where to enforce the refusal

- **Decision**: Enforce both refusals inside the private `EngineImpl.register(Actor)`, under the existing per-engine `ReentrantLock`, before any mutation of `actors`.
- **Rationale**: `register()` is the single funnel for all registration entry points (`registerAndStart` is the public one), so a single guard satisfies FR-001 ("whatever the registration entry point") and guarantees FR-003 (no trace on refusal: no put, no subscription, no thread start — the throw happens before `start()` is ever called).
- **Alternatives considered**: guarding in `registerAndStart` only (rejected: future entry points would bypass it); guarding in `Actor.run()`/`run()`-time (rejected: too late — a thread has already been created, violating FR-003).

## R2. Carrying the stopped state

- **Decision**: Defaulted `boolean isStopped()` on `Actor` (returns `false`), overridden by `ActorDelegate`, which flips a `private volatile boolean stopped` in the `finally` block of `run()`.
- **Rationale**: State lives on the object — zero engine-side growth (FR-006) and the object is reclaimed together with its state. A `finally` in `run()` is the single point covering every exit path (normal stop, stop-before-start, crash), and runs after the `STOPPED_ACTOR_EVENT` signal, so the flag cannot be observed `true` while the actor is still processing. A defaulted interface method breaks no existing implementation (FR-004, FR-005 contract documented for custom delegations).
- **Alternatives considered**: engine-side strong `Set<Integer>` of stopped ids (rejected in scoping: unbounded growth); engine-side `Set` of actor instances only (rejected alone: custom delegates with identity quirks; kept instead as a weak backstop, see R3); setting the flag at the `break LOOP` sites (rejected: two sites to maintain instead of one, crash path missed).

## R3. Backstop for custom delegates

- **Decision**: `EngineImpl` keeps `stoppedInstances = Collections.newSetFromMap(new WeakHashMap<Actor, Boolean>())`, written in `unregister()` only on the successful-removal branch (non-engine actors), read in `register()`; all accesses under the existing `ReentrantLock`.
- **Rationale**: An actor with a custom delegate that does not override `isStopped()` is still refused as long as the application can possibly re-register it — which requires holding a reference to the instance, which keeps the weak entry alive. Once the application drops the instance, the entry is reclaimed: memory is bounded by stopped-and-still-reachable instances, none of it new (FR-006). Using the existing `ReentrantLock` (never `synchronized`) complies with the virtual-thread locking rule in architecture.md.
- **Alternatives considered**: strong set (rejected — the original unbounded-growth objection); `WeakReference` queue with manual purging (rejected: more moving parts for the same guarantee); `ConcurrentHashMap` key set with weak keys (no such JDK class).

## R4. Identity semantics of the weak set

- **Decision**: Use the actor instances themselves as weak-set keys and rely on `equals`/`hashCode`.
- **Rationale**: Framework actor classes (`ActorDelegate`, `EngineImpl`) define `equals` by the unique `IdBuilder` id; business actors overwhelmingly use identity. Two distinct instances cannot share an id (uniqueness of `IdBuilder`), so a false refusal of a fresh instance is impossible (FR-007). A custom actor overriding `equals` by mutable business state is out of the documented contract and would refuse *more*, never less — fail-safe direction.
- **Alternatives considered**: `Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()))` (rejected: redundant layering on top of the existing lock).

## R5. Rejection behavior and message

- **Decision**: `Logger.error(this, messageTemplate, actor.getName())` followed by `throw new IllegalArgumentException(String.format(template, actor.getName()))`, where the template is a `static final String` constant (e.g. `"Actor %s is stopped and cannot be registered again; create a new instance"` — the exact wording for the currently-registered case is a second constant).
- **Rationale**: User decision (log + exception); `IllegalArgumentException` matches existing constructor validators (`Barrier`, `Objects.requireNonNull` style usage). Named literal per Constitution VII.
- **Alternatives considered**: `IllegalStateException` (semantically about receiver state, but project convention uses IAE for argument misuse); log-and-no-op (rejected earlier: silent failures).

## R6. Double registration of a live actor (clarification Q1)

- **Decision**: `register()` refuses when `actors.containsKey(actor.getId())` with the same log+exception behavior (different message constant); check precedes the stopped checks.
- **Rationale**: Clarification session 2026-09-30 (FR-008, SC-005). Closes the "blocked actor" loophole: an actor stuck in `process()` has no terminal flag yet, but it is still registered, so a second registration — which would silently overwrite the map slot and create a concurrent second thread on the same object — is refused. No internal flow (engine construction, logger, time source, supervisor) registers an already-registered instance, so SC-004 (no regression) is not at risk.
- **Alternatives considered**: allow and overwrite (current behavior, rejected by user); compare `actors.get(id) != actor` to permit idempotent re-registration (rejected: no legitimate use case, weaker guarantee).

## R7. Existing-suite compatibility audit

- **Decision**: No production code or existing test re-registers a stopped or concurrently-registered instance (verified by reading `EngineImplCoverageTest`, `EngineImplTest`, `ConcurrencyTest`, `TimeSourceTest`, and framework flows); the guards are additive.
- **Rationale**: Required to protect SC-004. Tests unregister instances at the end of each test with fresh instances per test; framework actors are registered exactly once at construction.
- **Alternatives considered**: none — this is a verification, not a choice; if a violation surfaces during implementation, the test (not the guard) must be adapted.
