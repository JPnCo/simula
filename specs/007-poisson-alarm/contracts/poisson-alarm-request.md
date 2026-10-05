# Contract: Poisson Alarm Request

**Branch**: `007-poisson-alarm` | **Date**: 2026-10-05 | **Spec**: [spec.md](spec.md) | **Data model**: [data-model.md](data-model.md)

Phase 1 output of the plan. Contract delta on the alarm service.

## New constant

```java
public final class TimeSource implements Engine {
  /** Period marker requesting a Poisson recurrence (FR-001). */
  public static final String POISSON = "POISSON";
}
```

## Request shapes (`Engine.REQUEST_ALARM_EVENT`)

```java
// one-shot — unchanged
EventImpl.createEvent(Engine.REQUEST_ALARM_EVENT, source, "TOPIC", 10);
// fixed period — unchanged
EventImpl.createEvent(Engine.REQUEST_ALARM_EVENT, source, "TOPIC", 10, 5);
// Poisson, unseeded — new
EventImpl.createEvent(Engine.REQUEST_ALARM_EVENT, source, "TOPIC", 10, TimeSource.POISSON, 2.0);
// Poisson, seeded (reproducible) — new
EventImpl.createEvent(Engine.REQUEST_ALARM_EVENT, source, "TOPIC", 10, TimeSource.POISSON, 2.0, 42L);
```

- `rate`: mean number of firings per unit of simulated time; MUST be a positive `Double` (FR-001, FR-006).
- `seed`: optional `Number`; the same rate + seed yields the same firing sequence (FR-003).
- First firing at the requested time is deterministic; each subsequent wait is drawn per FR-002 (exponential of parameter rate, whole simulated units, minimum one unit).
- The alarm re-arms until cancelled with the unchanged `Engine.CLEAR_ALARM_EVENT("TOPIC")` (FR-004) or until the simulation ends.
- Malformed Poisson requests (missing marker-compatible rate, rate `<= 0`, non-`Number` seed) are refused: an error is logged on the time source and no alarm is registered (FR-006); no exception reaches the requester (the request is an asynchronous signal).

## Unchanged behavior

- One-shot and fixed-period alarms (FR-005), replacement on same topic, firing by signaling the alarm topic with the time source as source.
- The 3-parameter shape without the marker keeps meaning a fixed integer period; a `Double` period without the marker is refused as malformed (never silently reinterpreted).

## Non-goals

- No distribution family other than exponential; no time-varying rate; no per-firing payload parameters on the fired event; no persistence of seeds.
