# Data Model: Add Barrier Actor

Entities relevant to the `003-add-barrier-actor` feature, derived from the feature spec's Key Entities and the existing framework. This is the in-memory domain model of the simulation framework; there is no persistence layer.

## Barrier

An actor that coordinates a set of participants by counting "ready" signals and notifying the creator when the configured participant count is reached.

| Field | Type | Notes / Validation |
|-------|------|--------------------|
| `id` | Integer | Unique per actor (from `IdBuilder`); identity basis |
| `participants` | int | Participant count to reach before firing; MUST be ≥ 1 (FR-009) |
| `readyTopic` | String | Topic the Barrier subscribes to; participants signal readiness here; MUST be non-null/non-blank (FR-002, FR-009) |
| `completeTopic` | String | Topic signaled when all participants are ready; MUST be non-null/non-blank (FR-004, FR-009) |
| `mode` | BarrierMode | Single-use or cyclic; MUST be non-null (FR-008, FR-009) |
| `distinct` | boolean | If true, count each distinct source only once; MUST be non-null (FR-007, FR-009) |
| `count` | int | Current number of ready participants (FR-003) |
| `readySources` | Set\<Actor\> | Distinct sources seen so far, used only when `distinct = true` (FR-007) |
| `delegate` | Actor | Delegation target for the standard event loop (ActorDelegate) |
| `engine` | Engine | Owning engine |

**Behavior**:
- On construction: subscribes to `readyTopic` (FR-002).
- `process(Event)`: when the event's topic equals `readyTopic`, increments `count` (respecting `distinct` via `readySources` keyed on `event.getSource()` — FR-007, FR-010). When `count >= participants`, signals the complete topic (FR-004) and then applies the mode:
  - `SINGLE_USE` (FR-005): unsubscribes from `readyTopic` and stops reacting.
  - `CYCLIC` (FR-006): resets `count` (and `readySources` when distinct) and remains subscribed.

**State transitions**:
- `awaiting → fired → (single-use) inactive`
- `awaiting → fired → awaiting (cyclic, reset)`

## BarrierMode

Selection of the Barrier's behavior after firing.

| Value | Meaning |
|-------|---------|
| `SINGLE_USE` | The Barrier fires once, then unsubscribes from the ready topic and no longer reacts (FR-005) |
| `CYCLIC` | The Barrier resets after firing and can fire again on subsequent cycles (FR-006) |

**Validation**: mode must be one of the enum values; an invalid/unknown value is rejected with a clear error (FR-008). Mode is fixed at construction.

## Participant

An actor that signals readiness by signaling an event on the ready topic. It is identified by the event's source (`event.getSource()`), which enables distinct counting (FR-010).

## Creator

The actor that builds the Barrier and subscribes to the complete topic to be notified when all participants are ready. It receives the complete event after the Barrier fires (FR-004).
