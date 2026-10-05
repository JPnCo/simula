# Feature Specification: Poisson Alarm

**Feature Branch**: `007-poisson-alarm`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "Extend the alarm service so a requesting actor can ask for an alarm whose recurrence follows a Poisson process of given rate, with reproducible randomness via an optional seed, keeping one-shot and fixed-period alarms unchanged"

## Clarifications

### Session 2026-10-05

- Q: Forme de la fonctionnalité ? → A: Étendre le service d'alarmes existant (demande via l'événement de demande d'alarme), plutôt qu'un acteur générateur dédié.
- Q: Déterminisme de l'aléa ? → A: Graine injectable (paramètre optionnel de la demande) pour des tests reproductibles ; sans graine, comportement non reproductible volontairement.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ask for a randomly recurring alarm (Priority: P1)

A simulation developer models random phenomena — machine failures, customer arrivals, noise bursts — whose occurrences are independent and happen at a known average rate, with a random gap between consecutive occurrences. The developer requests an alarm the same way as any other, but instead of a fixed period declares a **rate** (average number of firings per unit of simulated time). The alarm then fires again and again, each time after a random wait whose average equals one over the rate, until it is cleared or the simulation ends.

**Why this priority**: This is the core capability: a Poisson-process alarm — memoryless, exponential random waits around the requested rate.

**Independent Test**: Request a Poisson alarm at rate 1 with a given seed on a running engine; observe firings at irregular times; repeat the same scenario with the same seed: the exact same firing times come out.

**Acceptance Scenarios**:

1. **Given** a running engine, **When** an actor requests a Poisson alarm (topic, first firing time, rate 2, seed), **Then** the topic fires first at the requested time, then at irregular simulated-time gaps averaging one half unit.
2. **Given** the same request repeated with the same seed, **Then** the sequence of gaps is identical.
3. **Given** a Poisson alarm, **When** a clear request arrives for its topic before a firing, **Then** no further firing occurs.
4. **Given** a Poisson alarm, **When** firings occur, **Then** each gap between consecutive firings is at least one simulated time unit.

---

### User Story 2 - Statistical fidelity (Priority: P2)

Over a long run, the observed behavior matches the declared rate: the average gap between firings converges to one over the rate, and the gaps are dispersed (not the constant value of a fixed-period alarm).

**Why this priority**: The value of the feature is the statistical law, not just randomness; fidelity must be verifiable.

**Independent Test**: With a fixed seed, request a Poisson alarm, run enough firings, and check the empirical mean gap lies within a narrow tolerance of one over the rate, and that at least two distinct gap values occurred.

**Acceptance Scenarios**:

1. **Given** a seeded Poisson alarm at a known rate, **When** enough firings are observed, **Then** the empirical mean gap is within 10 % of one over the rate.
2. **Given** the same run, **Then** the observed gaps are not all identical.

---

### User Story 3 - No regression, clear errors (Priority: P3)

One-shot and fixed-period alarms behave exactly as before; a Poisson request that does not make sense (non-positive rate, malformed parameters) is refused with a logged error and no alarm is created.

**Why this priority**: The alarm service is used by existing code; the extension must be strictly additive and safe to misuse.

**Independent Test**: Re-run the existing alarm scenarios (unchanged); request a Poisson alarm with rate 0 and with a negative rate: each refusal logs an error and the alarm set is unchanged.

**Acceptance Scenarios**:

1. **Given** an engine, **When** a one-shot or fixed-period alarm is requested, **Then** behavior is identical to before this feature.
2. **Given** an engine, **When** a Poisson alarm is requested with a rate less than or equal to zero, **Then** an error is logged and no alarm is registered.
3. **Given** a topic that already has an alarm, **When** a new alarm (of any kind) is requested on that topic, **Then** it replaces the previous one, as before.

---

### Edge Cases

- A Poisson alarm requested with a first firing time already elapsed fires as soon as the request is processed (same tolerance as existing alarms).
- Random waits are expressed in whole simulated time units (the framework's granularity): each drawn wait is rounded so that at least one unit separates two firings.
- Without a seed, two requests with the same rate produce different firing sequences.
- The seed belongs to the alarm: the sequence of one alarm is unaffected by other alarms being requested or cleared in between.
- Clearing a Poisson alarm uses the same clear request as any alarm (topic-parametered).
- The rate is a positive decimal quantity; requesting with an integral period parameter still means a fixed period (no ambiguity between the two request shapes).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A requesting actor MUST be able to request an alarm specifying: a topic, a first firing time, and a strictly positive rate (average firings per unit of simulated time), with an optional seed; such an alarm MUST keep re-arming until cleared.
- **FR-002**: Consecutive firings of a Poisson alarm MUST be separated by a random wait drawn from the exponential distribution of parameter equal to the rate, rounded to whole simulated time units and never below one unit.
- **FR-003**: Two requests with the same rate and the same seed MUST produce the same sequence of waits; a request without a seed MUST use unpredictable randomness.
- **FR-004**: A Poisson alarm MUST be cancellable by the existing clear-alarm request on its topic, with the same semantics as other alarms.
- **FR-005**: One-shot and fixed-period alarms MUST keep their current behavior exactly, including the one-alarm-per-topic replacement rule; the request shapes MUST remain distinguishable.
- **FR-006**: A Poisson request with a non-positive rate or malformed parameters MUST be refused with a logged error and MUST NOT register an alarm.
- **FR-007**: The randomness of one alarm's sequence MUST NOT be influenced by requests or removals of other alarms.

### Key Entities

- **Poisson alarm**: an alarm that re-arms indefinitely; each re-arm draws its next wait from the exponential distribution of parameter λ (rate), rounded up to whole simulated units; owns its random source (seeded or not).
- **Rate**: strictly positive average number of firings per unit of simulated time; the mean random wait is one over the rate.
- **Seed**: optional value making an alarm's random sequence reproducible.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With a fixed seed, the same Poisson request produces bit-identical firing times across runs.
- **SC-002**: Over at least 200 firings with a fixed seed, the empirical mean gap is within 10 % of one over the declared rate and at least two gap values differ.
- **SC-003**: Every gap between consecutive firings is at least one simulated time unit.
- **SC-004**: All pre-existing alarm scenarios pass unchanged; the project's coverage gates are met.

## Assumptions

- "Poisson law" means a Poisson process: independent exponential waits averaging one over the rate, the natural discrete-time realization of a Poisson arrival process (confirmed by the owner's request wording).
- Simulated time advances in whole units, so exact continuous-time Poisson behavior is approximated by integer waits of at least one unit.
- The existing alarm request/clear event mechanism, logging and time source are reused; no new dependency.
- The alarm's first firing is at the requested time (deterministic); randomness applies to re-arms only.
