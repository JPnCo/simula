# Research: Poisson Alarm

**Date**: 2026-10-05 | **Spec**: [spec.md](spec.md)

## R1 — How to encode a Poisson request without breaking existing shapes

**Decision**: keep `REQUEST_ALARM_EVENT` and add a marker parameter: `(topic, firstTime, TimeSource.POISSON, Double rate[, Long seed])`.
**Rationale**: existing shapes are `(topic, time)` and `(topic, time, Integer period)`; a length+type sniff (e.g. "Double means rate") would silently reinterpret `period = 1.0` and violate FR-005's "distinguishable shapes". An explicit String marker is self-documenting in request code and unambiguous to parse.
**Alternatives**: new topic `REQUEST_POISSON_ALARM_EVENT` — rejected: a second event topic multiplies subscription points for the time source for no semantic gain; boolean flag parameter — rejected: same arity as periodic requests, less readable.

## R2 — Drawing exponential waits at integer simulated-time granularity

**Decision**: `waitTicks = max(1, round(TIME_FACTOR * -ln(1-U) / rate))`, `U = random.nextDouble()`, computed per re-arm in a package-private static helper.
**Rationale**: inverse-CDF transform of the exponential of parameter rate is the standard exact method; `1-U` avoids `ln(0)`; scaling by `TIME_FACTOR` matches how fixed periods are already stored (internal clock unit); `round` is near-unbiased and `max(1)` enforces the spec's one-unit minimum gap (SC-003).
**Alternatives**: Bernoulli-per-unit-time (geometric waits) — rejected: different law for the same λ at coarse granularity; summing unit exponentials — rejected: slower, same result.

## R3 — Where the random source lives

**Decision**: one `java.util.Random` per rate-mode alarm, constructed as `new Random(seed)` (seeded) or `new Random()` (unseeded); the seed travels with the request.
**Rationale**: FR-007 requires an alarm's sequence to be independent of other alarms' life cycles; a shared `Random` would couple them through interleaved draws. Per-alarm instances also make SC-001 (seeded replay) exact.
**Alternatives**: shared `SplittableRandom` seeded once from the request — rejected: coupling as soon as two alarms exist; `ThreadLocalRandom` — rejected: not seedable.

## R4 — Validation and failure behavior

**Decision**: refuse (log `Logger.error` with a named message constant, register nothing) when the marker is present but `rate` is not a positive `Double`, or the seed is present but not a `Number`.
**Rationale**: today malformed requests propagate a `ClassCastException` into the time source loop (logged by its crash path); for the new shape the spec demands a refused, logged, state-free outcome (FR-006) — existing shapes are untouched (FR-005).
**Alternatives**: throw to the requester — rejected: alarm requests are asynchronous signals; the requester cannot catch anything.

## R5 — Test strategy under the deterministic-suite rule

**Decision**: statistical fidelity (SC-002) tested in-process over ≥ 2000 draws of the helper with a fixed seed (mean within 10 %, dispersion present); engine-level tests only assert seeded reproducibility, irregular gaps, minimum gap and clear semantics on a short seeded run.
**Rationale**: each simulated time unit costs wall-clock time (`TIME_FACTOR` seconds); the distribution is a pure function of the seed, so the expensive statistical check needs no simulated clock; the seeded integration replay proves the wiring.
