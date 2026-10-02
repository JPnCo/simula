# Contract: Supervision Listener API

**Branch**: `006-supervision-listener` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md) | **Data model**: [data-model.md](data-model.md)

Phase 1 output of the plan. Library API contract delta introduced by this feature.

## `jpnco.simula.actors.SupervisionListener` — new interface

```java
@FunctionalInterface
public interface SupervisionListener {
  void statusChanged(Actor component, SimulaSupervisor.Status previous, SimulaSupervisor.Status current);
}
```

- `component`: the observed actor or engine whose recorded status changed.
- `previous`: the status recorded before this change; `null` when the component was never recorded before (FR-002).
- `current`: the newly recorded status; already visible in `getStates()` when the callback runs (FR-003).
- Contract: invoked synchronously in the recording thread — which may be any thread under the supervisor's `process()` contract — once per actual transition, once per registration. Implementations MUST be thread-safe and SHOULD NOT block; a thrown `RuntimeException` is caught, logged as an error by the supervisor, and does not affect the recording or other listeners (FR-005).

## `jpnco.simula.actors.SimulaSupervisor` — new methods

```java
public void addSupervisionListener(SupervisionListener listener)
public void removeSupervisionListener(SupervisionListener listener)
```

- `add` rejects `null` with `NullPointerException`; adding the same listener again is a no-op — duplicate detection is `equals`-based, which is identity for typical lambda or anonymous-class listeners (FR-001).
- `remove` rejects `null` with `NullPointerException`; removing a listener that is not registered is a silent no-op (FR-007).
- Both are safe to call from any thread at any time, including from inside a `statusChanged` callback; a listener removed while a notification is in flight may or may not still receive that in-flight notification (unspecified), but never a later one (FR-006, FR-007).
- Multiple listeners are notified one after another in registration order, on the first-notification-wins snapshot taken when the notification begins.

## Behavior delta

- Every actual status transition recorded by the supervisor (actor start, actor stop, engine stop, and the FR-010 direct-stop case) now notifies registered listeners after the map update (FR-003).
- Events that do not change any recorded status notify nobody (FR-004).
- With no listener registered, observable behavior is identical to the pre-feature supervisor, including event ordering and the recorded map (FR-008, SC-004).

## Non-goals

- No asynchronous or replayed delivery: attaching late yields only future transitions.
- No listener-scoped filtering (by component or topic); listeners may filter in their callback.
- `getStates()` keeps its unmodifiable-map contract; no listener is invoked by read paths.
