# Contract: Barrier

`jpnco.simula.actors.Barrier` implements `jpnco.simula.Actor` and coordinates a set of participants using a counting barrier. It is created by a developer with an engine reference and registered/started through the engine, like the existing `Logger` actor.

## Constructor

```java
public Barrier(
    final Engine engine,
    final int participants,
    final String readyTopic,
    final String completeTopic,
    final BarrierMode mode,
    final boolean distinct)
```

| Parameter | Type | Description | Validation |
|-----------|------|-------------|------------|
| `engine` | Engine | The engine that runs this actor | non-null (required) |
| `participants` | int | Participant count to reach before firing | MUST be ≥ 1 (FR-009) |
| `readyTopic` | String | Topic the Barrier subscribes to; participants signal readiness here | non-null, non-blank (FR-009) |
| `completeTopic` | String | Topic signaled when all participants are ready | non-null, non-blank (FR-009) |
| `mode` | BarrierMode | Single-use or cyclic behavior | non-null (FR-008, FR-009) |
| `distinct` | boolean | If true, each distinct source counts only once | non-null (FR-009) |

On construction the Barrier subscribes to `readyTopic` (FR-002).

## Registration

The creator registers and starts the Barrier through the engine:

```java
engine.registerAndStart(new Barrier(engine, participants, readyTopic, completeTopic, mode, distinct));
```

## Event Protocol

- **Input (ready)**: a participant signals readiness by signaling an event whose topic is `readyTopic`. The Barrier receives it because it is subscribed to `readyTopic`.
- **Output (complete)**: when the counted ready participants reach `participants`, the Barrier signals an event whose topic is `completeTopic`, with the Barrier as source. The creator must subscribe to `completeTopic` to be notified (FR-004).

## Behavior Modes

| Mode | After firing |
|------|--------------|
| `SINGLE_USE` | Unsubscribes from `readyTopic` and stops reacting (FR-005) |
| `CYCLIC` | Resets the counter (and distinct-source set when `distinct`) and remains subscribed (FR-006) |

## Distinct Counting

When `distinct = true`, the Barrier counts each distinct source (the event's source actor) only once, even if it signals ready multiple times (FR-007, FR-010). When `distinct = false`, every ready event increments the counter.

## Contract methods

`Barrier` implements the `Actor` contract via delegation to `ActorDelegate`:

- `getDelegate()` — returns the delegate.
- `getId()` — returns the actor id (from `IdBuilder`).
- `process(Event)` — counts ready events and fires the complete topic.
- `equals` / `hashCode` — based on the actor id (like `Logger` and `TimeSource`).
